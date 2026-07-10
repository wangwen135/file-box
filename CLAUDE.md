# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

File Box is a self-hosted file sharing/management web app: Java 8 + Spring Boot 2.3, single fat-jar, **no database**. Files live directly on the filesystem; users, storage spaces, and system settings live in a hand-rolled YAML config. Default port **8888**.

## Build, run, test

```bash
mvn package                       # build jar + release tar.gz
mvn spring-boot:run               # run the app in dev (reads ./config/filebox.yml)
mvn test                          # run tests
mvn test -Dtest=FileCatalogServiceTest   # run a single test class
```

`mvn package` produces both `target/file-box-<version>.jar` and `target/file-box-<version>-release.tar.gz` (via the `maven-assembly-plugin` descriptor `src/assembly/assembly.xml`). The release tarball bundles `start.sh`/`start.bat`/`manage.sh`/`manage.bat` plus empty `data/`, `logs/`, `runtime/` dirs; it deliberately does **not** ship `config/filebox.yml` (that is generated on first run). Runtime heap is `-Xmx384m` (set in `start.sh`).

On first start, `ConfigValidationRunner` creates `./config`, `./data/default`, `./logs`, `./runtime/multipart-tmp`, and if `filebox.yml` is missing, generates a default config with a random admin password printed **once** to the log.

There is exactly one test class (`FileCatalogServiceTest`, JUnit 5 + AssertJ).

## The two-config split (most important concept)

There are two independent configuration systems — do not confuse them:

- **`src/main/resources/application.yml`** — Spring Boot config (port, multipart limits, multipart temp dir). Packaged inside the jar. Overridable at runtime via `config/application.yml` (git-ignored). Never put users/storage here.
- **`config/filebox.yml`** — **business config**: system name, anonymous-upload toggle, storage spaces, and users (passwords stored as BCrypt hashes). This is **not** loaded by Spring; it is read manually with SnakeYAML by `FileBoxConfigStore` and exposed via `ConfigService`. It **is** committed (dev sync) and contains real dev password hashes.

`ConfigService.getConfig()` checks the file's mtime on every call and reloads when it changes — so editing `config/filebox.yml` is picked up live, and `AdminController` writes persist through `ConfigService.saveConfig()` (atomic temp-file + move, with a `.bak` of the previous version). YAML keys are written kebab-case but both kebab-case and camelCase are accepted on read.

Config path resolution precedence (`FileBoxConfigStore.resolveConfigPath`): `--filebox.config=` CLI arg → `filebox.config` system property → `FILEBOX_CONFIG` env var → `./config/filebox.yml`.

## Maintenance commands run before Spring boots

`FileBoxApplication.main` checks for `--filebox.maintenance=...` **before** `SpringApplication.run`. The only command today is `reset-admin-password`, which loads the config store directly, rewrites the admin hash, prints a new password, and exits without starting the web server. Add new standalone admin operations by extending `MaintenanceCommand`.

## Authentication is custom, not Spring Security

There is **no Spring Security**. The `spring-security-crypto` dependency is used only for `BCryptPasswordEncoder`.

- `AuthInterceptor` (a `HandlerInterceptor`, registered in `WebConfig`) gates all requests. Tokens are read in order: `token` cookie → `Authorization: Bearer` header → `token` query param.
- Sessions are an **in-memory `ConcurrentHashMap`** (`AuthService.sessions`) — **all sessions are lost on restart**. Sessions extend their expiry on each access; "remember me" gives a 30-day window vs the default 24h.
- Roles: `ADMIN`, `MANAGER`, `USER` (`Role` enum), plus anonymous.
- **Subtlety:** `WebConfig` excludes `/admin/**` from the interceptor, so the static admin HTML pages are served without a server-side auth check — the actual security boundary for admin features is the `/api/admin/**` endpoints, which the interceptor *does* cover and enforces `ADMIN` on (`AuthInterceptor.ADMIN_PATHS`). Keep that asymmetry in mind when adding admin routes.

## Storage spaces are the multi-tenancy unit

A **storage space** = a named physical directory with a max size and an anonymous flag, defined in `filebox.yml`. Users are each granted a subset; **ADMIN users automatically get all spaces** (enforced at login in `AuthService.getStorageSpacesForUser`, and new spaces are auto-assigned to admins in `StorageService.createStorageSpace`).

Each session tracks a **current storage space** (`LoginSession.currentStorageSpace`), switchable via `POST /api/auth/switch-storage`. Nearly every file endpoint operates implicitly on the *current* space of the caller's session, validated through `FileBoxController.validateAndGetStorageSpace` (existence + per-user permission). Anonymous access is a separate login path (`/api/auth/anonymous-login`) that yields a `USER`-role session scoped to spaces with `allow-anonymous: true`.

## Files are the source of truth; the catalog is derived

There is no metadata DB. `FileCatalogService` walks the storage directory on demand and builds the view purely from filesystem attributes (size, mtime). Two listing paradigms coexist:

- `/list_files` (+ `/list_periods`) — legacy **time-based** view: files filtered by mtime year/month, sorted newest-first. The `/uploads/{year}/{month}/{filename}` URL is the legacy serving path.
- `/list_dir` + `/api/file?path=` — **directory** view that navigates the real folder tree and serves any file by storage-relative path.

Both are backed by a **per-storage-root walk cache** (45s TTL, `SCAN_CACHE_TTL_MS`) so pagination reuses one walk. There is a 200k-entry safety cap (`SCAN_CACHE_MAX_ENTRIES`) to protect the small heap. **Upload/delete calls must invalidate the cache** via `fileCatalogService.invalidateScanCache(...)` — every mutating endpoint already does this; new ones must too. Separately, `StorageService` caches usage stats (5min TTL, `STATS_CACHE_TTL_MS`).

## Security-critical patterns to replicate

When adding any endpoint that touches the filesystem, follow the existing contract:

- **Path traversal**: resolve the target against the storage root's normalized absolute path and assert `target.startsWith(basePath)`. Use `FileBoxController.resolveWithin` / `resolveServedPath` as the template.
- **Symlinks**: reject any path where a component below the storage root is a symbolic link (`containsSymbolicLink`). Symlinks are also skipped during walks and directory listings.
- **Errors**: write status + body directly (`writeError`). Do **not** use `response.sendError(...)` — `CustomErrorController` maps `/error` to `/index.html`, which turns errors into unintended 302 redirects.
- **Upload writes**: call `f.transferTo(absoluteFile)` (the `File` overload with an **absolute** path). `transferTo(Path)` always streams a copy; the `File` overload with an absolute path lets Tomcat do an atomic rename. This requires the multipart temp dir (`runtime/multipart-tmp`) to be on the **same filesystem** as the storage dir — see the comment in `application.yml` if a storage path ever moves to a separate mount.
- **Role gating**: `delete_file` requires `ADMIN` or `MANAGER`; `create_folder`/`delete_folder` require `ADMIN`. Regular `USER`s can upload but not delete.

## Conventions

- **Bilingual comments**: existing code comments are written in both Chinese and English (often inline pairs). Match this style for non-trivial comments.
- **Frontend**: vanilla HTML/JS/CSS under `src/main/resources/static/` (no build step). Admin pages live under `/admin/`; shared logic in `js/admin-common.js`, `js/theme.js`, `js/notification.js`. Chinese user-facing strings are sent directly from controllers.
- **Icons must be SVG, never emoji**: all UI icons are SVG `<symbol>`s in `src/main/resources/static/images/icons.svg` (Lucide set, `fill="none" stroke="currentColor" stroke-width="2"`), referenced via `<svg class="ico ..."><use href="/images/icons.svg#ico-..."/></svg>`. Do not paste emoji into HTML/JS as stand-ins for icons — emoji render inconsistently across OS/browser. Color icons through CSS classes/variables (`stroke="currentColor"` follows the host text color), not via the glyph.
- **Config-driven, not code-driven**: storage spaces and users are data in `filebox.yml`, not hardcoded. Prefer extending the config model (`SystemConfig` + `FileBoxConfigStore` toYaml/fromYaml) over adding constants when the value is per-deployment.

## Key file map

| Concern | Location |
|---|---|
| App entry / maintenance dispatch | `FileBoxApplication`, `MaintenanceCommand` |
| Business config load/save + bootstrap | `service/FileBoxConfigStore`, `service/ConfigService`, `config/ConfigValidationRunner` |
| Auth (interceptor, sessions, login) | `security/AuthInterceptor`, `service/AuthService`, `controller/AuthController` |
| File operations + security helpers | `controller/FileBoxController` |
| Admin API (users/storage/config) | `controller/AdminController` |
| Filesystem walk + cache | `service/FileCatalogService` |
| Storage CRUD + stats cache | `service/StorageService` |
| Tunable constants (limits, TTLs, patterns) | `constants/AppConstants` |
| Business config shape | `model/SystemConfig` |

`doc/` holds Chinese requirement/optimization notes; `references/pasteboard.py` is a reference impl the project drew inspiration from.
