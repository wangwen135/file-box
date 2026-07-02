package com.wwh.filebox.controller;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.StorageSpace;
import com.wwh.filebox.service.AuthService;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
    public ResponseEntity<String> uploadFile(HttpServletRequest request, @RequestParam("file") MultipartFile[] files) throws IOException {
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
        Path uploadDir = Paths.get(storageDir, DateTimeFormatter.getCurrentYear(), DateTimeFormatter.getCurrentMonth());
        Files.createDirectories(uploadDir);
        String timestamp = DateTimeFormatter.getCurrentTimestamp();

        for (MultipartFile f : files) {
            logger.info("Processing file uploaded by user {}: Original name: {}, Size: {} bytes", username, f.getOriginalFilename(), f.getSize());

            // Check storage space limit
            if (!storageService.hasEnoughSpace(storageSpaceName, f.getSize())) {
                logger.warn("Upload failed for user {}: Storage space {} is full", username, storageSpaceName);
                return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE).body("存储空间不足");
            }

            String filename = FileUtils.safeUnicodeFilename(f.getOriginalFilename() != null ? f.getOriginalFilename() : AppConstants.FileUpload.PASTED_FILE_PREFIX + timestamp + AppConstants.FileUpload.DEFAULT_FILE_EXTENSION);
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
        Path uploadDir = Paths.get(storageDir, DateTimeFormatter.getCurrentYear(), DateTimeFormatter.getCurrentMonth());
        Files.createDirectories(uploadDir);
        String filename = AppConstants.FileUpload.PASTE_FILE_PREFIX + DateTimeFormatter.getCurrentTimestamp() + AppConstants.FileUpload.DEFAULT_FILE_EXTENSION;
        Path savePath = uploadDir.resolve(filename);
        Files.write(savePath, ((String) body.get("text")).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
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

        if (Files.exists(targetPath) && Files.isRegularFile(targetPath)) {
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

        String storageDir = storageSpace.getPath();
        File root = new File(storageDir);
        List<Map<String, Object>> periods = new ArrayList<>();
        if (root.exists() && root.isDirectory()) {
            File[] years = root.listFiles(File::isDirectory);
            if (years != null) {
                for (File ydir : years) {
                    String yname = ydir.getName();
                    if (yname.matches("\\d+")) {
                        File[] months = ydir.listFiles(File::isDirectory);
                        List<String> mlist = new ArrayList<>();
                        if (months != null) {
                            for (File mdir : months) {
                                String mname = mdir.getName();
                                if (mname.matches("\\d+")) {
                                    mlist.add(mname);
                                }
                            }
                        }
                        mlist.sort((a, b) -> Integer.compare(Integer.parseInt(b), Integer.parseInt(a)));
                        Map<String, Object> map = new HashMap<>();
                        map.put("year", yname);
                        map.put("months", mlist);
                        periods.add(map);
                    }
                }
            }
        }
        periods.sort((a, b) -> Integer.parseInt((String) b.get("year")) - Integer.parseInt((String) a.get("year")));
        Map<String, Object> resp = new HashMap<>();
        resp.put("periods", periods);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/list_files")
    @ResponseBody
    public ResponseEntity<?> listFiles(HttpServletRequest request,
                                       @RequestParam(value = "year", required = false) String year,
                                       @RequestParam(value = "month", required = false) String month,
                                       @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) throws IOException {

        // 限制limit的最大值，防止性能问题
        if (limit > 1000) {
            limit = 1000;
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

        String storageDir = storageSpace.getPath();
        Path root = Paths.get(storageDir);

        List<Map<String, Object>> result = new ArrayList<>();
        if (year != null && month != null && !year.isEmpty() && !month.isEmpty()) {
            Path folder = root.resolve(year).resolve(month);
            if (Files.exists(folder) && Files.isDirectory(folder)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
                    for (Path p : ds) {
                        if (Files.isRegularFile(p)) {
                            result.add(buildRecord(p, year, month));
                        }
                    }
                }
                result.sort((a, b) -> ((String) b.get("time")).compareTo((String) a.get("time")));
            }
        } else {
            // 使用try-with-resources确保Stream正确关闭
            // 限制遍历深度为3层（year/month/file）以提高性能
            try {
                List<Map<String, Object>> all = new ArrayList<>();
                Files.walk(root, AppConstants.FileUpload.MAX_TRAVERSE_DEPTH)
                        .filter(Files::isRegularFile)
                        .forEach(p -> {
                            Path rel = root.relativize(p);
                            if (rel.getNameCount() >= 3) {
                                String y = rel.getName(0).toString();
                                String m = rel.getName(1).toString();
                                all.add(buildRecord(p, y, m));
                            }
                        });
                all.sort((a, b) -> ((String) b.get("time")).compareTo((String) a.get("time")));
                int lim = Math.min(limit, all.size());
                result = all.subList(0, lim);
            } catch (IOException e) {
                logger.warn("Error traversing directory: {}", e.getMessage());
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("files", result);
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

        // 使用统一的存储空间验证方法（文件服务不需要检查用户权限）
        String storageSpaceName = getCurrentStorageSpace(request);
        if (storageSpaceName == null) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "未选择存储空间");
            return;
        }

        StorageSpace storageSpace = storageService.getStorageSpace(storageSpaceName);
        if (storageSpace == null) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "存储空间未找到");
            return;
        }

        String storageDir = storageSpace.getPath();

        Path basePath = Paths.get(storageDir).toAbsolutePath().normalize();
        Path target;
        try {
            Path rel = Paths.get(year, month, filename);
            target = basePath.resolve(rel).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            logger.warn("Invalid file path request: {}/{}/{} - {}", year, month, filename, e.getMessage());
            writeError(response, HttpStatus.BAD_REQUEST.value(), "无效的文件路径");
            return;
        }

        // 安全检查：确保目标路径在存储目录内
        if (!target.startsWith(basePath)) {
            logger.warn("Path traversal attempt: {}", target);
            writeError(response, HttpStatus.BAD_REQUEST.value(), "无效的文件路径");
            return;
        }

        if (!Files.exists(target) || !Files.isRegularFile(target)) {
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
                try {
                    byte[] bytes = Files.readAllBytes(fullPath);
                    String txt = new String(bytes, StandardCharsets.UTF_8);
                    content = txt.length() > AppConstants.FileUpload.TEXT_PREVIEW_MAX_LENGTH
                        ? txt.substring(0, AppConstants.FileUpload.TEXT_PREVIEW_MAX_LENGTH)
                        : txt;
                } catch (IOException e) {
                    content = null;
                }
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
}
