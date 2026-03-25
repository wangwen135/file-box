package com.wwh.filebox.service;

import com.wwh.filebox.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Configuration service
 * 配置服务
 */
@Service
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    private static final String CONFIG_FILE = "application.yml";

    private SystemConfig configCache;
    private long configLastModified = 0;

    @Value("${spring.application.name:File-Box}")
    private String appName;

    public File getConfigFile() {
        return new File(CONFIG_FILE);
    }

    public SystemConfig getConfig() {
        File configFile = getConfigFile();

        if (configFile.exists() && (configCache == null || configFile.lastModified() > configLastModified)) {
            loadConfig();
        }

        return configCache;
    }

    /**
     * Load configuration from file
     * 从文件加载配置
     */
    private void loadConfig() {
        File configFile = getConfigFile();

        if (!configFile.exists()) {
            logger.warn("Configuration file not found: {}", configFile.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }

            configCache = parseYaml(lines);
            configLastModified = configFile.lastModified();

            logger.info("Configuration loaded from: {}", configFile.getAbsolutePath());

            // Log user count only (no sensitive data)
            if (configCache.getUsers() != null && !configCache.getUsers().isEmpty()) {
                logger.info("Loaded {} user(s)", configCache.getUsers().size());
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
        }
    }

    /**
     * Parse YAML configuration from list of lines
     * 从行列表解析YAML配置
     */
    private SystemConfig parseYaml(List<String> lines) {
        SystemConfig config = new SystemConfig();
        List<SystemConfig.StorageSpaceConfig> storageSpaces = new ArrayList<>();
        List<SystemConfig.UserConfig> users = new ArrayList<>();

        String section = "";
        SystemConfig.StorageSpaceConfig currentSpace = null;
        SystemConfig.UserConfig currentUser = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            int indent = getIndent(line);

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            // Handle section changes based on indent
            if (indent < 4 && (trimmed.endsWith(":") || trimmed.startsWith("- "))) {
                // Top-level section
                if (trimmed.startsWith("file-box:")) {
                    section = "file-box";
                } else if (trimmed.startsWith("system:") || (indent == 2 && trimmed.startsWith("system:"))) {
                    section = "system";
                } else if (trimmed.startsWith("storage-spaces:") || (indent == 2 && trimmed.startsWith("storage-spaces:"))) {
                    section = "storage-spaces";
                } else if (trimmed.startsWith("users:")) {
                    section = "users";
                    currentUser = null;
                } else if (trimmed.startsWith("anonymous:")) {
                    section = "anonymous";
                }
                continue;
            }

            // Parse anonymous section
            if (section.equals("file-box") && indent == 2 && trimmed.startsWith("anonymous:")) {
                section = "anonymous";
                continue;
            }

            // Parse anonymous properties
            if (section.equals("anonymous") && indent == 4) {
                if (trimmed.startsWith("enabled:")) {
                    config.setAnonymousUploadEnabled(Boolean.parseBoolean(trimmed.substring(8).trim()));
                }
                continue;
            }

            // Parse system properties
            if (section.equals("system") && indent == 4) {
                if (trimmed.startsWith("name:")) {
                    config.setName(trimmed.substring(5).trim());
                } else if (trimmed.startsWith("description:")) {
                    config.setDescription(trimmed.substring(12).trim());
                } else if (trimmed.startsWith("anonymous-upload-enabled:") || trimmed.startsWith("anonymousUploadEnabled:")) {
                    config.setAnonymousUploadEnabled(Boolean.parseBoolean(trimmed.substring(trimmed.indexOf(":") + 1).trim()));
                } else if (trimmed.startsWith("allowed-origins:") || trimmed.startsWith("allowedOrigins:")) {
                    config.setAllowedOrigins(trimmed.substring(trimmed.indexOf(":") + 1).trim());
                }
                continue;
            }

            // Parse storage space item
            if (section.equals("storage-spaces") && indent == 4 && trimmed.startsWith("- name:")) {
                currentSpace = new SystemConfig.StorageSpaceConfig();
                currentSpace.setName(trimmed.substring(7).trim());
                storageSpaces.add(currentSpace);
                continue;
            }

            // Parse storage space properties
            if (section.equals("storage-spaces") && indent == 6 && currentSpace != null) {
                if (trimmed.startsWith("path:")) {
                    currentSpace.setPath(trimmed.substring(5).trim());
                } else if (trimmed.startsWith("max-size:") || trimmed.startsWith("maxSize:")) {
                    currentSpace.setMaxSize(trimmed.substring(trimmed.indexOf(":") + 1).trim());
                } else if (trimmed.startsWith("url-prefix:") || trimmed.startsWith("urlPrefix:")) {
                    currentSpace.setUrlPrefix(trimmed.substring(trimmed.indexOf(":") + 1).trim());
                } else if (trimmed.startsWith("domain:")) {
                    // Legacy support: map domain to urlPrefix
                    String domain = trimmed.substring(7).trim();
                    currentSpace.setUrlPrefix(domain);
                } else if (trimmed.startsWith("allow-anonymous:") || trimmed.startsWith("allowAnonymous:")) {
                    currentSpace.setAllowAnonymous(Boolean.parseBoolean(trimmed.substring(trimmed.indexOf(":") + 1).trim()));
                }
                continue;
            }

            // Parse user item
            if (section.equals("users") && indent == 4 && trimmed.startsWith("- username:")) {
                currentUser = new SystemConfig.UserConfig();
                currentUser.setUsername(trimmed.substring(11).trim());
                users.add(currentUser);
                continue;
            }

            // Parse user properties
            if (section.equals("users") && indent == 6 && currentUser != null) {
                if (trimmed.startsWith("password:")) {
                    String password = trimmed.substring(9).trim();
                    currentUser.setPassword(password);
                } else if (trimmed.startsWith("role:")) {
                    currentUser.setRole(trimmed.substring(5).trim());
                } else if (trimmed.startsWith("storage-spaces:") || trimmed.startsWith("storageSpaces:")) {
                    // Parse [default]
                    String listStr = trimmed.substring(trimmed.indexOf("[") + 1, trimmed.indexOf("]")).trim();
                    String[] spaces = listStr.split(",");
                    for (String space : spaces) {
                        if (!space.trim().isEmpty()) {
                            currentUser.getStorageSpaces().add(space.trim());
                        }
                    }
                }
                continue;
            }
        }

        config.setStorageSpaces(storageSpaces);
        config.setUsers(users);

        // Debug: log loaded users
        logger.info("Loaded {} user(s)", users.size());
        for (SystemConfig.UserConfig user : users) {
            logger.info("  User: {}, Role: {}, Password: {}", user.getUsername(), user.getRole(), user.getPassword());
        }

        // Set defaults
        if (config.getName() == null || config.getName().isEmpty()) {
            config.setName("File Box");
        }

        return config;
    }

    private int getIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    /**
     * Save configuration to file
     * 保存配置到文件
     */
    public void saveConfig(SystemConfig config) {
        this.configCache = config;

        File configFile = getConfigFile();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))) {
            // Write YAML content (no BOM - BOM can cause parsing issues)
            writer.write(generateYaml(config));
            configLastModified = System.currentTimeMillis();

            logger.info("Configuration saved to: {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }

    /**
     * Generate YAML configuration from SystemConfig object
     * 从SystemConfig对象生成YAML配置
     */
    private String generateYaml(SystemConfig config) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("file-box:\n");
        yaml.append("  anonymous:\n");
        yaml.append("    enabled: ").append(config.isAnonymousUploadEnabled()).append("\n");
        yaml.append("  system:\n");
        yaml.append("    name: ").append(config.getName()).append("\n");
        yaml.append("    description: ").append(config.getDescription() != null ? config.getDescription() : "").append("\n");
        yaml.append("    anonymous-upload-enabled: ").append(config.isAnonymousUploadEnabled()).append("\n");
        if (config.getAllowedOrigins() != null && !config.getAllowedOrigins().isEmpty()) {
            yaml.append("    allowed-origins: ").append(config.getAllowedOrigins()).append("\n");
        }
        yaml.append("  storage-spaces:\n");

        if (config.getStorageSpaces() != null) {
            for (SystemConfig.StorageSpaceConfig space : config.getStorageSpaces()) {
                yaml.append("    - name: ").append(space.getName()).append("\n");
                yaml.append("      path: ").append(space.getPath()).append("\n");
                yaml.append("      max-size: ").append(space.getMaxSize()).append("\n");
                yaml.append("      url-prefix: ").append(space.getUrlPrefix() != null ? space.getUrlPrefix() : "").append("\n");
                yaml.append("      allow-anonymous: ").append(space.isAllowAnonymous()).append("\n");
            }
        }

        yaml.append("  users:\n");
        if (config.getUsers() != null) {
            for (SystemConfig.UserConfig user : config.getUsers()) {
                yaml.append("    - username: ").append(user.getUsername()).append("\n");
                yaml.append("      password: ").append(user.getPassword()).append("\n");
                yaml.append("      role: ").append(user.getRole()).append("\n");
                yaml.append("      storage-spaces: [").append(String.join(", ", user.getStorageSpaces())).append("]\n");
            }
        }

        return yaml.toString();
    }

    /**
     * Create default configuration
     * 创建默认配置
     */
    public SystemConfig createDefaultConfig(String adminUsername, String adminPasswordHash) {
        SystemConfig config = new SystemConfig();
        config.setName("File Box");
        config.setDescription("文件共享和管理工具");
        config.setAnonymousUploadEnabled(false);

        // Create default storage space
        SystemConfig.StorageSpaceConfig defaultSpace = new SystemConfig.StorageSpaceConfig();
        defaultSpace.setName("default");
        defaultSpace.setPath("./uploads");
        defaultSpace.setMaxSize("10GB");
        defaultSpace.setUrlPrefix("http://localhost:8080");
        defaultSpace.setAllowAnonymous(false);

        List<SystemConfig.StorageSpaceConfig> spaces = new ArrayList<>();
        spaces.add(defaultSpace);
        config.setStorageSpaces(spaces);

        // Create admin user
        SystemConfig.UserConfig adminUser = new SystemConfig.UserConfig();
        adminUser.setUsername(adminUsername);
        adminUser.setPassword(adminPasswordHash);
        adminUser.setRole("ADMIN");
        adminUser.setStorageSpaces(Arrays.asList("default"));

        List<SystemConfig.UserConfig> users = new ArrayList<>();
        users.add(adminUser);
        config.setUsers(users);

        return config;
    }

    public void reload() {
        // Force reload by resetting last modified time
        configLastModified = 0;
        loadConfig();
    }

    public boolean configExists() {
        return getConfigFile().exists();
    }
}
