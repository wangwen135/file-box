package com.wwh.filebox.config;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.service.ConfigService;
import com.wwh.filebox.service.FileBoxConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final Environment environment;
    private final ConfigService configService;

    public ConfigValidationRunner(Environment environment, ConfigService configService) {
        this.environment = environment;
        this.configService = configService;
    }

    @Override
    public void run(String... args) throws Exception {
        prepareRuntimeDirectories();

        FileBoxConfigStore.InitializationResult initResult = configService.initializeDefaultConfigIfMissing();
        File configFile = configService.getConfigFile();
        logger.info("File Box business config: {}", configFile.getAbsolutePath());

        if (initResult.isCreated()) {
            logger.info("========================================");
            logger.info("  First-time setup complete");
            logger.info("========================================");
            logger.info("  Username: admin");
            logger.info("  Initial password: {}", initResult.getAdminPassword());
            logger.info("  Change this password after login.");
            logger.info("========================================");
        }

        if (configService.getConfig() == null) {
            throw new IllegalStateException("Failed to load File Box configuration: " + configFile.getAbsolutePath());
        }

        printStartupInfo();
    }

    private void prepareRuntimeDirectories() {
        try {
            Files.createDirectories(Paths.get("./config"));
            Files.createDirectories(Paths.get("./data/default"));
            Files.createDirectories(Paths.get("./logs"));
            Path multipartTmp = Paths.get(AppConstants.FileUpload.MULTIPART_TEMP_DIR);
            Files.createDirectories(multipartTmp);
            logger.info("Multipart temp dir ready: {}", multipartTmp.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create runtime directories", e);
        }
    }

    private void printStartupInfo() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String address = "localhost";

        try {
            address = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            logger.debug("Failed to resolve local host address; using localhost");
        }

        logger.info("========================================");
        logger.info("  File Box started");
        logger.info("========================================");
        logger.info("Local access: http://localhost:{}{}", port, contextPath);
        logger.info("LAN access:   http://{}:{}{}", address, port, contextPath);
        logger.info("========================================");
    }
}
