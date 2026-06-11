# TOOLS.md — Commands, Dependencies, Harness

## just recipes (single source of truth — agents use these, never raw gradle)

| Recipe | What it does | When to run |
|---|---|---|
| `just` (default) | Lists all recipes | Orientation |
| `just bootstrap` | Prints step-by-step guidance for generating the Gradle (KTS) multi-module project per DESIGN.md | Once, at M0, before any code exists |
| `just build` | `./gradlew assembleDebug` — assembles `app-monitor` + `app-companion` debug APKs | After changes, before manual device testing |
| `just test` | `./gradlew test` — JUnit5 + Robolectric JVM unit tests across all modules | Constantly (TDD); always before commit |
| `just lint` | `./gradlew ktlintCheck detekt` — style + static analysis | Before commit; CI runs it first |
| `just format` | `./gradlew ktlintFormat` — auto-fixes Kotlin style | After edits if the ktlint hook didn't catch it |
| `just ci` | `lint` + `build` + `test` in order | The pre-commit / pre-PR gate; what GitHub Actions runs |

Until the project is bootstrapped (no `./gradlew` yet), every gradle-backed recipe exits 1 with a message pointing at `just bootstrap`. This is expected for the scaffold commit.

## External models & services

| Dependency | What/where | Auth | Cost / limits |
|---|---|---|---|
| MediaPipe Pose Landmarker | `pose_landmarker_lite.task` / `_full.task` from Google's MediaPipe model page (storage.googleapis.com/mediapipe-models) | None | Free download; bundle lite variant (~5MB) in `core/pose` assets or fetch on first run |
| Gemma 3n E2B | `.litertlm` from Hugging Face (`google/gemma-3n-E2B-it-litert-lm` family) via MediaPipe LLM Inference API | `HF_TOKEN` (gated model — accept Gemma license on HF first) | Free; ~3GB download — fetch at setup time on ≥4GB-RAM devices only, never bundle in APK |
| Firebase Cloud Messaging (HTTP v1) | Alert + heartbeat push fan-out via the thin relay; per-app `google-services.json` (gitignored) | `FCM_SERVICE_ACCOUNT_JSON` (relay-side service account) | Free tier; payloads are E2E-encrypted ciphertext ≤4KB — FCM's 4KB limit is also our Invariant 1 budget |
| ML Kit GenAI Summarization | Gemini Nano via AICore on the **caregiver's** phone for daily digests | None (device-gated) | Free, on-device; deterministic template fallback on non-AICore devices |
| Google Play Billing | Subscriptions: $19.99/mo, $149/yr, $249/yr family, 30-day trial (M3) | Play Console config, no repo secret | 15% Google fee under $1M/yr |

No frontier-model API is called anywhere in the product runtime (see DESIGN.md cost-discipline table). No other third-party SaaS.

## Required env vars (names only — values live in CI secrets / local.properties, never in git)

| Variable | Purpose | Needed when |
|---|---|---|
| `HF_TOKEN` | Download gated Gemma 3n `.litertlm` during model-fetch tooling | M2 verifier work |
| `FCM_SERVICE_ACCOUNT_JSON` | Relay's Firebase service-account credentials for FCM HTTP v1 | M1 alert delivery onward (relay side) |
| `ANDROID_KEYSTORE_B64` | Base64 release keystore for signed builds | Release builds / Play track (M3) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password | Release builds (M3) |
| `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` | Release signing key alias + password | Release builds (M3) |

## Local services

None required — by design. There is no docker compose, no database server, no media backend; all state is on-device (Room). The only server-side component is the thin FCM push relay (stateless, ciphertext-only); during M1 development, use direct FCM test sends before the relay exists. A local relay stub (single small HTTP service) may be added at M2 — if so, document it here and add a `just relay` recipe.

Device tooling: `adb` for install/logcat/soak tests (allowed in `.claude/settings.json`). Physical test hardware should include one ≥4GB-RAM device (verifier path) and one sub-4GB device (deterministic fallback path).

## Android build notes (verified)

- `app-monitor` + `app-companion` are included only when an Android SDK is found (`ANDROID_HOME`/`ANDROID_SDK_ROOT` env or `local.properties` `sdk.dir`); without one, all gradle recipes cover the 10 JVM modules.
- **AGP 9 has built-in Kotlin support**: applying `org.jetbrains.kotlin.android` is an error. App modules apply only `com.android.application` + `org.jetbrains.kotlin.plugin.compose`.
- compileSdk/targetSdk 37 (required by the current Compose BOM / androidx line), minSdk 26.
- No-SDK machines can self-provision: create `.tooling/android-sdk/licenses/android-sdk-license` with the accepted-license hashes and run with `ANDROID_HOME=$PWD/.tooling/android-sdk` — AGP auto-downloads the platform/build-tools it needs (gitignored, ~no setup).

## CI overview (`.github/workflows/ci.yml`)

- Triggers: every `push` and `pull_request`.
- Runner: `ubuntu-latest`. Steps: checkout → setup-just → temurin JDK 21 (`actions/setup-java@v4`) → `gradle/actions/setup-gradle@v4` (caching) → **bootstrap guard** → `just ci`.
- Bootstrap guard: if `./gradlew` is missing, CI emits a notice ("scaffold-only commit; skipping build") and exits green — so this scaffold commit passes. Once M0 lands the wrapper, `just ci` (lint + build + test) runs on every push and is the merge gate.

## AI harness notes (`.claude/settings.json`)

- **Hooks:** PostToolUse on `Write|Edit` runs `ktlint -F` on any edited `.kt`/`.kts` file (silent no-op if ktlint isn't installed locally). Detekt is not hooked — it runs via `just lint`/`just ci`.
- **Permissions:** pre-allowed: `just`, `./gradlew`, `gradle`, `ktlint`, `adb`, and read-only `git status/diff/log`. Anything else prompts.
- **Most useful subagents here:**
  - `tdd-guide` — every new feature starts test-first; especially the invariant tests and classifier golden-sequence suite.
  - `code-reviewer` — immediately after any change; pay extra attention to the alert path (fail-open) and file/function size limits.
  - `security-reviewer` — mandatory for anything touching `core/transport`, `core/pairing`, Keystore usage, logging, or FCM payloads (this product's entire value proposition is a privacy claim — Invariants 1, 2, 7).
  - `planner` — before each milestone (M0-M3) to decompose DESIGN.md acceptance criteria into tasks.
