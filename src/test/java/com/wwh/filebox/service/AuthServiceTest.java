package com.wwh.filebox.service;

import com.wwh.filebox.model.LoginSession;
import com.wwh.filebox.model.Role;
import com.wwh.filebox.model.SystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    @TempDir
    Path tempDir;
    private ConfigService configService;
    private AuthService authService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        System.setProperty(FileBoxConfigStore.CONFIG_PROPERTY, tempDir.resolve("filebox.yml").toString());
        configService = new ConfigService(encoder);
        configService.init();
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "configService", configService);
        ReflectionTestUtils.setField(authService, "passwordEncoder", encoder);
    }

    /** 配置一个 USER(拥有 default 空间)/ seed a USER with the default space. */
    private void seedUser(String username, String password) {
        SystemConfig config = FileBoxConfigStore.createDefaultConfig("admin", encoder.encode("adminpw"));
        SystemConfig.UserConfig u = new SystemConfig.UserConfig();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(Role.USER.name());
        u.setStorageSpaces(new ArrayList<>(Arrays.asList("default")));
        config.getUsers().add(u);
        configService.saveConfig(config);
    }

    @Test
    void invalidateSessionsForUserKillsAllTheirSessions() {
        seedUser("alice", "pw");
        String t1 = authService.login("alice", "pw");
        String t2 = authService.login("alice", "pw");
        assertThat(t1).isNotNull();
        assertThat(t2).isNotNull();

        authService.invalidateSessionsForUser("alice");

        assertThat(authService.getSession(t1)).isNull();
        assertThat(authService.getSession(t2)).isNull();
    }

    @Test
    void invalidateSessionsForUserLeavesOthersAlone() {
        seedUser("alice", "pw");
        seedUserKeepSpaces("bob", "pw");
        String aliceToken = authService.login("alice", "pw");
        String bobToken = authService.login("bob", "pw");

        authService.invalidateSessionsForUser("alice");

        assertThat(authService.getSession(aliceToken)).isNull();
        assertThat(authService.getSession(bobToken)).isNotNull();
    }

    // seedUser 会重写整张用户表,这里在现有配置上追加第二个用户 /
    // seedUser rewrites the whole user table; this appends a second user to the existing config.
    private void seedUserKeepSpaces(String username, String password) {
        SystemConfig config = configService.getConfig();
        SystemConfig.UserConfig u = new SystemConfig.UserConfig();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(Role.USER.name());
        u.setStorageSpaces(new ArrayList<>(Arrays.asList("default")));
        config.getUsers().add(u);
        configService.saveConfig(config);
    }

    private void setSpaces(String username, String... spaces) {
        SystemConfig config = configService.getConfig();
        for (SystemConfig.UserConfig u : config.getUsers()) {
            if (u.getUsername().equals(username)) {
                u.setStorageSpaces(new ArrayList<>(Arrays.asList(spaces)));
            }
        }
        configService.saveConfig(config);
    }

    @Test
    void refreshStorageSpacesPicksUpNewlyAssignedSpace() {
        seedUser("alice", "pw");
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"});
        setSpaces("alice", "default", "photos");

        authService.refreshStorageSpaces(session);

        assertThat(session.getStorageSpaces()).containsExactly("default", "photos");
    }

    @Test
    void refreshStorageSpacesClampsRemovedCurrentSpace() {
        seedUser("alice", "pw");
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"});
        assertThat(session.getCurrentStorageSpace()).isEqualTo("default");
        setSpaces("alice", "photos"); // default 被移除 / default removed

        authService.refreshStorageSpaces(session);

        assertThat(session.getStorageSpaces()).containsExactly("photos");
        assertThat(session.getCurrentStorageSpace()).isEqualTo("photos");
    }

    @Test
    void refreshStorageSpacesClearsCurrentWhenEmpty() {
        seedUser("alice", "pw");
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"});
        setSpaces("alice"); // 清空 / cleared

        authService.refreshStorageSpaces(session);

        assertThat(session.getStorageSpaces()).isEmpty();
        assertThat(session.getCurrentStorageSpace()).isNull();
    }
}
