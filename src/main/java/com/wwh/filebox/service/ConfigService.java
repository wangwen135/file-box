package com.wwh.filebox.service;

import com.wwh.filebox.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${filebox.config:}")
    private String configuredConfigFile;

    private FileBoxConfigStore store;
    private SystemConfig configCache;
    private long configLastModified = 0;

    public ConfigService(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        Path configPath = FileBoxConfigStore.resolveConfigPath(configuredConfigFile);
        this.store = new FileBoxConfigStore(configPath, passwordEncoder);
        logger.info("File Box business config path: {}", configPath.toAbsolutePath());
    }

    public File getConfigFile() {
        return store.getConfigPath().toFile();
    }

    public SystemConfig getConfig() {
        File configFile = getConfigFile();
        try {
            if (configFile.exists() && (configCache == null || store.lastModified() > configLastModified)) {
                loadConfig();
            }
        } catch (IOException e) {
            logger.error("Failed to inspect configuration file", e);
        }
        return configCache;
    }

    public void loadConfig() {
        try {
            configCache = store.load();
            configLastModified = configCache != null && configExists() ? store.lastModified() : 0;
            if (configCache != null) {
                int userCount = configCache.getUsers() != null ? configCache.getUsers().size() : 0;
                logger.info("Configuration loaded from: {}", getConfigFile().getAbsolutePath());
                logger.info("Loaded {} user(s)", userCount);
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
        }
    }

    public FileBoxConfigStore.InitializationResult initializeDefaultConfigIfMissing() {
        try {
            FileBoxConfigStore.InitializationResult result = store.initializeDefaultConfigIfMissing();
            configCache = result.getConfig();
            configLastModified = configExists() ? store.lastModified() : 0;
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize File Box configuration", e);
        }
    }

    public void saveConfig(SystemConfig config) {
        try {
            store.save(config);
            this.configCache = config;
            this.configLastModified = store.lastModified();
            logger.info("Configuration saved to: {}", getConfigFile().getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
            throw new IllegalStateException("Failed to save configuration", e);
        }
    }

    public SystemConfig createDefaultConfig(String adminUsername, String adminPasswordHash) {
        return FileBoxConfigStore.createDefaultConfig(adminUsername, adminPasswordHash);
    }

    public void reload() {
        configLastModified = 0;
        loadConfig();
    }

    public boolean configExists() {
        return store.exists();
    }
}
