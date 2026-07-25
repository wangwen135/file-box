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
import java.util.Map;

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

    @Test
    void loginRecordsLoginIp() {
        seedUser("alice", "pw");
        String token = authService.login("alice", "pw", false, "203.0.113.9");

        LoginSession s = authService.getSession(token);
        assertThat(s).isNotNull();
        assertThat(s.getLoginIp()).isEqualTo("203.0.113.9");
        assertThat(s.isAnonymous()).isFalse();
        assertThat(s.getLastActiveMillis()).isGreaterThan(0L);
    }

    @Test
    void anonymousLoginIsMarkedAndCarriesIp() {
        seedUser("alice", "pw");
        enableAnonymous();
        String token = authService.loginAnonymous("198.51.100.7");

        LoginSession s = authService.getSession(token);
        assertThat(s).isNotNull();
        assertThat(s.isAnonymous()).isTrue();
        assertThat(s.getLoginIp()).isEqualTo("198.51.100.7");
        assertThat(s.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void invalidateSessionRemovesOnlyThatOne() {
        seedUser("alice", "pw");
        String t1 = authService.login("alice", "pw");
        String t2 = authService.login("alice", "pw");

        assertThat(authService.invalidateSession(t1)).isTrue();
        assertThat(authService.getSession(t1)).isNull();
        assertThat(authService.getSession(t2)).isNotNull();
        assertThat(authService.invalidateSession("does-not-exist")).isFalse();
    }

    @Test
    void getActiveSessionsExposesIpAnonymousAndLastActive() {
        seedUser("alice", "pw");
        enableAnonymous();
        String alice = authService.login("alice", "pw", true, "10.0.0.1");
        String anon = authService.loginAnonymous("10.0.0.2");
        authService.getSession(alice); // 触发一次访问,刷新 lastActive / touch to refresh lastActive

        List<Map<String, Object>> list = authService.getActiveSessions();
        Map<String, Object> aliceRow = row(list, alice);
        Map<String, Object> anonRow = row(list, anon);

        assertThat(aliceRow.get("loginIp")).isEqualTo("10.0.0.1");
        assertThat(aliceRow.get("anonymous")).isEqualTo(false);
        assertThat(anonRow.get("loginIp")).isEqualTo("10.0.0.2");
        assertThat(anonRow.get("anonymous")).isEqualTo(true);
        assertThat(((Number) aliceRow.get("lastActiveMillis")).longValue()).isGreaterThan(0L);
    }

    private void enableAnonymous() {
        SystemConfig config = configService.getConfig();
        config.setAnonymousAccessEnabled(true);
        if (config.getStorageSpaces() != null) {
            for (SystemConfig.StorageSpaceConfig space : config.getStorageSpaces()) {
                space.setAllowAnonymousAccess(true);
            }
        }
        configService.saveConfig(config);
    }

    private Map<String, Object> row(List<Map<String, Object>> list, String token) {
        return list.stream()
                .filter(m -> token.equals(m.get("token")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no session row for token " + token));
    }
}
