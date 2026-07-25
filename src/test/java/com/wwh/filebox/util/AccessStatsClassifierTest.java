package com.wwh.filebox.util;

import com.wwh.filebox.model.AccessBucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessStatsClassifierTest {

    @Test
    void classifiesDynamicPaths() {
        assertThat(AccessStatsClassifier.classify("/list_files")).isEqualTo(AccessBucket.LIST);
        assertThat(AccessStatsClassifier.classify("/list_dir")).isEqualTo(AccessBucket.LIST);
        assertThat(AccessStatsClassifier.classify("/list_periods")).isEqualTo(AccessBucket.LIST);
        assertThat(AccessStatsClassifier.classify("/list_files?year=2026")).isEqualTo(AccessBucket.LIST);
        assertThat(AccessStatsClassifier.classify("/api/file?path=x")).isEqualTo(AccessBucket.DOWNLOAD);
        assertThat(AccessStatsClassifier.classify("/api/file")).isEqualTo(AccessBucket.DOWNLOAD);
        assertThat(AccessStatsClassifier.classify("/upload_file")).isEqualTo(AccessBucket.OTHER);
        assertThat(AccessStatsClassifier.classify("/api/auth/login")).isEqualTo(AccessBucket.OTHER);
        assertThat(AccessStatsClassifier.classify("/index.html")).isEqualTo(AccessBucket.OTHER);
        assertThat(AccessStatsClassifier.classify("/")).isEqualTo(AccessBucket.OTHER);
    }

    @Test
    void recognizesStaticAssets() {
        assertThat(AccessStatsClassifier.isStatic("/css/x.css")).isTrue();
        assertThat(AccessStatsClassifier.isStatic("/js/index.js")).isTrue();
        assertThat(AccessStatsClassifier.isStatic("/images/logo.png")).isTrue();
        assertThat(AccessStatsClassifier.isStatic("/lib/x.js")).isTrue();
        assertThat(AccessStatsClassifier.isStatic("/favicon.ico")).isTrue();
        assertThat(AccessStatsClassifier.isStatic("/css/x.css?v=1")).isTrue();
    }

    @Test
    void nonStaticIsNotStatic() {
        assertThat(AccessStatsClassifier.isStatic("/api/file")).isFalse();
        assertThat(AccessStatsClassifier.isStatic("/index.html")).isFalse();
        assertThat(AccessStatsClassifier.isStatic("/list_files")).isFalse();
        assertThat(AccessStatsClassifier.isStatic("/")).isFalse();
    }
}
