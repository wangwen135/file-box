package com.wwh.filebox.service;

import com.wwh.filebox.constants.AppConstants;
import com.wwh.filebox.model.SystemConfig;
import com.wwh.filebox.security.SecureTokenGenerator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileBoxConfigStore {

    public static final String DEFAULT_CONFIG_FILE = "./config/filebox.yml";
    public static final String CONFIG_PROPERTY = "filebox.config";
    public static final String CONFIG_ENV = "FILEBOX_CONFIG";

    private final Path configPath;
    private final BCryptPasswordEncoder passwordEncoder;

    public FileBoxConfigStore(Path configPath, BCryptPasswordEncoder passwordEncoder) {
        this.configPath = configPath;
        this.passwordEncoder = passwordEncoder;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public boolean exists() {
        return Files.exists(configPath);
    }

    public long lastModified() throws IOException {
        return Files.getLastModifiedTime(configPath).toMillis();
    }

    public SystemConfig load() throws IOException {
        if (!exists()) {
            return null;
        }

        Yaml yaml = new Yaml();
        try (InputStream input = Files.newInputStream(configPath)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map)) {
                return new SystemConfig();
            }
            return fromYamlMap(castMap(loaded));
        }
    }

    public void save(SystemConfig config) throws IOException {
        Path parent = configPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(configPath)) {
            Files.copy(configPath, configPath.resolveSibling(configPath.getFileName().toString() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        Path tmp = configPath.resolveSibling(configPath.getFileName().toString() + ".tmp");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);

        try (OutputStream output = Files.newOutputStream(tmp)) {
            yaml.dump(toYamlMap(config), new java.io.OutputStreamWriter(output, java.nio.charset.StandardCharsets.UTF_8));
        }

        try {
            Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public InitializationResult initializeDefaultConfigIfMissing() throws IOException {
        if (exists()) {
            return new InitializationResult(false, null, load());
        }

        String password = generateAdminPassword();
        SystemConfig config = createDefaultConfig(AppConstants.Auth.DEFAULT_ADMIN_USERNAME, passwordEncoder.encode(password));
        Files.createDirectories(Paths.get("./data/default"));
        save(config);
        return new InitializationResult(true, password, config);
    }

    public PasswordResetResult resetAdminPassword() throws IOException {
        SystemConfig config;
        if (exists()) {
            config = load();
        } else {
            config = new SystemConfig();
        }

        if (config.getUsers() == null) {
            config.setUsers(new ArrayList<SystemConfig.UserConfig>());
        }

        String password = generateAdminPassword();
        SystemConfig.UserConfig admin = null;
        for (SystemConfig.UserConfig user : config.getUsers()) {
            if (AppConstants.Auth.DEFAULT_ADMIN_USERNAME.equals(user.getUsername())) {
                admin = user;
                break;
            }
        }

        if (admin == null) {
            config = createDefaultConfig(AppConstants.Auth.DEFAULT_ADMIN_USERNAME, passwordEncoder.encode(password));
            Files.createDirectories(Paths.get("./data/default"));
        } else {
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole("ADMIN");
            if (admin.getStorageSpaces() == null || admin.getStorageSpaces().isEmpty()) {
                admin.setStorageSpaces(new ArrayList<>(Arrays.asList("default")));
            }
        }

        save(config);
        return new PasswordResetResult(password, config);
    }

    public static Path resolveConfigPath(String explicitPath) {
        if (explicitPath != null && !explicitPath.trim().isEmpty()) {
            return Paths.get(explicitPath.trim());
        }
        String property = System.getProperty(CONFIG_PROPERTY);
        if (property != null && !property.trim().isEmpty()) {
            return Paths.get(property.trim());
        }
        String env = System.getenv(CONFIG_ENV);
        if (env != null && !env.trim().isEmpty()) {
            return Paths.get(env.trim());
        }
        return Paths.get(DEFAULT_CONFIG_FILE);
    }

    public static String readConfigPathArg(String[] args) {
        String prefix = "--" + CONFIG_PROPERTY + "=";
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }

    public static String generateAdminPassword() {
        // 仅字母数字,便于首启/重置时从控制台复制 / alphanumeric only, easy to copy from the console
        return SecureTokenGenerator.generateAlphanumeric(22);
    }

    public static SystemConfig createDefaultConfig(String adminUsername, String adminPasswordHash) {
        SystemConfig config = new SystemConfig();
        config.setAnonymousUploadEnabled(false);
        config.setShareNoticeEnabled(true);

        SystemConfig.StorageSpaceConfig defaultSpace = new SystemConfig.StorageSpaceConfig();
        defaultSpace.setName("default");
        defaultSpace.setPath("./data/default");
        defaultSpace.setMaxSize("10GB");
        defaultSpace.setAllowAnonymousAccess(false);
        defaultSpace.setAllowAnonymousUpload(false);
        config.setStorageSpaces(new ArrayList<SystemConfig.StorageSpaceConfig>(Arrays.asList(defaultSpace)));

        SystemConfig.UserConfig adminUser = new SystemConfig.UserConfig();
        adminUser.setUsername(adminUsername);
        adminUser.setPassword(adminPasswordHash);
        adminUser.setRole("ADMIN");
        adminUser.setStorageSpaces(new ArrayList<String>(Arrays.asList("default")));
        config.setUsers(new ArrayList<SystemConfig.UserConfig>(Arrays.asList(adminUser)));

        return config;
    }

    private static SystemConfig fromYamlMap(Map<String, Object> root) {
        SystemConfig config = new SystemConfig();
        Map<String, Object> fileBox = castMap(root.get("file-box"));
        if (fileBox == null) {
            fileBox = root;
        }

        Map<String, Object> anonymous = castMap(fileBox.get("anonymous"));
        if (anonymous != null && anonymous.containsKey("enabled")) {
            config.setAnonymousUploadEnabled(Boolean.parseBoolean(String.valueOf(anonymous.get("enabled"))));
        }

        Map<String, Object> system = castMap(fileBox.get("system"));
        if (system != null) {
            Object anonymousUploadEnabled = firstPresent(system, "anonymous-upload-enabled", "anonymousUploadEnabled");
            if (anonymousUploadEnabled != null) {
                config.setAnonymousUploadEnabled(Boolean.parseBoolean(String.valueOf(anonymousUploadEnabled)));
            }
            // 匿名访问总开关;缺省时回退到上传开关旧值(旧配置里访问+上传是绑定的) / access gate; fall back to upload flag for legacy configs
            Object anonymousAccessEnabled = firstPresent(system, "anonymous-access-enabled", "anonymousAccessEnabled");
            if (anonymousAccessEnabled != null) {
                config.setAnonymousAccessEnabled(Boolean.parseBoolean(String.valueOf(anonymousAccessEnabled)));
            } else {
                config.setAnonymousAccessEnabled(config.isAnonymousUploadEnabled());
            }
            Object shareNoticeEnabled = firstPresent(system, "share-notice-enabled", "shareNoticeEnabled");
            if (shareNoticeEnabled != null) {
                config.setShareNoticeEnabled(Boolean.parseBoolean(String.valueOf(shareNoticeEnabled)));
            }
            config.setAllowedOrigins(stringValue(firstPresent(system, "allowed-origins", "allowedOrigins")));
        }

        List<SystemConfig.StorageSpaceConfig> spaces = new ArrayList<SystemConfig.StorageSpaceConfig>();
        Object rawSpaces = fileBox.get("storage-spaces");
        if (rawSpaces instanceof List) {
            for (Object item : (List<?>) rawSpaces) {
                Map<String, Object> map = castMap(item);
                if (map == null) {
                    continue;
                }
                SystemConfig.StorageSpaceConfig space = new SystemConfig.StorageSpaceConfig();
                space.setName(stringValue(map.get("name")));
                space.setPath(stringValue(map.get("path")));
                space.setMaxSize(stringValue(firstPresent(map, "max-size", "maxSize")));
                // 优先读两个新键;缺省则回退旧单键 allow-anonymous(旧值同时表示访问+上传) / prefer the two new keys, fall back to legacy single flag
                Object legacyAnon = firstPresent(map, "allow-anonymous", "allowAnonymous");
                Object allowAccess = firstPresent(map, "allow-anonymous-access", "allowAnonymousAccess");
                Object allowUpload = firstPresent(map, "allow-anonymous-upload", "allowAnonymousUpload");
                boolean legacyVal = legacyAnon != null && Boolean.parseBoolean(String.valueOf(legacyAnon));
                boolean access = allowAccess != null ? Boolean.parseBoolean(String.valueOf(allowAccess)) : legacyVal;
                boolean upload = allowUpload != null ? Boolean.parseBoolean(String.valueOf(allowUpload)) : legacyVal;
                space.setAllowAnonymousAccess(access || upload); // 上传蕴含访问 / upload implies access
                space.setAllowAnonymousUpload(upload);
                spaces.add(space);
            }
        }
        config.setStorageSpaces(spaces);

        List<SystemConfig.UserConfig> users = new ArrayList<SystemConfig.UserConfig>();
        Object rawUsers = fileBox.get("users");
        if (rawUsers instanceof List) {
            for (Object item : (List<?>) rawUsers) {
                Map<String, Object> map = castMap(item);
                if (map == null) {
                    continue;
                }
                SystemConfig.UserConfig user = new SystemConfig.UserConfig();
                user.setUsername(stringValue(map.get("username")));
                user.setPassword(stringValue(map.get("password")));
                user.setRole(stringValue(map.get("role")));
                user.setStorageSpaces(stringList(firstPresent(map, "storage-spaces", "storageSpaces")));
                users.add(user);
            }
        }
        config.setUsers(users);

        return config;
    }

    private static Map<String, Object> toYamlMap(SystemConfig config) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        Map<String, Object> fileBox = new LinkedHashMap<String, Object>();
        root.put("file-box", fileBox);

        Map<String, Object> anonymous = new LinkedHashMap<String, Object>();
        anonymous.put("enabled", config.isAnonymousAccessEnabled()); // 旧版兼容:匿名是否可用的总开关 / legacy: anonymous availability gate
        fileBox.put("anonymous", anonymous);

        Map<String, Object> system = new LinkedHashMap<String, Object>();
        system.put("anonymous-access-enabled", config.isAnonymousAccessEnabled());
        system.put("anonymous-upload-enabled", config.isAnonymousUploadEnabled());
        system.put("share-notice-enabled", config.isShareNoticeEnabled());
        if (config.getAllowedOrigins() != null && !config.getAllowedOrigins().trim().isEmpty()) {
            system.put("allowed-origins", config.getAllowedOrigins());
        }
        fileBox.put("system", system);

        List<Map<String, Object>> spaces = new ArrayList<Map<String, Object>>();
        if (config.getStorageSpaces() != null) {
            for (SystemConfig.StorageSpaceConfig space : config.getStorageSpaces()) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                map.put("name", space.getName());
                map.put("path", space.getPath());
                map.put("max-size", space.getMaxSize());
                map.put("allow-anonymous-access", space.isAllowAnonymousAccess());
                map.put("allow-anonymous-upload", space.isAllowAnonymousUpload());
                spaces.add(map);
            }
        }
        fileBox.put("storage-spaces", spaces);

        List<Map<String, Object>> users = new ArrayList<Map<String, Object>>();
        if (config.getUsers() != null) {
            for (SystemConfig.UserConfig user : config.getUsers()) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                map.put("username", user.getUsername());
                map.put("password", user.getPassword());
                map.put("role", user.getRole());
                map.put("storage-spaces", user.getStorageSpaces() != null ? user.getStorageSpaces() : new ArrayList<String>());
                users.add(map);
            }
        }
        fileBox.put("users", users);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private static Object firstPresent(Map<String, Object> map, String first, String second) {
        if (map == null) {
            return null;
        }
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (value != null) {
            String raw = String.valueOf(value).trim();
            if (!raw.isEmpty()) {
                for (String item : raw.split(",")) {
                    if (!item.trim().isEmpty()) {
                        result.add(item.trim());
                    }
                }
            }
        }
        return result;
    }

    public static class InitializationResult {
        private final boolean created;
        private final String adminPassword;
        private final SystemConfig config;

        public InitializationResult(boolean created, String adminPassword, SystemConfig config) {
            this.created = created;
            this.adminPassword = adminPassword;
            this.config = config;
        }

        public boolean isCreated() {
            return created;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public SystemConfig getConfig() {
            return config;
        }
    }

    public static class PasswordResetResult {
        private final String adminPassword;
        private final SystemConfig config;

        public PasswordResetResult(String adminPassword, SystemConfig config) {
            this.adminPassword = adminPassword;
            this.config = config;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public SystemConfig getConfig() {
            return config;
        }
    }
}
