# 原生打包 / Native packaging

把 File Box 打成**自带 JRE 的「解压即用」压缩包**,用户无需预装 Java、无需安装(installer)。
Build **self-contained, extract-and-run archives** — no Java pre-install, no installer.

## 产物 / Artifacts

| 平台 / Platform | 脚本 / Script | 产物 / Artifact | 运行 / Run |
|---|---|---|---|
| Linux | `build-native.sh` | `FileBox-<ver>-linux-jre.tar.gz` | `./start.sh` |
| Windows | `build-native.ps1` | `FileBox-<ver>-windows-jre.zip` | 双击 `FileBox.exe` |

两平台结构不同 —— **Linux 服务端走脚本式(透明、可接 systemd),Windows 桌面走 jpackage(双击 exe)**:
```
Linux: FileBox-<ver>-linux-jre/       Windows: FileBox-<ver>-windows-jre/
         file-box-<ver>.jar                      FileBox.exe          ← 双击运行
         jre/bin/java      ← 自带 JRE            runtime/             ← 自带 JRE
         start.sh / manage.sh                   app/file-box-<ver>.jar
         data/default/操作说明.html             README.txt
         README.txt                             file-box-data/       ← 首启生成(配置/数据/日志)
         # 首启在本目录生成 config/ data/ logs/ runtime/
```

**Linux 的 `start.sh` / `manage.sh` 自动探测 `jre/bin/java`**(有则用自带 JRE、无则退回系统 `java`)—— 所以**和基础包 release.tar.gz 共用同一套脚本**(`src/assembly/start.sh`)。数据落在包根,跟基础包布局一致;整个文件夹可随身拷贝,升级时替换 `file-box-<ver>.jar` + `jre/`、保留 `config/ data/ logs/`。

**Windows** 的数据由 launcher 的 `-Dfilebox.data.dir=$ROOTDIR/file-box-data` 写到 `file-box-data/`(程序文件夹内)。

## 构建 / Build

```bash
mvn package                          # 先出 fat jar / produce the fat jar first
./packaging/build-native.sh          # Linux(在 Linux 上跑)
# 或 / or  Windows: 在 Windows 上跑 packaging/build-native.ps1
```

输出在 `target/native/`。依赖 JDK 17+(`jlink` / `jpackage` / `jmods`)。

## 模块裁剪 / Runtime trimming

`jlink` 用一组保守模块裁出 ~57M 精简 JRE(脚本里的 `MODULES`)。两个 jdeps 检测不到、但运行时必需的:
`java.instrument`(Tomcat 类转换)、`jdk.crypto.ec`/`jdk.crypto.cryptoki`(TLS)。

## 备注 / Notes

- **不要安装包**:Linux 不出 deb/rpm、Windows 不出 msi —— 就要这种便携压缩包,工作目录随文件夹一起带。
- **Windows 脚本**(`build-native.ps1`)按 Linux 脚本类推;jpackage 不能交叉编译,Windows 包必须在 Windows 上构建。
- **图标 / Icon**:暂用 Java 默认图标;需要品牌 icon 后通过 `--icon` 注入。
