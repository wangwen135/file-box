# 配置说明

File Box 使用两类配置文件：业务配置和 Spring Boot 运行配置。它们的用途不同，不应混用。

| 配置文件 | 用途 |
| --- | --- |
| `config/filebox.yml` | 用户、存储空间、匿名访问和界面选项等业务配置 |
| `config/application.yml` | HTTP 端口、上传限制等 Spring Boot 运行参数 |

## 业务配置

### 配置路径

业务配置默认从当前工作目录下的 `./config/filebox.yml` 读取。可以使用以下方式覆盖，优先级从高到低依次为：

1. 命令行参数 `--filebox.config`
2. JVM 系统属性 `-Dfilebox.config`
3. 环境变量 `FILEBOX_CONFIG`
4. 默认路径 `./config/filebox.yml`

命令行参数示例：

```bash
java -jar file-box-<version>.jar --filebox.config=/path/to/filebox.yml
```

JVM 系统属性示例：

```bash
java -Dfilebox.config=/path/to/filebox.yml -jar file-box-<version>.jar
```

环境变量示例：

```bash
FILEBOX_CONFIG=/path/to/filebox.yml java -jar file-box-<version>.jar
```

在 Windows PowerShell 中可以使用：

```powershell
$env:FILEBOX_CONFIG = 'D:\file-box\config\filebox.yml'
java -jar file-box-<version>.jar
```

### 完整示例

```yaml
file-box:
  anonymous:
    enabled: false
  system:
    anonymous-upload-enabled: false
    share-notice-enabled: true
  storage-spaces:
    - name: default
      path: ./data/default
      max-size: 10GB
      allow-anonymous: false
  users:
    - username: admin
      password: <bcrypt-password-hash>
      role: ADMIN
      storage-spaces:
        - default
```

### 匿名访问和系统选项

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `file-box.anonymous.enabled` | Boolean | `false` | 是否允许匿名上传。为兼容旧配置而保留 |
| `file-box.system.anonymous-upload-enabled` | Boolean | `false` | 是否允许匿名上传；若同时配置，它会覆盖 `anonymous.enabled` |
| `file-box.system.share-notice-enabled` | Boolean | `true` | 是否在文件页面显示当前访问地址提示 |
| `file-box.system.allowed-origins` | String | 未设置 | 预留的来源配置字段；当前版本尚未用它执行来源校验 |

启用匿名上传还不够，目标存储空间的 `allow-anonymous` 也必须为 `true`。

### 存储空间

`file-box.storage-spaces` 是存储空间列表。每个存储空间支持以下字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | String | 存储空间的唯一名称，也用于用户授权引用 |
| `path` | String | 文件保存目录；相对路径以进程当前工作目录为基准 |
| `max-size` | String | 空间容量上限，支持 `KB`、`MB`、`GB` 或字节数，例如 `10GB` |
| `allow-anonymous` | Boolean | 是否允许匿名用户使用该空间，默认为 `false` |

用户的 `storage-spaces` 中引用的名称必须与这里的 `name` 一致。运行 File Box 的系统账号还必须拥有对应目录的读写权限。

### 用户和角色

`file-box.users` 是用户列表。每个用户支持以下字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `username` | String | 登录用户名 |
| `password` | String | BCrypt 密码哈希，不能填写明文密码 |
| `role` | String | 用户角色：`ADMIN`、`MANAGER` 或 `USER` |
| `storage-spaces` | String 列表 | 该用户可以访问的存储空间名称 |

角色名称不区分大小写。无法识别或未填写的角色会按 `USER` 处理。

建议通过管理后台维护用户和密码。管理员密码遗失时，可运行 `manage.sh` 或 `manage.bat`，选择 **Reset admin password**。也可以直接执行：

```bash
java -jar file-box-<version>.jar --filebox.maintenance=reset-admin-password
```

重置命令会写入新的 BCrypt 哈希，并只在控制台输出一次新的明文密码。

### 首次启动

如果业务配置文件不存在，File Box 会自动完成以下初始化：

- 创建 `config/filebox.yml`。
- 创建默认存储目录 `data/default/`。
- 创建 `admin` 用户并生成随机密码。
- 将密码以 BCrypt 哈希形式写入配置文件。
- 仅在首次启动的控制台或日志中输出一次明文初始密码。

首次登录后应立即修改管理员密码，并妥善保存首次启动日志。

## Spring Boot 运行配置

应用包内的 `application.yml` 提供默认值。发布包中的 `config/application.yml` 可用于外部覆盖；业务用户、密码和存储空间不能写在这里。

### HTTP 端口

```yaml
server:
  port: 8888
```

修改端口后需要重启 File Box。

### 上传限制和临时目录

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB
      location: ./runtime/multipart-tmp
```

| 字段 | 说明 |
| --- | --- |
| `max-file-size` | 单个上传文件的最大大小 |
| `max-request-size` | 单次 multipart 请求的最大大小，通常不应小于 `max-file-size` |
| `location` | 上传过程使用的临时目录；相对路径以进程当前工作目录为基准 |

为使大文件落盘时能够使用文件系统重命名，而不是跨设备复制，multipart 临时目录应与目标存储目录位于同一个文件系统。如果存储目录迁移到独立挂载点，也应将 `location` 调整到该挂载点下。

## 修改配置的注意事项

- 修改配置前先备份 `config/filebox.yml`。
- 不要在配置文件中保存明文密码，也不要提交真实密码哈希或生产环境路径。
- 确保存储目录和 multipart 临时目录存在，并且运行账号具有读写权限。
- YAML 使用空格缩进，不要使用制表符；存储空间名称和用户授权引用必须保持一致。
- 通过管理后台保存业务配置时，程序会在同目录生成 `filebox.yml.bak` 备份。
- 外部修改业务配置后，程序会在后续读取时检测文件更新时间并重新加载；生产环境中仍建议在维护窗口修改并检查日志。
- 修改 `config/application.yml` 后需要重启应用才能可靠生效。
