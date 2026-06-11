# AGENTS.md — Operating Manual for KinSight

## Project snapshot

- **What:** KinSight turns a spare ("donor") Android phone into an all-local fall and anomaly monitor for aging parents. Two apps: a donor-phone **monitor** (CameraX + MediaPipe Pose via LiteRT, temporal fall classifier, Gemma 3n verification on ≥4GB devices) and a family **companion** app.
- **Privacy architecture:** only encrypted text alerts ever leave the device; raw video never persists anywhere — this is the entire value proposition, enforced as invariants below.
- **Who pays:** adult children (45-65) already paying $33-46/mo for medical-alert pendants their parents won't wear. Pricing: $19.99/mo / $149/yr / $249/yr family, 30-day trial.
- **Status:** pipeline **runner-up** — viable with caveats. The adversarial review in README.md is binding context, not background noise.
- **Honest scope (non-negotiable):** daytime living-area monitoring + routine-deviation alerts is the product claim. Fall detection is a best-effort wellness feature. No IR means no night coverage; no bathroom coverage, ever. Android monitor first; iOS monitor is secondary and out of scope until Android ships.

## Read first

1. `README.md` — research dossier: market evidence, comparables, adversarial review, recommended stack. Do not contradict it.
2. `DESIGN.md` — architecture, module map, data model, key flows, milestones (M0-M3), risks.
3. `TOOLS.md` — every command, external dependency, env var, and CI behavior.

## Commands

`just` is the single source of truth. Never run raw `./gradlew` directly; if a recipe is missing, add it to the justfile.

| Recipe | What it does |
|---|---|
| `just` | List all recipes |
| `just bootstrap` | Print guidance for generating the Gradle project (M0) |
| `just build` | `./gradlew assembleDebug` — both apps + core modules |
| `just test` | `./gradlew test` — JUnit5 + Robolectric JVM tests |
| `just lint` | `./gradlew ktlintCheck detekt` |
| `just format` | `./gradlew ktlintFormat` |
| `just ci` | lint + build + test — must be green before any commit |

All gradle-backed recipes fail with a helpful message until the project is bootstrapped (M0).

## Architecture summary

Capture → on-device inference → store → surface. A foreground camera service on the donor phone runs a free luma motion gate 24/7; when motion opens the gate, MediaPipe Pose Landmarker (LiteRT) streams keypoints at 5-10 fps into an in-memory window scored by a temporal fall/immobility classifier; low/medium-confidence candidates may be verified by Gemma 3n (≥4GB RAM only, 3s fail-open timeout); confirmed events become encrypted text alerts via local siren + FCM to the companion app. Modules: `app-monitor`, `app-companion`, `core/{capture, pose, classify, verify, events, alerts, transport, pairing, design}`, `watchdog`. Dependency rule: apps depend on core; core never depends on apps; **`core/transport` is the only module allowed network access** and only for sealed text DTOs. See DESIGN.md for the full map and data model.

## Coding standards

- Kotlin 2.x, Gradle KTS, Jetpack Compose. ktlint + detekt enforced (`just lint`).
- Files <800 lines (target 200-400); functions <50 lines; no nesting >4 levels — prefer early returns.
- Immutability by default: `val`, `data class .copy()`, immutable collections. No shared mutable state outside well-named state holders (StateFlow).
- Explicit error handling at every boundary: camera callbacks, LiteRT inference, FCM dispatch, Room I/O. Never swallow exceptions on the alert path — fail open toward alerting (Invariant 3).
- No hardcoded secrets. Config via env vars / local.properties (gitignored); see TOOLS.md env table.
- Conventional commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`.
- Naming: PascalCase types/composables, camelCase functions, `is/has/should/can` booleans, UPPER_SNAKE_CASE constants. No magic numbers — thresholds like immobility seconds and verifier timeout are named constants in one place per module.

## Testing policy

- TDD: write the failing test first (RED → GREEN → REFACTOR). Target 80%+ coverage on `core/*`.
- AAA pattern (Arrange-Act-Assert) with behavior-describing names: `test("high-confidence fall alerts even when verifier times out")`.
- What matters most for THIS product, in order:
  1. **Invariant tests** (below) — these are the product; they run in CI forever.
  2. **Classifier/pipeline tests** — golden keypoint sequences (recorded falls, couch-lying, kneeling, pet motion) replayed through `core/classify` asserting score + decision; regression suite grows with every false positive/negative found in pilots.
  3. **Alert-path tests** — escalation state machine, airplane-mode siren, FCM queue/retry, watchdog restart (Robolectric).
  4. **Companion UI tests** — alert rendering, acknowledge flow, offline-monitor banner.
- Fix the implementation, not the test, unless the test is provably wrong.

## PRODUCT INVARIANTS (non-negotiable; each must have a CI test)

1. **No media egress.**
   - No code path may pass a `Bitmap`, `Image`, `ImageProxy`, frame buffer, or any pixel-derived blob to a network API.
   - `core/transport` accepts only the sealed DTOs `AlertEvent`, `ActivitySummary`, `Heartbeat`, each serialized ≤4KB.
   - Test: compile-time sealed interface + a detekt rule/unit test that rejects any new DTO carrying byte arrays >4KB or image types.
2. **No frame persistence.**
   - Camera frames and pose keypoints live only in in-memory ring buffers.
   - Nothing image-like is written to disk — including debug dumps, logs, and crash-report attachments.
   - Test: filesystem assertion after a simulated monitor session (Robolectric) finds zero image/serialized-frame files.
3. **Deterministic before LLM, fail-open toward alerting.**
   - The temporal classifier is the decision-maker. Gemma 3n may only suppress **low/medium-confidence** candidates.
   - A high-confidence fall alerts regardless of verifier verdict, timeout (3s hard), OOM, or crash.
   - Test: verifier stubbed to hang/throw/return "not a fall" — high-confidence alert still fires within budget.
4. **Verifier RAM gate.**
   - Gemma 3n loads only on devices reporting ≥4GB RAM, decided once at service start; sub-4GB devices use the deterministic heuristic path.
   - Test: capability gate unit tests for both device classes; no lazy mid-session model load.
5. **Local-first alerting.**
   - The siren fires before any network call and must work in airplane mode; network alerts queue and retry.
   - Test: alert path with connectivity mocked off — siren fires, alert persists in the outbound queue.
6. **Honest copy.**
   - No string resource, store listing, or notification may claim night coverage, bathroom coverage, medical-device status, pendant replacement, or guaranteed fall detection.
   - Canon phrases: "daytime living-area monitoring", "best-effort wellness alerts".
   - Test: honest-copy lint over `strings.xml` with a banned-phrase list (`night`, `bathroom`, `medical device`, `never miss`, `guaranteed`, ...).
7. **E2E-encrypted metadata, zero plaintext PII.**
   - Event payloads are encrypted with pairing keys (Android Keystore); the relay sees ciphertext only.
   - Logs, analytics, and crash reports contain no names, rooms, or health inferences.
   - Test: transport round-trip asserts ciphertext on the wire; log-scrubber unit tests.
8. **Silence is a signal.**
   - Heartbeat every 5 min; the companion app must raise "monitor offline >15 min" as a first-class alert.
   - Test: heartbeat-gap simulation triggers the offline alert.

## Definition of done

- [ ] `just ci` green (lint + build + test)
- [ ] New behavior covered by tests written first; coverage ≥80% on touched core modules
- [ ] All 8 product invariants still pass; new code on the alert path fails open toward alerting
- [ ] No file >800 lines, no function >50 lines, no new magic numbers
- [ ] No secrets, no plaintext PII in logs, no new network access outside `core/transport`
- [ ] User-facing strings pass honest-copy lint; scope claims match DESIGN.md
- [ ] Conventional commit message; DESIGN.md/TOOLS.md updated if architecture or commands changed
