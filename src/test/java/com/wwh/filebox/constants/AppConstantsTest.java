package com.wwh.filebox.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AppConstants 行为测试
 * Behavioral tests for AppConstants
 */
class AppConstantsTest {

    @Test
    void cookieMaxAgeSecondsIsThirtyDaysWhenRememberMe() {
        // 勾选"记住我":cookie 与会话同为 30 天 / remember-me: cookie matches the 30-day session
        assertThat(AppConstants.Auth.cookieMaxAgeSeconds(true))
                .isEqualTo(30 * 24 * 60 * 60);
    }

    @Test
    void cookieMaxAgeSecondsIsOneDayWhenNotRememberMe() {
        // 不勾选:cookie 缩短为 1 天,与服务端 1 天会话一致 / not remembered: cookie shrinks to 1 day, matching the 1-day session
        assertThat(AppConstants.Auth.cookieMaxAgeSeconds(false))
                .isEqualTo(24 * 60 * 60);
    }
}
