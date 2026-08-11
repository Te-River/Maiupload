# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

MaiProberPlus — Android app that intercepts WeChat game traffic via a local VPN, parses maimai DX / CHUNITHM score pages, and uploads them to third-party "probers" (Diving Fish 水鱼, LXNS 落雪) or a local Room database. A small Vue 3 web tool (`web/image_id_finder`) lives alongside it but is independent.

## Layout

- `android/` — the Android app (Gradle, Kotlin + Java). **All build/test/lint commands run from here.**
  - `app/src/main/java/io/github/teriver/maiupload/`
    - `vpn/` — **pure Java** VPN/TCP/DNS tunneling stack (LocalVpnService, TcpProxyServer, tunnels). Do not convert to Kotlin casually; it is self-contained and interop-sensitive.
    - `core/proxy/` — NanoHTTPD-based local HTTP server + `InterceptHandler` (the bridge called from Java VPN code into the Kotlin prober layer).
    - `core/prober/` — prober backends implementing `IProberUtil` (DivingFish, LXNS, Local), selected via the `ProberPlatform` enum.
    - `core/data/{maimai,chuni}/` — per-game enums, data models, and score managers (Room).
    - `core/database/` — Room DB (`AppDatabase`), entities, DAOs, migrations. KSP generates Room code.
    - `core/config/` — `ConfigStorage` serialized to `config.json` in app filesDir.
    - `ui/compose/` — Jetpack Compose UI, split per game (`scores/maimai`, `scores/chuni`) and feature (`sync`, `setting`, `bests`).
- `web/image_id_finder/` — Vue 3 + Vite + UnoCSS + Element Plus, pnpm-managed. Standalone.

## Build / Test / Lint (run inside `android/`)

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew assembleDebug` |
| Build snapshot APK (CI variant) | `./gradlew assembleSnapshot` |
| Unit tests | `./gradlew test` |
| Instrumentation tests | `./gradlew connectedAndroidTest` |
| Lint | `./gradlew lint` |
| Room schema dir | `app/schemas/` (configured in `app/build.gradle.kts` `room {}`) |

**落雪 OAuth client_id 已硬编码**：`app/build.gradle.kts` 直接写入 `LXNS_OAUTH_CLIENT_ID` BuildConfig 字段（PKCE 公共客户端，无需 client_secret），不再读取环境变量，本地/CI 构建均无需注入。

JDK 21 / AGP 8.7.2 / Kotlin 2.0.21 / compileSdk 34 / minSdk 26. Versions are centralized in `android/gradle/libs.versions.toml` — edit there, not in `build.gradle.kts`.

Web tool: `cd web/image_id_finder && pnpm install && pnpm dev` (or `pnpm build`).

## Conventions & gotchas

- **Mixed Kotlin/Java by design.** App code is Kotlin; the VPN stack under `vpn/` is Java. The Java VPN code calls back into Kotlin via `InterceptHandler.onAuthHook` (note the `@JvmStatic`). When touching the VPN↔prober boundary, keep that static bridge intact.
- **Three build types, not two.** `debug`, `release` (minified + shrunk), and `snapshot` (cloned from release, used by CI and the in-app updater). `androidCheck.yml` and `androidCI.yml` build `assembleSnapshot`.
- **Versioning is manual and dual-field.** `appVersion` and `appVersionCode` in `android/app/build.gradle.kts` are edited by hand; `versionCode` is derived by concatenating them and stripping dots. CI tags releases `v<appVersion>-<shortSha>`.
- **The `getCurrentAppVersion` Gradle task** writes `appVersion.txt` — CI reads it for the release tag. Don't remove it.
- **`GlobalViewModel` is a process-wide singleton `object`**, holding UI state (VPN running flag, hooking flags, selected platform/game). It is NOT a per-screen ViewModel; new screens should not store state here.
- **`Application` is a custom `Application` subclass** (registered in the manifest via `android:name=".Application"`), not the stock one. It owns `configManager`, `proberContext`, the Room `db`, and the notification channel.
- **Two external probers need tokens.** `divingfishToken` and `lxnsToken` live in `ConfigStorage`; the `LOCAL` prober needs no token. The `InterceptHandler` short-circuits to a UI message when the token is empty.
- **Assets under `app/src/main/assets/`** (maimai/chunithm rating/num/class/dan PNGs) are read at runtime via `Application.getAssetAsString`/`getImageFromAssets` — paths are game-specific and baked into the image generators.
- **Directory naming quirk:** the Room DAO package is spelled `inteface` (typo for `interface`). Keep the existing spelling when adding files there; renaming would ripple through imports.
- **Android CI only triggers on `android/app/**`, `android/gradle/**`, workflow files, root `*.gradle.kts`, and `gradle.properties`.** Changes outside these paths won't run CI.

## Commit & PR

- Target `master`. PR checks (`androidCheck.yml`) run `assembleSnapshot` on Ubuntu with JDK 21.
- Keep Commits conventional; write subject/body in Simplified Chinese, leave code identifiers untouched.
