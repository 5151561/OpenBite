---
inclusion: always
---

# Tech stack and build rules

## Versions (do not change without asking)

| Item | Version |
| --- | --- |
| Gradle | 9.8.2, the user's existing install. Never download another Gradle distribution. |
| JDK | 21 (Gradle toolchain) |
| AGP | 9.5.0 (built-in Kotlin; no `kotlin-android` plugin) |
| Kotlin / KSP | 2.4.21 / 2.3.13 |
| SDK | minSdk 26, targetSdk 36, compileSdk 37 (required by AppFunctions) |
| Database | Room 2.8.5 KMP |
| DI | Koin 4.2.2 |
| UI | Compose BOM 2026.06.01 + Material 3, Navigation 3 |
| Network | Ktor 3.6.1 |
| Map | MapLibre Native 13.7.1 + OpenFreeMap "liberty" style (no key) |
| Assistant | `androidx.appfunctions` 1.0.0-rc01 |

All versions live in `gradle/libs.versions.toml`. Add new libraries there, never inline.

## Architecture

- Kotlin Multiplatform shared core, native UI per platform (Google's recommended KMP shape).
- Dependency direction: `feature` -> `domain` -> `data` -> `database` / `network` / `model`.
- `:shared:feature:*` may depend only on `:shared:core:domain` (and `model`). The convention plugin `KmpLibraryConventionPlugin` fails the build on any other edge. Do not work around it; add a thin use case instead.
- Features never depend on each other. Navigation lives in `:androidApp:app`.
- Shared ViewModels and their Koin definitions live in `:shared:feature:*/commonMain`. `androidApp` only references the modules.
- Platform capabilities are interfaces in `shared/core/data/.../platform/PlatformServices.kt`, implemented in `:androidApp:app` and injected with Koin.
- `openbite.ios=false` in `gradle.properties` keeps iOS targets off. Shared code must still compile for iOS when it is set to `true`: no `java.*` in `commonMain`.

## Coroutines

- Never swallow `CancellationException`. Use `try/catch` and rethrow it; do not use `runCatching` around suspend calls.
- Heavy work (zip, image, JSON) runs on the injected `AppDispatchers`, not hard-coded `Dispatchers`.
- Cleanup that must finish after a screen closes runs in the application scope with `NonCancellable`.

## AI providers

- **Never hardcode model names or default model lists.** Fetch models from the provider's `/models` endpoint. Frontier models are retired within months.
- Two wire formats only: Anthropic Messages and OpenAI Chat Completions (Gemini, DeepSeek and custom endpoints use the OpenAI-compatible one).
- A model is usable for vision only after it answers a 64x64 red PNG probe with "red". Remember the result.
- An SSE stream without `message_stop`, or ending with `stop_reason=max_tokens`, is a failure. Never save a partial menu.
- Do not send `max_tokens` on OpenAI-compatible requests.

## Commands

```bash
./gradlew :androidApp:app:assembleDebug
./gradlew :androidApp:app:installDebug
./gradlew test                              # all JVM unit tests
./gradlew :androidApp:app:assembleRelease   # R8, arm64-v8a only
```

The host has limited memory. Prefer running the tests of the modules you touched (for example `./gradlew :shared:core:domain:testAndroidHostTest`) before the full suite.

## Done means

Each step ends with `assembleDebug` passing, the relevant tests passing, and one commit whose message starts with the step id (`U3a:`, `F6:`, `R2:` ...).
