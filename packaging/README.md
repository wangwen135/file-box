# 原生打包 / Native packaging

把 File Box 打成**自带 JRE 的原生包**,用户无需预装 Java。
Build File Box into **self-contained native packages** — no Java pre-install needed on the target machine.

## 构建 / Build

```bash
mvn package                          # 先出 fat jar / produce the fat jar first
./packaging/build-native.sh          # 在 Linux 上构建 / build on Linux
```

产物在 `target/native/`:
- `FileBox/` — 便携 **app-image**(解压即用,始终生成)
- `filebox_<ver>_amd64.deb` — Debian 包(需 `dpkg-deb`)
- `filebox-<ver>.x86_64.rpm` — RPM 包(需 `rpmbuild`)

依赖 JDK 17+(`jlink` / `jpackage` / `jmods`)。Debian: `apt install openjdk-21-jdk-headless`。
Requires JDK 17+ (`jlink` / `jpackage` / `jmods`).

## 运行 / Run

```bash
# app-image(便携) / portable
FileBox/bin/FileBox [--server.port=8888]   # 数据写到同级 file-box-data/
```

`-Dfilebox.data.dir=$ROOTDIR/../file-box-data`(由 launcher 注入)把 config/data/logs/runtime
写到 app-image **同级**的 `file-box-data/` 目录 —— 升级时替换 `FileBox/`、保留 `file-box-data/` 即可。

For the app-image, `filebox.data.dir` is injected by the launcher to a sibling `file-box-data/`
folder — replace `FileBox/` to upgrade, keep `file-box-data/` for your data.

## 模块裁剪 / Runtime trimming

`jlink` 用一组保守模块裁出 ~57M 的精简 JRE(`build-native.sh` 里的 `MODULES`)。注意两个 jdeps 检测不到、
但运行时必需的模块:`java.instrument`(Tomcat 类转换)、`jdk.crypto.ec`/`jdk.crypto.cryptoki`(TLS)。

## 已知待办 / Known follow-ups(后续阶段 / later phases)

- **安装包的系统集成**:deb/rpm 装到 `/opt/filebox`,数据默认去 `/opt/filebox-data`(需 root)。
  生产级 deb/rpm 还需:专用服务用户、`/var/lib/filebox` 数据目录、systemd 单元、postinst 建目录设权限。
  app-image(便携)无此问题,是当前推荐的部署形态。
- **Windows `msi`**:`build-native.ps1` + CI 的 Windows runner。
- **图标 / Icon**:暂用 Java 默认图标;需要品牌 icon(`.png`/`.ico`)后通过 `--icon` 注入。
