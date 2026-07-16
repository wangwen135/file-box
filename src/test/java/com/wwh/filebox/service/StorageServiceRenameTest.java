package com.wwh.filebox.service;

import com.wwh.filebox.constants.AppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储空间改名的单元测试 / Unit tests for storage-space rename.
 * 覆盖 {@link StorageService#shouldRename(String, String)} 的判定分支与名称正则,
 * 不启动 Spring 上下文、不依赖文件系统。
 */
class StorageServiceRenameTest {

    @Test
    @DisplayName("同名 / 空 / null 不触发改名 / no-op when new name equals old or is blank")
    void noRenameWhenSameOrBlank() {
        assertThat(StorageService.shouldRename("default", "default")).isFalse();
        assertThat(StorageService.shouldRename("default", null)).isFalse();
        assertThat(StorageService.shouldRename("default", "")).isFalse();
        assertThat(StorageService.shouldRename("default", "   ")).isFalse();
    }

    @Test
    @DisplayName("不同名且非空触发改名（含中文、自动去空白）/ rename triggered when name actually changes")
    void renameWhenChanged() {
        assertThat(StorageService.shouldRename("default", "默认")).isTrue();
        assertThat(StorageService.shouldRename("default", "default2")).isTrue();
        // 前后空白经 trim 后与旧名不同,视为改名 / surrounding whitespace is trimmed first
        assertThat(StorageService.shouldRename("default", "  default2  ")).isTrue();
    }

    @Test
    @DisplayName("名称正则允许中文/字母/数字/下划线/连字符,拒绝空格与斜杠 / pattern accepts CJK, rejects space & slash")
    void namePatternAcceptsCjkRejectsSpaces() {
        assertThat("默认_01".matches(AppConstants.Storage.STORAGE_NAME_PATTERN)).isTrue();
        assertThat("aB3-_".matches(AppConstants.Storage.STORAGE_NAME_PATTERN)).isTrue();
        assertThat("bad name".matches(AppConstants.Storage.STORAGE_NAME_PATTERN)).isFalse();
        assertThat("a/b".matches(AppConstants.Storage.STORAGE_NAME_PATTERN)).isFalse();
    }
}
