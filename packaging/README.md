# 原生打包 / Native packaging

把 File Box 打成**自带 JRE 的「解压即用」压缩包**,用户无需预装 Java、无需安装(installer)。
Build **self-contained, extract-and-run archives** — no Java pre-install, no installer.

## 产物 / Artifacts

| 平台 / Platform | 脚本 / Script | 产物 / Artifact | 运行 / Run |
|---|---|---|---|
| Linux | `build-native.sh` | `FileBox-<ver>-linux-jre.tar.gz` | `./bin/FileBox` |
| Windows | `build-native.ps1` | `FileBox-<ver>-windows-jre.zip` | 双击 `FileBox.exe` |

解压后得到一个文件夹(Linux 例:`FileBox-<ver>-linux-jre/`),启动器直接在根目录、无多余层级:
```
FileBox-<ver>-linux-jre/
  bin/FileBox       ← 启动器(Windows 对应 FileBox.exe,直接在根目录)
  lib/              ← 自带运行时(runtime)+ fat jar(app)
  README.txt        ← 运行说明
  file-box-data/    ← 首次运行时生成:config / data / logs / runtime(在启动器旁边)
```

## 构建 / Build

```bash
mvn package                          # 先出 fat jar / produce the fat jar first
./packaging/build-native.sh          # Linux(在 Linux 上跑)
# 或 / or  Windows: 在 Windows 上跑 packaging/build-native.ps1
```

输出在 `target/native/`。依赖 JDK 17+(`jlink` / `jpackage` / `jmods`)。

## 数据目录 / Data dir

launcher 注入 `-Dfilebox.data.dir=$ROOTDIR/file-box-data`,把 config/data/logs/runtime 写到
**程序文件夹内**(启动器旁边)的 `file-box-data/`。整个文件夹可随身拷贝;
升级时把新版解压覆盖到同一文件夹、保留 `file-box-data/` 即可。

## 模块裁剪 / Runtime trimming

`jlink` 用一组保守模块裁出 ~57M 精简 JRE(脚本里的 `MODULES`)。两个 jdeps 检测不到、但运行时必需的:
`java.instrument`(Tomcat 类转换)、`jdk.crypto.ec`/`jdk.crypto.cryptoki`(TLS)。

## 备注 / Notes

- **不要安装包**:Linux 不出 deb/rpm、Windows 不出 msi —— 就要这种便携压缩包,工作目录随文件夹一起带。
- **Windows 脚本**(`build-native.ps1`)按 Linux 脚本类推;jpackage 不能交叉编译,Windows 包必须在 Windows 上构建。
- **图标 / Icon**:暂用 Java 默认图标;需要品牌 icon 后通过 `--icon` 注入。
