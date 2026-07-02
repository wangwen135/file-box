package com.wwh.filebox.config;

import com.wwh.filebox.constants.AppConstants;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.MultipartConfigElement;
import java.io.File;

/**
 * 自定义 multipart 配置 / Custom multipart configuration.
 *
 * <p>关键作用：把 multipart 临时目录解析为【基于 CWD 的绝对路径】。</p>
 * <p>Tomcat 对【相对的】multipart location 会改解析到自身工作目录（通常在 /tmp，多为 tmpfs），
 * 这会导致两个问题：</p>
 * <ol>
 *   <li>上传大文件时临时数据落进 tmpfs（内存），在 -Xmx384m 的本机上有 OOM / 撑爆 tmpfs 的风险；</li>
 *   <li>临时文件与存储目录不在同一文件系统，transferTo 的 rename 会失败，回退为拷贝。</li>
 * </ol>
 * <p>设为绝对路径后，Tomcat 直接使用该路径，临时文件与 ./uploads 同处一个文件系统，
 * transferTo 走 rename，且不占用内存。</p>
 *
 * <p>Resolves the multipart temp location to an ABSOLUTE, CWD-relative path so Tomcat writes
 * upload temp files to the SAME filesystem as storage (enabling transferTo rename, not copy)
 * and avoids buffering large uploads into tmpfs (/tmp) RAM.</p>
 */
@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(MultipartProperties multipartProperties) {
        // 把 yml 中的相对路径（./.multipart-tmp）解析为相对 CWD 的绝对路径
        // Resolve the relative yml path to an absolute, CWD-relative path
        String absoluteLocation = new File(AppConstants.FileUpload.MULTIPART_TEMP_DIR).getAbsolutePath();

        return new MultipartConfigElement(
                absoluteLocation,
                multipartProperties.getMaxFileSize().toBytes(),
                multipartProperties.getMaxRequestSize().toBytes(),
                (int) Math.min(Integer.MAX_VALUE, multipartProperties.getFileSizeThreshold().toBytes())
        );
    }
}
