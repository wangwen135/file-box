package com.wwh.filebox.model;

import org.junit.jupiter.api.Test;

import static com.wwh.filebox.constants.AppConstants.Auth;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoginSession 会话有效期测试 / LoginSession session-TTL tests.
 *
 * 这些测试锁定"记住我"=30 天、否则 1 天的会话有效期契约,作为常量提取重构的安全网。
 * These lock the contract (remember-me = 30 days, else 1 day) as a safety net for the
 * constant-extraction refactor.
 */
class LoginSessionTest {

    @Test
    void rememberMeSessionExpiresAfterThirtyDays() {
        long before = System.currentTimeMillis();
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"}, true);
        long after = System.currentTimeMillis();

        assertThat(session.isRememberMe()).isTrue();
        // expiryTime 在 [before, after] 区间内被设置为 now + TTL / set to now + TTL somewhere in [before, after]
        assertThat(session.getExpiryTime())
                .isBetween(before + Auth.SESSION_TTL_REMEMBER_ME_MS, after + Auth.SESSION_TTL_REMEMBER_ME_MS);
    }

    @Test
    void nonRememberMeSessionExpiresAfterOneDay() {
        long before = System.currentTimeMillis();
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"}, false);
        long after = System.currentTimeMillis();

        assertThat(session.isRememberMe()).isFalse();
        assertThat(session.getExpiryTime())
                .isBetween(before + Auth.SESSION_TTL_DEFAULT_MS, after + Auth.SESSION_TTL_DEFAULT_MS);
    }

    @Test
    void extendExpiryRespectsRememberMeFlag() {
        LoginSession remembered = new LoginSession("alice", Role.USER, new String[]{"default"}, true);
        LoginSession ephemeral = new LoginSession("bob", Role.USER, new String[]{"default"}, false);

        long before = System.currentTimeMillis();
        remembered.extendExpiry();
        ephemeral.extendExpiry();
        long after = System.currentTimeMillis();

        assertThat(remembered.getExpiryTime())
                .isBetween(before + Auth.SESSION_TTL_REMEMBER_ME_MS, after + Auth.SESSION_TTL_REMEMBER_ME_MS);
        assertThat(ephemeral.getExpiryTime())
                .isBetween(before + Auth.SESSION_TTL_DEFAULT_MS, after + Auth.SESSION_TTL_DEFAULT_MS);
    }

    @Test
    void extendExpiryWithFlagFlipsTtlToOneDay() {
        LoginSession session = new LoginSession("alice", Role.USER, new String[]{"default"}, true);

        long before = System.currentTimeMillis();
        // 切到"不记住":续期后有效期应变更为 1 天 / switch to not-remembered: TTL should become 1 day
        session.extendExpiry(false);
        long after = System.currentTimeMillis();

        assertThat(session.isRememberMe()).isFalse();
        assertThat(session.getExpiryTime())
                .isBetween(before + Auth.SESSION_TTL_DEFAULT_MS, after + Auth.SESSION_TTL_DEFAULT_MS);
    }
}
