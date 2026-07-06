package com.wwh.filebox.controller;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.StorageSpace;
import com.wwh.filebox.service.AuthService;
import com.wwh.filebox.service.FileCatalogService;
import com.wwh.filebox.service.StorageService;
import com.wwh.filebox.util.DateTimeFormatter;
import com.wwh.filebox.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

/**
 * File Box controller
 * 文件盒子控制器
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>文件上传处理（支持拖放和粘贴）</li>
 *   <li>文本内容上传</li>
 *   <li>文件列表查询（按年月筛选）</li>
 *   <li>文件下载服务</li>
 *   <li>文件删除（管理员/经理权限）</li>
 * </ul>
 *
 * <p>安全特性：</p>
 * <ul>
 *   <li>路径遍历防护（确保访问路径在存储目录内）</li>
 *   <li>基于角色的访问控制</li>
 *   <li>存储空间权限验证</li>
 * </ul>
 *
 * @author FileBox Team
 * @version 1.0
 */
@Controller
public class FileBoxController {

    private static final Logger logger = LoggerFactory.getLogger(FileBoxController.class);

    @Autowired
    private StorageService storageService;

    @Autowired
    private AuthService authService;

    @Autowired
    private FileCatalogService fileCatalogService;

    private String getTokenFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private LoginSession getSession(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        if (token != null) {
            return authService.getSession(token);
        }
        return null;
    }

    private String getCurrentStorageSpace(HttpServletRequest request) {
        LoginSession session = getSession(request);
        if (session != null) {
            return session.getCurrentStorageSpace();
        }
        return null;
    }

    @PostMapping("/upload_file")
    @ResponseBody
    public ResponseEntity<String> uploadFile(HttpServletRequest request,
                                             @RequestParam("file") MultipartFile[] files,
                                             @RequestParam(value = "targetFolder", required = false) String targetFolder) throws IOException {
        LoginSession session = getSession(request);
        String username = session != null ? session.getUsername() : "unknown";
        logger.info("User {} attempting to upload {} files", username, files.length);

        if (files == null || files.length == 0) {
            logger.warn("Upload failed for user {}: No files selected", username);
            return ResponseEntity.badRequest().body("未选择文件");
        }

        // 使用统一的存储空间验证方法
        StorageSpace storageSpace = validateAndGetStorageSpace(request, "Upload");
        if (storageSpace == null) {
            // 根据不同的验证失败情况返回不同的错误码
            String storageSpaceName = getCurrentStorageSpace(request);
            if (storageSpaceName == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("未选择存储空间");
            }
            if (storageService.getStorageSpace(storageSpaceName) == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("存储空间未找到");
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无上传权限");
        }

        String storageSpaceName = storageSpace.getName();
        String storageDir = storageSpace.getPath();
        // 上传始终进入当前目录；未指定 targetFolder 时即存储空间根目录。
        Path uploadDir = resolveWithinStorage(request, targetFolder);
        if (uploadDir == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的目标文件夹");
        }
        if (Files.exists(uploadDir) && !Files.isDirectory(uploadDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("目标文件夹不存在");
        }
        Files.createDirectories(uploadDir);
        String timestamp = DateTimeFormatter.getCurrentTimestamp();

        for (MultipartFile f : files) {
            logger.info("Processing file uploaded by user {}: Original name: {}, Size: {} bytes", username, f.getOriginalFilename(), f.getSize());

            // Check storage space limit
            if (!storageService.hasEnoughSpace(storageSpaceName, f.getSize())) {
                logger.warn("Upload failed for user {}: Storage space {} is full", username, storageSpaceName);
                return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE).body("存储空间不足");
            }

            // 文件名:合规→原样保留,不合规→最小清理;返回 null 表示无法采用
            // Filename: keep original when compliant, otherwise minimal cleanup; null = unusable.
            String original = f.getOriginalFilename();
            String filename = FileUtils.prepareUploadFilename(
                    (original != null && !original.isEmpty())
                            ? original
                            : AppConstants.FileUpload.PASTED_FILE_PREFIX + timestamp + AppConstants.FileUpload.DEFAULT_FILE_EXTENSION);
            if (filename == null) {
                logger.warn("Upload rejected for user {}: non-compliant filename '{}'", username, original);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("文件名不合规，请重命名后上传");
            }
            Path savePath = uploadDir.resolve(filename);
            int counter = 1;
            int dotIndex = filename.lastIndexOf('.');
            String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
            String extension = dotIndex > 0 ? filename.substring(dotIndex) : "";

            while (Files.exists(savePath)) {
                String newFilename = baseName + " (" + counter + ")" + extension;
                savePath = uploadDir.resolve(newFilename);
                counter++;
            }
            // 用 transferTo(File) 走 Part.write → rename（同文件系统下原子移动，消除二次拷贝）
            // transferTo(File) delegates to Part.write → atomic rename on the same filesystem.
            // 注意 1：必须用 File 重载 —— transferTo(Path) 恒为流式拷贝。
            // 注意 2：必须传【绝对路径】。Part.write 会把相对路径解析到 multipart 的 location
            //         （Tomcat 工作目录下的临时目录），而非进程 CWD，导致找不到目标父目录。
            // Note 1: use the File overload; transferTo(Path) always copies.
            // Note 2: pass an ABSOLUTE path. Part.write resolves a relative path against the
            //         multipart location (Tomcat work dir), not the process CWD.
            f.transferTo(savePath.toAbsolutePath().normalize().toFile());
            Files.setLastModifiedTime(savePath, FileTime.fromMillis(System.currentTimeMillis()));
            logger.info("User {} uploaded file: {}", username, savePath.getFileName());
        }

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/upload_text")
    @ResponseBody
    public ResponseEntity<String> uploadText(HttpServletRequest request, @RequestBody Map<String, Object> body) throws IOException {
        LoginSession session = getSession(request);
        String username = session != null ? session.getUsername() : "unknown";
        logger.info("User {} attempting to upload text", username);

        if (body == null || !body.containsKey("text") || ((String) body.get("text")).trim().isEmpty()) {
            logger.warn("Text upload failed for user {}: Content is empty", username);
            return ResponseEntity.badRequest().body("无文本内容");
        }

        // 使用统一的存储空间验证方法
        StorageSpace storageSpace = validateAndGetStorageSpace(request, "Text upload");
        if (storageSpace == null) {
            String storageSpaceName = getCurrentStorageSpace(request);
            if (storageSpaceName == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("未选择存储空间");
            }
            if (storageService.getStorageSpace(storageSpaceName) == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("存储空间未找到");
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无上传权限");
        }

        String storageDir = storageSpace.getPath();
        // 上传文本也进入当前目录；未指定 targetFolder 时即存储空间根目录。
        String targetFolder = body.get("targetFolder") != null ? body.get("targetFolder").toString() : null;
        Path uploadDir = resolveWithinStorage(request, targetFolder);
        if (uploadDir == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的目标文件夹");
        }
        if (Files.exists(uploadDir) && !Files.isDirectory(uploadDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("目标文件夹不存在");
        }
        Files.createDirectories(uploadDir);
        String filename = AppConstants.FileUpload.PASTE_FILE_PREFIX + DateTimeFormatter.getCurrentTimestamp() + AppConstants.FileUpload.DEFAULT_FILE_EXTENSION;
        Path savePath = uploadDir.resolve(filename);
        Files.write(savePath, ((String) body.get("text")).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        Files.setLastModifiedTime(savePath, FileTime.fromMillis(System.currentTimeMillis()));
        logger.info("User {} uploaded text file: {}", username, filename);

        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/delete_file")
    @ResponseBody
    public ResponseEntity<String> deleteFile(@RequestParam("path") String filePath, HttpServletRequest request) throws IOException {
        LoginSession session = getSession(request);
        String username = session != null ? session.getUsername() : "unknown";
        logger.info("User {} attempting to delete file: {}", username, filePath);

        if (session == null) {
            logger.warn("Delete failed for user {}: Not logged in", username);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无删除权限");
        }

        Role role = session.getRole();
        if (role != Role.ADMIN && role != Role.MANAGER) {
            logger.warn("Delete failed for user {}: No delete permission", username);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无删除权限");
        }

        // 使用统一的存储空间验证方法
        StorageSpace storageSpace = validateAndGetStorageSpace(request, "Delete");
        if (storageSpace == null) {
            String storageSpaceName = getCurrentStorageSpace(request);
            if (storageSpaceName == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("未选择存储空间");
            }
            if (storageService.getStorageSpace(storageSpaceName) == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("存储空间未找到");
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无删除权限");
        }

        String storageDir = storageSpace.getPath();

        // Ensure file path starts with '/' handling
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }

        Path basePath = Paths.get(storageDir).toAbsolutePath().normalize();
        Path targetPath = Paths.get(basePath.toString(), filePath).toAbsolutePath().normalize();

        // Security check: ensure target path is within storage directory
        if (!targetPath.startsWith(basePath)) {
            logger.warn("Delete failed for user {}: Path traversal attempt: {}", username, filePath);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的文件路径");
        }

        if (Files.exists(targetPath) && Files.isRegularFile(targetPath) && !Files.isSymbolicLink(targetPath)) {
            Files.delete(targetPath);
            logger.info("User {} successfully deleted file: {}", username, filePath);
            return ResponseEntity.ok("文件删除成功");
        } else {
            logger.warn("Delete failed for user {}: File not found - {}", username, filePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件不存在，请刷新后重试");
        }
    }

    @GetMapping("/list_periods")
    @ResponseBody
    public ResponseEntity<?> listPeriods(HttpServletRequest request) throws IOException {
        String storageSpaceName = getCurrentStorageSpace(request);
        if (storageSpaceName == null) {
            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("periods", new ArrayList<>());
            }});
        }

        StorageSpace storageSpace = storageService.getStorageSpace(storageSpaceName);
        if (storageSpace == null) {
            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("periods", new ArrayList<>());
            }});
        }

        FileCatalogService.ScanResult scan = fileCatalogService.scan(
                Paths.get(storageSpace.getPath()), null, null, 1);
        Map<String, Object> resp = new HashMap<>();
        resp.put("periods", scan.getPeriods());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/list_files")
    @ResponseBody
    public ResponseEntity<?> listFiles(HttpServletRequest request,
                                       @RequestParam(value = "year", required = false) String year,
                                       @RequestParam(value = "month", required = false) String month,
                                       @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) throws IOException {

        if (limit < 1) {
            return ResponseEntity.badRequest().body("limit 必须大于 0");
        }
        limit = Math.min(limit, AppConstants.FileUpload.MAX_FILE_LIMIT);
        if (year != null && !year.matches("\\d{4}")) {
            return ResponseEntity.badRequest().body("年份格式无效");
        }
        if (month != null && !month.matches("0[1-9]|1[0-2]")) {
            return ResponseEntity.badRequest().body("月份格式无效");
        }
        if (month != null && year == null) {
            return ResponseEntity.badRequest().body("选择月份时必须同时指定年份");
        }

        String storageSpaceName = getCurrentStorageSpace(request);
        if (storageSpaceName == null) {
            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("files", new ArrayList<>());
            }});
        }

        StorageSpace storageSpace = storageService.getStorageSpace(storageSpaceName);
        if (storageSpace == null) {
            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("files", new ArrayList<>());
            }});
        }

        List<Map<String, Object>> result = new ArrayList<>();
        FileCatalogService.ScanResult scan;
        try {
            scan = fileCatalogService.scan(Paths.get(storageSpace.getPath()), year, month, limit);
            for (FileCatalogService.FileEntry entry : scan.getFiles()) {
                result.add(buildDirRecord(entry.getPath(), entry.getRelativePath()));
            }
        } catch (IOException e) {
            logger.warn("Error scanning storage space {}: {}", storageSpaceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("读取文件列表失败");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("files", result);
        resp.put("periods", scan.getPeriods());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/uploads/{year}/{month}/{filename:.+}")
    public void serveFile(HttpServletRequest request,
                          HttpServletResponse response,
                          @PathVariable String year,
                          @PathVariable String month,
                          @PathVariable String filename) throws IOException {
        // 验证filename不为空
        if (filename == null || filename.trim().isEmpty()) {
            logger.warn("Invalid file path request: filename is null or empty");
            writeError(response, HttpStatus.BAD_REQUEST.value(), "无效的文件路径");
            return;
        }
        // 解析路径(含穿越校验)后委托统一流式方法 / Resolve (with traversal check) then delegate
        Path target = resolveServedPath(request, response, Paths.get(year, month, filename));
        if (target == null) return;
        serveResolvedFile(target, request, response);
    }

    /**
     * 按相对路径下载文件(目录视图用) / Serve a file by storage-relative path (used by directory view).
     * 与 /uploads/{year}/{month}/{filename} 共用 {@link #serveResolvedFile},仅路径来源不同。
     */
    @GetMapping("/api/file")
    public void serveByPath(HttpServletRequest request,
                            HttpServletResponse response,
                            @RequestParam("path") String relPath) throws IOException {
        if (relPath == null || relPath.trim().isEmpty()) {
            writeError(response, HttpStatus.BAD_REQUEST.value(), "无效的文件路径");
            return;
        }
        Path target = resolveServedPath(request, response, Paths.get(relPath));
        if (target == null) return;
        serveResolvedFile(target, request, response);
    }

    /**
     * 解析"当前存储空间"内的相对路径,做路径穿越校验。失败时写入错误响应并返回 null。
     * Resolve a storage-relative path with traversal check; writes error response and returns null on failure.
     */
    private Path resolveServedPath(HttpServletRequest request, HttpServletResponse response, Path rel) throws IOException {
        String storageSpaceName = getCurrentStorageSpace(request);
        if (storageSpaceName == null) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "未选择存储空间");
            return null;
        }
        StorageSpace storageSpace = storageService.getStorageSpace(storageSpaceName);
        if (storageSpace == null) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "存储空间未找到");
            return null;
        }
        Path basePath = Paths.get(storageSpace.getPath()).toAbsolutePath().normalize();
        Path target;
        try {
            target = basePath.resolve(rel).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            logger.warn("Invalid file path request: {} - {}", rel, e.getMessage());
            writeError(response, HttpStatus.BAD_REQUEST.value(), "无效的文件路径");
            return null;
        }
        if (!target.startsWith(basePath)) {
            logger.warn("Path traversal attempt: {}", target);
            writeError(response, HttpStatus.BAD_REQUEST.value(), "无效的文件路径");
            return null;
        }
        if (containsSymbolicLink(basePath, target)) {
            logger.warn("Symbolic-link access rejected: {}", target);
            writeError(response, HttpStatus.BAD_REQUEST.value(), "不支持符号链接");
            return null;
        }
        return target;
    }

    /**
     * 流式输出已解析(且通过穿越校验)的文件,保留 Range / 大文件 / 错误处理语义。
     * Stream a resolved file; preserves Range / large-file / error-handling semantics.
     */
    private void serveResolvedFile(Path target, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String filename = target.getFileName().toString();

        if (!Files.exists(target) || !Files.isRegularFile(target) || Files.isSymbolicLink(target)) {
            writeError(response, HttpStatus.NOT_FOUND.value(), "文件不存在");
            return;
        }

        long fileSize = Files.size(target);

        // 解析 Range 头（仅支持单段；多段或无法解析时回退为全量 200）
        // Parse the Range header (single range only; fall back to full 200 otherwise)
        long start = 0;
        long end = fileSize - 1;
        boolean partial = false;
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).trim();
            int comma = spec.indexOf(',');
            if (comma < 0) {
                int dash = spec.indexOf('-');
                if (dash >= 0) {
                    try {
                        String s = spec.substring(0, dash).trim();
                        String e = spec.substring(dash + 1).trim();
                        long parsedStart;
                        if (s.isEmpty()) {
                            // 后缀形式 "-N"：取文件末尾 N 字节
                            long suffix = Long.parseLong(e);
                            if (suffix <= 0) {
                                throw new NumberFormatException();
                            }
                            parsedStart = Math.max(0, fileSize - suffix);
                        } else {
                            parsedStart = Long.parseLong(s);
                            if (parsedStart >= fileSize) {
                                // 起始超出文件大小 → 416
                                response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                                response.setHeader("Content-Range", "bytes */" + fileSize);
                                return;
                            }
                            if (!e.isEmpty()) {
                                long parsedEnd = Long.parseLong(e);
                                if (parsedEnd < parsedStart) {
                                    throw new NumberFormatException();
                                }
                                end = Math.min(parsedEnd, fileSize - 1);
                            }
                        }
                        start = parsedStart;
                        partial = true;
                    } catch (NumberFormatException ex) {
                        // 解析失败：回退为全量响应（partial 保持 false）
                    }
                }
            }
            // 多段请求（含逗号）：回退为全量响应（partial 保持 false）
        }

        // 设置公共响应头（必须在写出第一个字节之前完成）
        // Set common headers before writing the first byte
        String contentType = FileUtils.getContentTypeByExtension(filename);
        try {
            response.setContentType(MediaType.parseMediaType(contentType).toString());
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            logger.debug("Failed to parse media type: {}, using default", contentType);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        response.setHeader("Content-Disposition", "inline; filename=" + FileUtils.encodeFilenameForHttpHeader(filename));
        response.setHeader("Accept-Ranges", "bytes");

        long contentLength = end - start + 1;

        // 打开文件通道后再设置状态/长度，这样通道打开失败时仍能返回干净的错误响应
        // Open the channel before committing status/length so a failure still
        // produces a clean error (not a redirect via CustomErrorController).
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.READ)) {
            if (start > 0) {
                channel.position(start);
            }

            if (partial) {
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            } else {
                response.setStatus(HttpStatus.OK.value());
            }
            response.setContentLengthLong(contentLength);

            // 用 64KB 缓冲流式写出（FileChannel 已定位到 start，O(1) seek）
            // Stream with a 64KB buffer; FileChannel is already positioned at start
            ByteBuffer buffer = ByteBuffer.allocate(AppConstants.FileUpload.DOWNLOAD_BUFFER_SIZE);
            OutputStream out = response.getOutputStream();
            long remaining = contentLength;
            while (remaining > 0) {
                buffer.clear();
                if (buffer.limit() > remaining) {
                    buffer.limit((int) remaining);
                }
                int read = channel.read(buffer);
                if (read < 0) {
                    break;
                }
                out.write(buffer.array(), buffer.arrayOffset(), read);
                remaining -= read;
            }
            out.flush();
        } catch (IOException e) {
            logger.warn("Failed to stream file {}: {}", target, e.getMessage());
            // 响应未提交时返回干净错误；已提交则只能记录（客户端会收到截断的响应）
            // If uncommitted, return a clean error; if committed, just log (truncated response)
            if (!response.isCommitted()) {
                response.reset();
                writeError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "读取文件失败");
            }
        }
    }

    /**
     * 直接写入错误响应（状态码 + 中文消息）
     * 注意：不能用 response.sendError()，因为 CustomErrorController 会把 /error 重定向到首页。
     * Write an error response directly (status + message). Do NOT use response.sendError():
     * CustomErrorController redirects /error to /index.html, which would turn errors into 302s.
     */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain; charset=UTF-8");
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        response.setContentLengthLong(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private Map<String, Object> buildRecord(Path fullPath, String y, String m) {
        Map<String, Object> map = new HashMap<>();
        try {
            File f = fullPath.toFile();
            long mtime = f.lastModified();
            String mtimeS = DateTimeFormatter.formatFileTime(new Date(mtime));
            String filename = fullPath.getFileName().toString();
            String ftype = FileUtils.getFileTypeCategory(filename);
            long fileSize = f.length();
            String content = null;
            if ("text".equals(ftype)) {
                content = readTextPreview(fullPath);
            }
            map.put("year", y);
            map.put("month", m);
            map.put("filename", filename);
            map.put("url", "/uploads/" + y + "/" + m + "/" + filename);
            map.put("time", mtimeS);
            map.put("type", ftype);
            map.put("size", fileSize);
            map.put("content", content);
        } catch (Exception e) {
            logger.warn("Error building file record: {}", e.getMessage(), e);
        }
        return map;
    }

    /**
     * 为目录视图构建文件记录(url 走 /api/file?path=<rel>) / Build a file record for the directory view.
     * 与 buildRecord 的区别:文件可位于任意深度的自定义文件夹,url 用 /api/file?path=。
     */
    private Map<String, Object> buildDirRecord(Path fullPath, String relPath) {
        Map<String, Object> map = new HashMap<>();
        try {
            File f = fullPath.toFile();
            String filename = fullPath.getFileName().toString();
            String ftype = FileUtils.getFileTypeCategory(filename);
            String content = null;
            if ("text".equals(ftype)) {
                content = readTextPreview(fullPath);
            }
            map.put("filename", filename);
            map.put("path", relPath); // 存储空间根的相对路径,供前端删除/重命名/移动使用
            try {
                map.put("url", "/api/file?path=" + java.net.URLEncoder.encode(relPath, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                map.put("url", "/api/file?path=" + relPath); // UTF-8 始终可用,理论上不会走到
            }
            map.put("time", DateTimeFormatter.formatFileTime(new Date(f.lastModified())));
            map.put("type", ftype);
            map.put("size", f.length());
            map.put("content", content);
        } catch (Exception e) {
            logger.warn("Error building dir record: {}", e.getMessage(), e);
        }
        return map;
    }

    /** Read only the small prefix needed by the UI instead of loading a whole text file into heap. */
    private String readTextPreview(Path path) {
        char[] buffer = new char[AppConstants.FileUpload.TEXT_PREVIEW_MAX_LENGTH];
        int total = 0;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while (total < buffer.length) {
                int read = reader.read(buffer, total, buffer.length - total);
                if (read < 0) break;
                total += read;
            }
            return new String(buffer, 0, total);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean canUserAccessStorageSpace(LoginSession session, String storageSpaceName) {
        for (String space : session.getStorageSpaces()) {
            if (space.equals(storageSpaceName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取并验证存储空间，统一处理存储空间相关的验证逻辑
     *
     * @param request HTTP请求
     * @param operation 操作名称（用于日志）
     * @return 存储空间对象，验证失败返回null
     */
    private StorageSpace validateAndGetStorageSpace(HttpServletRequest request, String operation) {
        LoginSession session = getSession(request);
        String username = session != null ? session.getUsername() : "unknown";

        String storageSpaceName = getCurrentStorageSpace(request);
        if (storageSpaceName == null) {
            logger.error("{} failed for user {}: No storage space selected", operation, username);
            return null;
        }

        StorageSpace storageSpace = storageService.getStorageSpace(storageSpaceName);
        if (storageSpace == null) {
            logger.error("{} failed for user {}: Storage space not found: {}", operation, username, storageSpaceName);
            return null;
        }

        // 检查用户权限
        if (session != null && session.getRole() == Role.USER && !canUserAccessStorageSpace(session, storageSpaceName)) {
            logger.warn("{} failed for user {}: No permission for storage space {}", operation, username, storageSpaceName);
            return null;
        }

        return storageSpace;
    }

    // ==================== 目录浏览 / Directory browsing ====================

    /**
     * 把"当前存储空间根"内的相对路径解析为绝对路径,并做路径穿越校验。
     * 失败(存储空间不可用或穿越)返回 null。 / Resolve a storage-relative path with traversal check.
     */
    private Path resolveWithinStorage(HttpServletRequest request, String relPath) {
        StorageSpace storageSpace = validateAndGetStorageSpace(request, "Path resolve");
        if (storageSpace == null) return null;
        return resolveWithin(Paths.get(storageSpace.getPath()), relPath);
    }

    /**
     * 在 base 下解析 relPath 并校验不越界。relPath 为空/纯根 → 返回 base。 / Resolve relPath under base, reject traversal.
     */
    private Path resolveWithin(Path base, String relPath) {
        Path basePath = base.toAbsolutePath().normalize();
        String p = relPath == null ? "" : relPath.trim();
        if (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return basePath;
        Path target = basePath.resolve(p).toAbsolutePath().normalize();
        if (!target.startsWith(basePath)) return null; // 路径穿越
        if (containsSymbolicLink(basePath, target)) return null;
        return target;
    }

    /** Reject a path when any existing component below the storage root is a symbolic link. */
    private boolean containsSymbolicLink(Path basePath, Path target) {
        Path current = basePath;
        Path relative = basePath.relativize(target);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 列出某目录的子文件夹 + 文件(目录视图用) / List subfolders + files of a directory.
     * GET /list_dir?path=<relpath>(空=根)
     */
    @GetMapping("/list_dir")
    @ResponseBody
    public ResponseEntity<?> listDir(HttpServletRequest request,
                                     @RequestParam(value = "path", required = false) String path) {
        StorageSpace storageSpace = validateAndGetStorageSpace(request, "list_dir");
        if (storageSpace == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的存储空间");
        }
        Path basePath = Paths.get(storageSpace.getPath()).toAbsolutePath().normalize();
        Path dir = resolveWithin(basePath, path);
        if (dir == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的路径");
        }
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("目录不存在");
        }

        List<Map<String, Object>> folders = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (Files.isSymbolicLink(p)) {
                    continue;
                }
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    Map<String, Object> fo = new HashMap<>();
                    fo.put("name", name);
                    folders.add(fo);
                } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    String rel = basePath.relativize(p).toString().replace('\\', '/');
                    files.add(buildDirRecord(p, rel));
                }
            }
        } catch (IOException e) {
            logger.warn("list_dir failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("读取目录失败");
        }
        folders.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare((String) a.get("name"), (String) b.get("name")));
        files.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare((String) a.get("filename"), (String) b.get("filename")));

        Map<String, Object> resp = new HashMap<>();
        resp.put("path", path == null ? "" : path);
        resp.put("folders", folders);
        resp.put("files", files);
        return ResponseEntity.ok(resp);
    }

    /**
     * 新建文件夹(可多级)。仅 ADMIN。 / Create a folder (nested allowed). ADMIN only.
     * POST /create_folder  body: {"path": "<relpath>"}
     */
    @PostMapping("/create_folder")
    @ResponseBody
    public ResponseEntity<?> createFolder(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        LoginSession session = getSession(request);
        if (session == null || session.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("仅管理员可创建文件夹");
        }
        String path = body == null ? null : (String) body.get("path");
        Path target = resolveWithinStorage(request, path);
        if (target == null || target.toString().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的文件夹路径");
        }
        if (Files.exists(target)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("已存在同名项");
        }
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            logger.warn("create_folder failed for {}: {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("创建文件夹失败");
        }
        logger.info("User {} created folder: {}", session.getUsername(), path);
        return ResponseEntity.ok("OK");
    }

    /**
     * 递归删除文件夹。仅 ADMIN。 / Recursively delete a folder. ADMIN only.
     * DELETE /delete_folder?path=<relpath>
     */
    @DeleteMapping("/delete_folder")
    @ResponseBody
    public ResponseEntity<?> deleteFolder(@RequestParam("path") String folderPath, HttpServletRequest request) throws IOException {
        LoginSession session = getSession(request);
        if (session == null || session.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("仅管理员可删除文件夹");
        }
        StorageSpace storageSpace = validateAndGetStorageSpace(request, "delete_folder");
        if (storageSpace == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的存储空间");
        }
        Path basePath = Paths.get(storageSpace.getPath()).toAbsolutePath().normalize();
        Path target = resolveWithin(basePath, folderPath);
        if (target == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("无效的路径");
        }
        if (target.equals(basePath)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("不能删除存储根目录");
        }
        if (!Files.exists(target)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件夹不存在");
        }
        if (!Files.isDirectory(target)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("目标不是文件夹");
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("delete_folder failed {}: {}", folderPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除文件夹失败");
        }
        logger.info("User {} deleted folder: {}", session.getUsername(), folderPath);
        return ResponseEntity.ok("文件夹删除成功");
    }
}
