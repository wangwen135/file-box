package com.wwh.filebox.security;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    @Test
    void alphanumericHasRequestedLengthAndOnlyAlphanumeric() {
        String pwd = SecureTokenGenerator.generateAlphanumeric(22);
        assertThat(pwd).hasSize(22);
        assertThat(pwd).matches("^[A-Za-z0-9]+$");
    }

    @RepeatedTest(20)
    void alphanumericNeverContainsSymbols() {
        // 反复抽样,确保不出现 Base64-URL 的 - 或 _ / sampled repeatedly, no - or _
        String pwd = SecureTokenGenerator.generateAlphanumeric(22);
        assertThat(pwd).doesNotContain("-", "_", "+", "/", "=");
    }
}
