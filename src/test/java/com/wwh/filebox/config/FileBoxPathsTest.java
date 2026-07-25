package com.wwh.filebox.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileBoxPaths 数据根目录解析测试
 * Tests for the data-home resolver.
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
    void dataHomeHonorsSystemProperty() {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir")).resolve("fbx-datahome-test");
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
    void wellKnownDirsAnchorUnderDataHome() {
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, "/tmp");
        assertThat(FileBoxPaths.configDir()).isEqualTo(Paths.get("/tmp/config"));
        assertThat(FileBoxPaths.configFile()).isEqualTo(Paths.get("/tmp/config/filebox.yml"));
        assertThat(FileBoxPaths.defaultStorageDir()).isEqualTo(Paths.get("/tmp/data/default"));
        assertThat(FileBoxPaths.logsDir()).isEqualTo(Paths.get("/tmp/logs"));
        assertThat(FileBoxPaths.multipartTempDir()).isEqualTo(Paths.get("/tmp/runtime/multipart-tmp"));
    }

    @Test
    void resolveRelOrAbsKeepsAbsoluteAsIs() {
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, "/tmp/datahome");
        // 用户在 yml 里填了绝对路径时应原样尊重 / an absolute path from yml is honored as-is
        assertThat(FileBoxPaths.resolveRelOrAbs("/var/log/x.json"))
                .isEqualTo(Paths.get("/var/log/x.json"));
    }

    @Test
    void resolveRelOrAbsAnchorsRelativeUnderDataHome() {
        System.setProperty(FileBoxPaths.DATA_HOME_PROP, "/tmp/datahome");
        assertThat(FileBoxPaths.resolveRelOrAbs("logs/transfer.jsonl"))
                .isEqualTo(Paths.get("/tmp/datahome/logs/transfer.jsonl"));
    }
}
