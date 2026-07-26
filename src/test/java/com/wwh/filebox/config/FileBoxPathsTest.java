package com.wwh.filebox.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileBoxPaths 数据根目录解析测试(跨平台 / cross-platform:不在断言里写死 Unix 路径)。
 * Tests for the data-home resolver. Avoids hardcoded Unix paths so it passes on Windows too.
 */
class FileBoxPathsTest {

    @AfterEach
    void clearOverride() {
        // 仅清理系统属性;环境变量在进程启动时已固定,正常测试环境不会预设 FILEBOX_DATA_HOME
        System.clearProperty(FileBoxPaths.DATA_HOME_PROP);
    }

    @Test
    void dataHomeDefaultsToCurrentDirWhenUnset() {
        // 既未设属性也未设环境变量 → 退回当前目录(.),与旧行为一致
        System.clearProperty(FileBoxPaths.DATA_HOME_PROP);
        Path expected = Paths.get(".").toAbsolutePath().normalize();
        assertThat(FileBoxPaths.dataHome()).isEqualTo(expected);
    }

    @Test
    void dataHomeHonorsSystemProperty(@TempDir Path tmp) {
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, tmp.toString());
        assertThat(FileBoxPaths.dataHome()).isEqualTo(tmp.toAbsolutePath().normalize());
    }

    @Test
    void dataHomeNormalizesRelativeValue() {
        // 相对值也要解析成绝对、规范化路径 / a relative value still resolves to absolute + normalized
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, "foo/../bar");
        assertThat(FileBoxPaths.dataHome()).isEqualTo(Paths.get("bar").toAbsolutePath().normalize());
    }

    @Test
    void wellKnownDirsAnchorUnderDataHome(@TempDir Path tmp) {
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, tmp.toString());
        assertThat(FileBoxPaths.configDir()).isEqualTo(tmp.resolve("config"));
        assertThat(FileBoxPaths.configFile()).isEqualTo(tmp.resolve("config").resolve("filebox.yml"));
        assertThat(FileBoxPaths.defaultStorageDir()).isEqualTo(tmp.resolve("data").resolve("default"));
        assertThat(FileBoxPaths.logsDir()).isEqualTo(tmp.resolve("logs"));
        assertThat(FileBoxPaths.multipartTempDir()).isEqualTo(tmp.resolve("runtime").resolve("multipart-tmp"));
    }

    @Test
    void resolveRelOrAbsKeepsAbsoluteAsIs(@TempDir Path tmp) {
        // 用一个真·跨平台绝对路径(Linux 是 /tmp/...,Windows 是 C:\...)验证「绝对路径原样保留」
        // Use a genuinely cross-platform absolute path to verify "absolute stays as-is"
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, tmp.toString()); // 不影响:输入是绝对路径
        Path absolute = tmp.resolve("x.json");
        assertThat(FileBoxPaths.resolveRelOrAbs(absolute.toString())).isEqualTo(absolute);
    }

    @Test
    void resolveRelOrAbsAnchorsRelativeUnderDataHome(@TempDir Path tmp) {
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, tmp.toString());
        assertThat(FileBoxPaths.resolveRelOrAbs("logs/transfer.jsonl"))
                .isEqualTo(tmp.resolve("logs").resolve("transfer.jsonl"));
    }
}
