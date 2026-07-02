package com.wwh.filebox.config;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ConfigValidationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConfigValidationRunner.class);

    private final GroupConfig groupConfig;
    private final Environment environment;
    private final ConfigService configService;

    @Autowired
    public ConfigValidationRunner(GroupConfig groupConfig, Environment environment, ConfigService configService) {
        this.groupConfig = groupConfig;
        this.environment = environment;
        this.configService = configService;
    }

    @Override
    public void run(String... args) throws Exception {
        // 创建 multipart 临时目录（Tomcat 不会自动创建，缺失会导致上传失败）
        // Create the multipart temp dir at startup; Tomcat does NOT auto-create it
        // and will throw "The temporary upload location is not valid" if absent.
        try {
            Path multipartTmp = Paths.get(AppConstants.FileUpload.MULTIPART_TEMP_DIR);
            Files.createDirectories(multipartTmp);
            logger.info("Multipart temp dir ready: {}", multipartTmp.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "无法创建 multipart 临时目录 / Failed to create multipart temp dir: "
                            + AppConstants.FileUpload.MULTIPART_TEMP_DIR, e);
        }

        logger.info("开始配置验证...");

        // Check if new config file exists
        File configFile = configService.getConfigFile();
        if (configFile.exists()) {
            logger.info("发现新的配置文件格式，使用新配置系统");
            logger.info("配置文件路径: {}", configFile.getAbsolutePath());

            // Load and validate config
            if (configService.getConfig() != null) {
                logger.info("配置加载成功");

                // Print startup info
                printStartupInfoNew();
            } else {
                logger.warn("配置文件存在但加载失败");
            }
            return;
        }

        // Fall back to old config validation for backward compatibility
        boolean hasValidGroups = false;

        // 验证普通用户组配置（如果有配置）
        if (groupConfig.getGroups() != null && !groupConfig.getGroups().isEmpty()) {
            hasValidGroups = true;
            for (GroupConfig.Group group : groupConfig.getGroups()) {
                logger.info("正在验证组: {}", group.getName());
                logger.info("  目录: {}", group.getDirectory());
                logger.info("  用户数量: {}", group.getUsers() != null ? group.getUsers().size() : 0);
                if (group.getUsers() != null) {
                    group.getUsers().forEach(user -> logger.info("    用户: {}", user.getUsername()));
                }
            }
        }

        // 验证匿名用户组配置
        GroupConfig.Anonymous anonymous = groupConfig.getAnonymous();
        if (anonymous != null && anonymous.isEnabled()) {
            hasValidGroups = true;
            logger.info("正在验证匿名用户组配置");
            logger.info("  名称: {}", anonymous.getName());
            logger.info("  目录: {}", anonymous.getDirectory());
            logger.info("  角色: {}", anonymous.getRole());
        } else if (anonymous != null) {
            logger.info("匿名用户组已配置但未启用");
        } else {
            logger.info("未配置匿名用户组");
        }

        // If no valid groups found, this is first time setup - allow startup
        if (!hasValidGroups) {
            logger.info("首次启动 - 未检测到配置文件");
            logger.info("请使用默认管理员账号登录 (admin/admin123) 以自动生成配置");
        } else {
            logger.info("配置验证成功完成");
        }

        printStartupInfo();
    }

    /**
     * 打印应用启动信息（统一的启动信息打印方法）
     */
    private void printStartupInfo() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String address = "localhost";

        try {
            address = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            logger.debug("无法获取本地主机地址，使用 localhost");
        }

        logger.info("========================================");
        logger.info("  File Box 应用启动成功！");
        logger.info("========================================");
        logger.info("访问地址:");
        logger.info("  本地访问: http://localhost:{}{}", port, contextPath);
        logger.info("  局域网访问: http://{}:{}{}", address, port, contextPath);
        logger.info("========================================");

        // 打印额外的配置信息（仅针对旧配置系统）
        if (groupConfig != null) {
            if (groupConfig.getAnonymous() != null && groupConfig.getAnonymous().isEnabled()) {
                logger.info("  匿名访问: 已启用");
            } else {
                logger.info("  匿名访问: 已禁用");
            }

            if (groupConfig.getGroups() != null && !groupConfig.getGroups().isEmpty()) {
                logger.info("  用户组数量: {}", groupConfig.getGroups().size());
            }
        }

        logger.info("========================================");
    }

    /**
     * 打印应用启动信息（新配置系统专用）
     * 内部调用统一的printStartupInfo方法
     */
    private void printStartupInfoNew() {
        printStartupInfo();
    }
}
