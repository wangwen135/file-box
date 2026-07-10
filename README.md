# File Box

File Box is a lightweight local file sharing and management tool built with Java 8 and Spring Boot.

## Build

```bash
mvn package
```

The release archive is generated at:

```text
target/file-box-<version>-release.tar.gz
```

## Release Layout

After extracting the release archive:

```text
file-box-<version>/
  file-box-<version>.jar
  start.sh
  start.bat
  manage.sh
  manage.bat
  data/
    default/
  logs/
  runtime/
    multipart-tmp/
```

## Configuration

File Box uses two different configuration files:

- `src/main/resources/application.yml`: packaged inside the jar; contains Spring Boot defaults such as `server.port` and multipart upload limits.
- `config/filebox.yml`: external runtime business configuration; contains users, password hashes, system settings, and storage spaces.

Optional external Spring Boot overrides can be placed in:

```text
config/application.yml
```

This file can be used to change settings such as the HTTP port:

```yaml
server:
  port: 8888
```

Do not put users or storage spaces in `config/application.yml`. Those belong in `config/filebox.yml`.

## First Start

Run the platform startup script:

```bash
./start.sh
```

or on Windows:

```bat
start.bat
```

On first startup, File Box creates:

```text
config/filebox.yml
data/default/
runtime/multipart-tmp/
logs/
```

It also creates the `admin` user with a random password. The plaintext password is printed to the console/log only once. The config file stores only the BCrypt hash.

## Reset Admin Password

Use the management script:

```bash
./manage.sh
```

or on Windows:

```bat
manage.bat
```

Choose `Reset admin password`. The script calls the jar maintenance command:

```bash
java -jar file-box-<version>.jar --filebox.maintenance=reset-admin-password
```

The command updates `config/filebox.yml`, prints the new admin password, and exits without starting the web server.

## Development Runtime Files

The development workspace uses the same layout as the release package:

```text
config/filebox.yml
config/application.yml
data/
logs/
runtime/
```

`config/filebox.yml` is committed so the business configuration can be shared across development machines. It is intentionally not included in the release archive.

`config/application.yml` is a local Spring Boot override and remains ignored by git.

## Config Path Override

By default, business config is read from:

```text
./config/filebox.yml
```

Override it with either:

```bash
java -jar file-box-<version>.jar --filebox.config=/path/to/filebox.yml
```

or:

```bash
FILEBOX_CONFIG=/path/to/filebox.yml java -jar file-box-<version>.jar
```

## License

This project is licensed under the Apache License, Version 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
