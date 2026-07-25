package com.wwh.filebox.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数据根目录解析器:把 config / data / logs / runtime 等相对路径锚定到一个可配置的数据根。
 * Data-home resolver: anchors the config / data / logs / runtime relative paths to a configurable base.
 *
 * <p>解析优先级:系统属性 {@code filebox.data.dir} → 环境变量 {@code FILEBOX_DATA_HOME} → 当前目录 {@code .}。
 * 默认回退到当前目录,与旧版本(相对 CWD)行为<b>完全一致</b>;只有显式指定数据根时才重定向
 * (例如原生启动器通过 {@code -Dfilebox.data.dir=...} 指向一个可写的安装目录)。</p>
 *
 * <p>Precedence: {@code filebox.data.dir} property → {@code FILEBOX_DATA_HOME} env → current dir {@code .}.
 * The default fallback to the current dir preserves the legacy (CWD-relative) behavior <b>exactly</b>;
 * paths are only relocated when a data home is explicitly given (e.g. the native launcher passes
 * {@code -Dfilebox.data.dir=...} pointing at a writable install directory).</p>
 */
public final class FileBoxPaths {

    public static final String DATA_HOME_PROP = "filebox.data.dir";
    public static final String DATA_HOME_ENV = "FILEBOX_DATA_HOME";

    private FileBoxPaths() {
    }

    /**
     * 数据根(绝对、规范化)。/ Data home (absolute, normalized).
     *
     * <p>每次调用都现读系统属性/环境变量(开销可忽略),以便测试与启动器能在运行时覆盖。
     * Read live on every call (negligible cost) so tests and the launcher can override at runtime.</p>
     */
    public static Path dataHome() {
        String explicit = System.getProperty(DATA_HOME_PROP);
        if (explicit == null || explicit.trim().isEmpty()) {
            explicit = System.getenv(DATA_HOME_ENV);
        }
        if (explicit == null || explicit.trim().isEmpty()) {
            explicit = ".";
        }
        return Paths.get(explicit.trim()).toAbsolutePath().normalize();
    }

    /**
     * 绝对路径原样返回(仅规范化);相对路径锚定到数据根。
     * Absolute paths are returned as-is (normalized only); relative paths anchor under the data home.
     * 用于 application.yml 里那些相对的日志/临时路径(它们原本相对 CWD,现在相对数据根)。
     */
    public static Path resolveRelOrAbs(String path) {
        if (path == null || path.trim().isEmpty()) {
            return dataHome();
        }
        Path p = Paths.get(path.trim());
        return p.isAbsolute() ? p.normalize() : dataHome().resolve(p).normalize();
    }

    public static Path configDir() {
        return dataHome().resolve("config");
    }

    public static Path configFile() {
        return configDir().resolve("filebox.yml");
    }

    public static Path dataDir() {
        return dataHome().resolve("data");
    }

    public static Path defaultStorageDir() {
        return dataDir().resolve("default");
    }

    public static Path logsDir() {
        return dataHome().resolve("logs");
    }

    public static Path runtimeDir() {
        return dataHome().resolve("runtime");
    }

    public static Path multipartTempDir() {
        return runtimeDir().resolve("multipart-tmp");
    }
}
