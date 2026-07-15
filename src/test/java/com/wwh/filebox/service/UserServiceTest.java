package com.wwh.filebox.service;

import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    @TempDir
    Path tempDir;
    private Path configPath;
    private ConfigService configService;
    private UserService userService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("filebox.yml");
        // ConfigService.init() 通过该系统属性定位配置文件 / ConfigService.init() resolves via this system property
        System.setProperty(FileBoxConfigStore.CONFIG_PROPERTY, configPath.toString());
        configService = new ConfigService(encoder);
        configService.init();
        userService = new UserService();
        ReflectionTestUtils.setField(userService, "configService", configService);
        ReflectionTestUtils.setField(userService, "passwordEncoder", encoder);
    }

    private void seed(SystemConfig config) {
        configService.saveConfig(config);
    }

    /** 构造含若干 ADMIN 的配置 / build a config with the given admin usernames. */
    private SystemConfig configWithAdmins(String... adminNames) {
        SystemConfig config = FileBoxConfigStore.createDefaultConfig(adminNames[0], encoder.encode("x"));
        List<SystemConfig.UserConfig> users = new ArrayList<>(config.getUsers());
        for (int i = 1; i < adminNames.length; i++) {
            SystemConfig.UserConfig u = new SystemConfig.UserConfig();
            u.setUsername(adminNames[i]);
            u.setPassword(encoder.encode("x"));
            u.setRole(Role.ADMIN.name());
            u.setStorageSpaces(new ArrayList<>());
            users.add(u);
        }
        config.setUsers(users);
        return config;
    }

    @Test
    void deleteUserRefusesToRemoveLastAdmin() {
        seed(configWithAdmins("admin"));
        assertThatThrownBy(() -> userService.deleteUser("admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员");
    }

    @Test
    void deleteUserAllowsRemovingAdminWhenOthersExist() {
        seed(configWithAdmins("admin", "admin2"));
        assertThat(userService.deleteUser("admin2")).isTrue();
        assertThat(configService.getConfig().getUsers()).extracting(SystemConfig.UserConfig::getUsername)
                .containsExactly("admin");
    }

    @Test
    void deleteUserAllowsRemovingNonAdmin() {
        SystemConfig config = configWithAdmins("admin");
        SystemConfig.UserConfig bob = new SystemConfig.UserConfig();
        bob.setUsername("bob");
        bob.setPassword(encoder.encode("x"));
        bob.setRole(Role.USER.name());
        bob.setStorageSpaces(new ArrayList<>());
        config.getUsers().add(bob);
        seed(config);

        assertThat(userService.deleteUser("bob")).isTrue();
    }
}
