package com.wwh.filebox.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文件工具类
 * 提供文件处理相关的常用工具方法，包括文件名安全处理、文件类型检测等功能
 */
public class FileUtils {

    /**
     * 安全处理Unicode文件名
     * 功能：移除路径组件、空字符、控制字符，替换斜杠，合并空白符，修剪点和空格，限制长度
     * 用于确保文件名在存储和展示时的安全性和兼容性
     *
     * @param name 原始文件名
     * @return 安全处理后的文件名
     */
    public static String safeUnicodeFilename(String name) {
        if (name == null) name = "file";
        // Basename
        name = Paths.get(name).getFileName().toString();
        // Remove nulls
        name = name.replace("\0", "");
        // Replace separators
        name = name.replace("/", "_").replace("\\", "_");
        // Collapse whitespace
        name = name.trim().replaceAll("\\s+", " ");
        // 安全处理：只移除纯点和空格组成的文件名，保留其他以点开头的合法文件名
        // 但禁止纯点（.）或纯多点（..）作为文件名
        if (name.equals(".") || name.equals("..") || name.matches("^\\.+$")) {
            name = "file";
        }
        // 只移除结尾的空白字符，不移除点
        name = name.replaceAll("\\s+$", "");
        if (name.isEmpty()) name = "file";
        // Normalize to NFC to keep Unicode sensible
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        // Limit length while preserving extension
        int maxTotal = 200;
        if (name.length() > maxTotal) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                String ext = name.substring(dot);
                int maxBase = maxTotal - ext.length();
                name = name.substring(0, Math.min(name.length(), maxBase)) + ext;
            } else {
                name = name.substring(0, maxTotal);
            }
        }
        // Remove control characters
        name = name.replaceAll("[\\p{Cntrl}]", "");
        // Final fallback
        if (name.isEmpty()) name = "file";
        return name;
    }

    /**
     * 根据文件扩展名获取MIME类型
     * 支持各种常见的图片、视频、文档等格式
     *
     * @param filename 文件名
     * @return 对应的MIME类型
     */
    public static String getContentTypeByExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "application/octet-stream";
        }

        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            ext = filename.substring(dot + 1).toLowerCase();
        }

        // 图片类型
        List<String> imageExts = Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tiff", "ico");
        if (imageExts.contains(ext)) {
            switch (ext) {
                case "png":
                    return "image/png";
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "gif":
                    return "image/gif";
                case "webp":
                    return "image/webp";
                case "bmp":
                    return "image/bmp";
                case "svg":
                    return "image/svg+xml";
                case "tiff":
                    return "image/tiff";
                case "ico":
                    return "image/x-icon";
                default:
                    return "image/*";
            }
        }

        // 视频类型
        List<String> videoExts = Arrays.asList("mp4", "webm", "ogg", "mov", "avi", "wmv", "mkv", "flv", "m4v");
        if (videoExts.contains(ext)) {
            switch (ext) {
                case "mp4":
                    return "video/mp4";
                case "webm":
                    return "video/webm";
                case "ogg":
                    return "video/ogg";
                case "mov":
                    return "video/quicktime";
                case "avi":
                    return "video/x-msvideo";
                case "wmv":
                    return "video/x-ms-wmv";
                case "mkv":
                    return "video/x-matroska";
                case "flv":
                    return "video/x-flv";
                case "m4v":
                    return "video/mp4";
                default:
                    return "video/*";
            }
        }

        // 音频类型
        List<String> audioExts = Arrays.asList("mp3", "wav", "ogg", "flac", "m4a", "wma", "aac", "opus");
        if (audioExts.contains(ext)) {
            switch (ext) {
                case "mp3":
                    return "audio/mpeg";
                case "wav":
                    return "audio/wav";
                case "ogg":
                    return "audio/ogg";
                case "flac":
                    return "audio/flac";
                case "m4a":
                    return "audio/mp4";
                case "wma":
                    return "audio/x-ms-wma";
                case "aac":
                    return "audio/aac";
                case "opus":
                    return "audio/opus";
                default:
                    return "audio/*";
            }
        }

        // 文档类型
        List<String> docExts = Arrays.asList("txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp");
        if (docExts.contains(ext)) {
            switch (ext) {
                case "txt":
                    return "text/plain; charset=UTF-8"; //目前只支持UTF-8编码
                case "pdf":
                    return "application/pdf";
                case "doc":
                    return "application/msword";
                case "docx":
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls":
                    return "application/vnd.ms-excel";
                case "xlsx":
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "ppt":
                    return "application/vnd.ms-powerpoint";
                case "pptx":
                    return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "odt":
                    return "application/vnd.oasis.opendocument.text";
                case "ods":
                    return "application/vnd.oasis.opendocument.spreadsheet";
                case "odp":
                    return "application/vnd.oasis.opendocument.presentation";
                default:
                    return "application/octet-stream";
            }
        }

        // 压缩文件类型
        List<String> archiveExts = Arrays.asList("zip", "rar", "tar", "gz", "7z", "bz2", "xz");
        if (archiveExts.contains(ext)) {
            switch (ext) {
                case "zip":
                    return "application/zip";
                case "rar":
                    return "application/x-rar-compressed";
                case "tar":
                    return "application/x-tar";
                case "gz":
                    return "application/gzip";
                case "7z":
                    return "application/x-7z-compressed";
                case "bz2":
                    return "application/x-bzip2";
                case "xz":
                    return "application/x-xz";
                default:
                    return "application/octet-stream";
            }
        }

        // 网页和脚本类型
        if ("html".equals(ext)) return "text/html";
        if ("css".equals(ext)) return "text/css";
        if ("js".equals(ext)) return "application/javascript";
        if ("json".equals(ext)) return "application/json";
        if ("xml".equals(ext)) return "application/xml";

        // 默认返回二进制流
        return "application/octet-stream";
    }

    /**
     * 获取文件类型分类（用于前端展示）
     *
     * @param filename 文件名
     * @return 文件类型分类(image, video, audio, text, document, archive, file)
     */
    public static String getFileTypeCategory(String filename) {
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) ext = filename.substring(dot + 1).toLowerCase();

        if (Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tiff", "ico").contains(ext))
            return "image";
        if (Arrays.asList("mp4", "webm", "ogg", "mov", "avi", "wmv", "mkv", "flv", "m4v").contains(ext)) return "video";
        if (Arrays.asList("mp3", "wav", "ogg", "flac", "m4a", "wma", "aac", "opus").contains(ext)) return "audio";
        if ("txt".equals(ext)) return "text";
        if (Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp").contains(ext))
            return "document";
        if (Arrays.asList("zip", "rar", "tar", "gz", "7z", "bz2", "xz").contains(ext)) return "archive";
        return "file";
    }

    /**
     * 编码文件名用于HTTP响应头中的Content-Disposition
     * 根据RFC 5987和RFC 6266标准，支持非ASCII字符文件名在各种浏览器中正确显示
     *
     * @param filename 原始文件名
     * @return 编码后的文件名，可直接用于Content-Disposition头
     */
    public static String encodeFilenameForHttpHeader(String filename) {
        if (filename == null) {
            return "file";
        }

        // 首先检查文件名是否只包含ASCII字符
        boolean isAscii = true;
        for (char c : filename.toCharArray()) {
            if (c > 127) {
                isAscii = false;
                break;
            }
        }

        // 如果是纯ASCII文件名，直接返回，添加引号以处理包含空格等特殊字符的情况
        if (isAscii) {
            return quoteFilename(filename);
        }

        // 对于非ASCII文件名，使用RFC 5987标准编码
        // 同时提供一个ASCII后备名称，以兼容不支持RFC 5987的旧浏览器
        String encodedFilename = Base64.getEncoder().encodeToString(filename.getBytes(StandardCharsets.UTF_8));

        // 返回包含UTF-8编码和ASCII后备名称的格式
        return String.format("=?UTF-8?B?%s?=; filename*=UTF-8''%s", encodedFilename, percentEncode(filename));
    }

    /**
     * 对文件名进行百分比编码
     *
     * @param filename 原始文件名
     * @return 百分比编码后的文件名
     */
    private static String percentEncode(String filename) {
        StringBuilder result = new StringBuilder();
        byte[] bytes = filename.getBytes(StandardCharsets.UTF_8);

        for (byte b : bytes) {
            // 保留字母数字字符和一些安全的特殊字符
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') ||
                    b == '-' || b == '_' || b == '.' || b == '!' || b == '~' || b == '*' ||
                    b == '\'' || b == '(' || b == ')') {
                result.append((char) b);
            } else {
                // 其他字符使用%XX格式编码
                result.append(String.format("%%%02X", b & 0xFF));
            }
        }

        return result.toString();
    }

    /**
     * 为文件名添加引号，确保在HTTP头中正确解析
     *
     * @param filename 原始文件名
     * @return 添加引号后的文件名
     */
    private static String quoteFilename(String filename) {
        // 如果文件名包含引号，先对其进行转义
        filename = filename.replace("\"", "\\\"");
        return '"' + filename + '"';
    }
}
