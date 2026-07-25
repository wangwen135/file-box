package com.wwh.filebox.service;

import com.wwh.filebox.model.LoginRecord;
import com.wwh.filebox.service.LoginLogReader.LoginFilter;
import com.wwh.filebox.service.LoginLogReader.LoginPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginLogReaderTest {

    @TempDir
    Path tempDir;
    private JsonlLog log;
    private LoginLogReader reader;

    @BeforeEach
    void seed() throws Exception {
        Path file = tempDir.resolve("login-history.jsonl");
        log = new JsonlLog(file, 1000);
        reader = new LoginLogReader(file);
        // 时间递增,最新为 t4 / ascending times, newest is t4
        log.append(rec(1, "admin", true, null));
        log.append(rec(2, "alice", false, "密码错误"));
        log.append(rec(3, "admin", false, "密码错误"));
        log.append(rec(4, "bob", true, null));
    }

    @Test
    void readsNewestFirstAndPaginates() {
        LoginPage p1 = reader.read(noFilter(), 0, 2);
        assertThat(p1.total).isEqualTo(4);
        assertThat(users(p1)).containsExactly("bob", "admin");

        LoginPage p2 = reader.read(noFilter(), 2, 2);
        assertThat(p2.total).isEqualTo(4);
        assertThat(users(p2)).containsExactly("alice", "admin");
    }

    @Test
    void filtersByUsernameAndSuccess() {
        // admin 的两次(一成一败),最新在前:admin(失败) 在前 / admin's two attempts, newest first
        LoginPage admin = reader.read(new LoginFilter("admin", null, null, null), 0, 50);
        assertThat(admin.total).isEqualTo(2);
        assertThat(users(admin)).containsExactly("admin", "admin");

        // 只看失败 / failures only: alice, admin(失败) → 最新在前 admin, alice
        LoginPage failed = reader.read(new LoginFilter(null, false, null, null), 0, 50);
        assertThat(failed.total).isEqualTo(2);
        assertThat(users(failed)).containsExactly("admin", "alice");

        // 只看成功 / successes only: admin(t1), bob(t4) → bob, admin
        LoginPage ok = reader.read(new LoginFilter(null, true, null, null), 0, 50);
        assertThat(users(ok)).containsExactly("bob", "admin");
    }

    @Test
    void returnsEmptyForMissingFile() {
        LoginLogReader missing = new LoginLogReader(tempDir.resolve("nope.jsonl"));
        LoginPage page = missing.read(noFilter(), 0, 50);
        assertThat(page.records).isEmpty();
        assertThat(page.total).isZero();
    }

    private LoginFilter noFilter() {
        return new LoginFilter(null, null, null, null);
    }

    private List<String> users(LoginPage page) {
        List<String> out = new ArrayList<>();
        for (LoginRecord r : page.records) {
            out.add(r.getUsername());
        }
        return out;
    }

    private long time(int t) {
        return 1_700_000_000_000L + t;
    }

    private LoginRecord rec(int t, String username, boolean success, String reason) {
        return new LoginRecord(time(t), username, "1.2.3.4", success, reason, true);
    }
}
