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
import java.util.Arrays;
import java.util.List;

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

        List<String> banner = Arrays.asList(
                "========================================",
                "  File Box started",
                "========================================",
                "Local access: http://localhost:" + port + contextPath,
                "LAN access:   http://" + address + ":" + port + contextPath,
                "========================================"
        );

        // 始终写日志:dev 下控制台 appender 开 → IDEA 控制台可见;prod 下进 filebox.log。
        // Always to the log: visible in the IDEA console in dev (console appender on), filebox.log in prod.
        for (String line : banner) {
            logger.info(line);
        }

        // prod profile 下控制台 appender 已关闭,额外 echo 到 stdout,让发布包 start.sh 指向的
        // logs/out.log 也能看到访问地址(运维 tail out.log 时无需再翻 filebox.log)。纯英文 ASCII,
        // 无 Windows System.out 中文乱码风险。dev 不 echo,避免 IDEA 控制台重复打印。
        // Under the prod profile the console appender is off, so echo to stdout too — this puts the
        // access URL into the release package's logs/out.log (the file start.sh advertises), so operators
        // tailing out.log needn't dig into filebox.log. ASCII-only, so no Windows System.out mojibake.
        // Not echoed in dev to avoid duplicating the line on the IDEA console.
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            for (String line : banner) {
                System.out.println(line);
            }
        }
    }
}
