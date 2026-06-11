# KinSight — Design Doc

## Thesis

Adult children already pay $33-46/mo for medical-alert pendants their parents refuse to wear, and they reject cloud cameras over privacy — KinSight turns a $0 drawer phone into an all-local daytime activity monitor where only text alerts ever leave the house. The edge-AI architecture is the moat: no incumbent camera platform can claim "raw video never exists off this device" without a rebuild, and we can prove it with an in-app network ledger. We win by being honest about scope (daytime living-area monitoring + routine-deviation alerts; fall detection as best-effort wellness, not a Life Alert replacement) and by pricing against $32.95+/mo medical-alert services, never against $2.49/mo camera apps.

## Architecture

Two Android apps plus shared core modules. Pipeline shape: **capture → on-device inference → store → surface**. The donor phone does all vision work; the companion phone only ever receives small encrypted text events.

```
DONOR PHONE (monitor)                                        CAREGIVER PHONE (companion)
┌─────────────────────────────────────────────────┐
│ CameraX frames (never persisted)                │
│   └─> luma motion gate (deterministic, 24/7)    │
│        └─> Pose Landmarker 5-10fps (LiteRT)     │
│             └─> temporal classifier (LiteRT)    │          ┌──────────────────────────┐
│                  ├─ high conf ──────────┐       │  E2E-    │ full-screen alert        │
│                  └─ low/med conf        │       │  encrypted│ acknowledge / escalate  │
│                      └─> Gemma 3n ──────┤       │  text    │ daily digest (on-device  │
│                          (≥4GB, 3s,     │       │  ≤4KB    │   ML Kit GenAI)          │
│                           fail-open)    ▼       │ ───────> │ privacy ledger view      │
│ Room event store <── CandidateEvent → Alert     │  via FCM │ offline-monitor banner   │
│ local siren (works offline) ◄───────────┘       │  relay   └──────────────────────────┘
└─────────────────────────────────────────────────┘
        ▲ no media path exists anywhere to the right of the donor phone ▲
```

### Cost discipline (deterministic > cheap model > small LLM; frontier never in runtime)

| Tier | What runs | When | Cost |
|---|---|---|---|
| Deterministic | Luma frame-diff motion gate; immobility timers; routine-bucket heuristics | Every frame, 24/7 | ~free, keeps thermals sane |
| Cheap model | MediaPipe Pose Landmarker (LiteRT, GPU/NNAPI) at 5-10 fps; 1D-CNN/GRU temporal classifier over keypoint windows | Only while motion gate is open | Milliwatts, on-device |
| Small LLM | Gemma 3n E2B (.litertlm via MediaPipe LLM Inference API) verifies candidate anomaly frames | Only on low/medium-confidence candidates, only on ≥4GB-RAM devices, hard 3s timeout | On-device, zero API cost |
| Frontier model | Never in the product runtime. Dev-time only (synthetic test scenario generation, code review). | — | — |

The caregiver digest is generated **on the caregiver's own phone** (ML Kit GenAI summarization / Gemini Nano via AICore where available; deterministic template otherwise) from event metadata only.

### Module map (Gradle, KTS)

```
kinsight/
├── app-monitor/          # Donor-phone app: kiosk-style UI, foreground camera service
├── app-companion/        # Caregiver app: alerts, daily digest, privacy ledger, pairing
├── core/
│   ├── capture/          # CameraX + FOREGROUND_SERVICE_TYPE_CAMERA, luma motion gate,
│   │                     #   thermal governor (~1fps idle), charge-limit guidance
│   ├── pose/             # MediaPipe Pose Landmarker wrapper (LiteRT delegate selection)
│   ├── classify/         # Temporal fall/immobility classifier (LiteRT), confidence scoring
│   ├── verify/           # Gemma 3n verifier behind RAM gate; deterministic fallback path
│   ├── events/           # Room event store (metadata only, never frames), routine baselines
│   ├── alerts/           # Local siren, escalation state machine, FCM dispatch
│   ├── transport/        # E2E-encrypted event envelopes; whitelisted DTOs only; relay client
│   ├── pairing/          # QR pairing, X25519 key exchange between monitor + companion
│   └── design/           # Compose theme ("Hearthlight"), shared elder-legible components
└── watchdog/             # Boot receiver, heartbeat, auto-relaunch, offline detection
```

Dependency rule: `app-*` → `core/*`; core modules never depend on app modules; `transport` is the **only** module with network permission in its manifest, and it accepts only `AlertEvent`/`ActivitySummary`/`Heartbeat` DTOs (compile-time sealed).

### Infra

Thin push relay only: FCM HTTP v1 for alert fan-out, payloads are E2E-encrypted ciphertext under ~4KB. No media path exists anywhere in infra — there is nothing to subpoena, leak, or stream.

## Data model sketch

| Entity | Key fields | Notes |
|---|---|---|
| Household | id, name, plan (single/family), createdAt | Billing unit; owns devices + caregivers |
| MonitorDevice | id, householdId, room label, ramClass (verifier-eligible?), lastHeartbeatAt, thermalState | One per donor phone |
| Caregiver | id, householdId, fcmToken, publicKey, escalation rank | Companion-app user |
| PairingKey | monitorDeviceId, caregiverId, sharedSecret ref (Keystore alias) | Established via QR; never leaves Keystore |
| PoseWindow | ring-buffer of keypoint frames + timestamps | **In-memory only — never persisted** |
| CandidateEvent | id, deviceId, type (fall/immobility/routine-deviation), classifierScore, verifierVerdict?, verifierLatencyMs, occurredAt | Metadata only; no pixels, ever |
| Alert | id, candidateEventId, severity, deliveredAt, acknowledgedBy?, escalatedAt?, sirenFiredAt | Audit trail for trust + liability |
| ActivityBucket | deviceId, date, hourSlot, zone (kitchen/living/hall), motionMinutes, lastSeenAt | Feeds routine baseline |
| RoutineBaseline | deviceId, weekday, expected bucket profile, tolerance | Rolling 14-day learned profile |
| NetworkLedgerEntry | timestamp, endpoint, payloadBytes, payloadType | Powers the user-visible privacy proof |

## Key flows

### 1. Fall candidate → alert (the safety-critical path)

1. Motion gate (luma diff) opens; pose pipeline spins up to 5-10 fps.
2. Pose Landmarker streams keypoints into the in-memory `PoseWindow`.
3. Temporal classifier scores the window (rapid hip-height drop + horizontal trunk + immobility ≥30s).
4. **High confidence (≥ threshold): alert fires immediately — verifier is skipped.** Low/medium confidence on a ≥4GB device: Gemma 3n verifies the candidate frame in-memory with a 3s hard timeout; timeout/OOM/error ⇒ alert fires anyway (fail-open toward alerting).
5. Local siren fires first (works in airplane mode); `Alert` row written; E2E-encrypted text alert queued to FCM.
6. Companion app shows full-screen alert with acknowledge / call / escalate; unacknowledged after 5 min ⇒ next-ranked caregiver.

### 2. Routine deviation (the primary daytime claim)

1. Motion events accumulate into hourly `ActivityBucket`s per zone.
2. Nightly on-device job updates the 14-day `RoutineBaseline` per weekday.
3. Deterministic comparison flags deviations (e.g., no kitchen motion by 10:30 when baseline says 8:15 ± 45min ⇒ possible missed breakfast).
4. Deviation produces a low-severity `CandidateEvent` → companion notification (no siren).
5. Caregiver's phone composes the daily digest locally from event metadata.

### 3. Pairing & setup (trust starts here)

1. Adult child installs companion app, creates household, starts "Add a monitor."
2. Donor phone installs monitor app; shows QR with its public key + relay token.
3. Companion scans QR; X25519 exchange; shared secret lands in each device's Keystore.
4. Monitor app walks through wall-mount placement (living area, ~2m height), charge-limit setup, and Do-Not-Upgrade-Overnight guidance; runs a 60-second self-test (pose detected, siren audible, encrypted ping round-trip shown in the network ledger).

### 4. Uptime watchdog (donor phones are flaky)

1. Heartbeat every 5 min: monitor → relay → companion (encrypted, ~100 bytes).
2. Watchdog process re-launches the foreground service on crash; BOOT_COMPLETED receiver restarts after power loss.
3. Thermal governor drops capture to ~1 fps when hot; logs `thermalState` in heartbeat.
4. Companion surfaces "Monitor offline > 15 min" as a first-class alert — silence is itself a signal in elder care.

### 5. Trial → paid (M3)

1. 30-day free trial, full features, no card.
2. Paywall copy anchors exclusively against medical-alert pricing ("$19.99/mo vs. $32.95+ for a pendant they won't wear").
3. Play Billing: $19.99/mo, $149/yr, $249/yr family (2-4 monitors); annual is the default-highlighted option (gifting moment).

## Product & visual design direction: "Hearthlight"

Warm-paper calm, not surveillance-tech. Light-only theme (the buyers are 45-65; the elder-facing monitor screen must be readable at a glance from across a room): oat/cream surfaces (`oklch(96% 0.015 85)`), deep pine green as the single brand color (`oklch(38% 0.07 165)`), ember amber reserved **exclusively** for alert states (`oklch(70% 0.16 60)`) so color carries semantic weight. Typography: Fraunces (display serif — warmth, domesticity, trust) paired with Atkinson Hyperlegible (body — designed for low-vision readers; the demographic demands it). Imagery rule: **stick-figure pose silhouettes everywhere, camera/video imagery nowhere** — the privacy promise is the visual identity. The network ledger screen is a hero feature, styled like a paper receipt: every byte that left the house, itemized. Large touch targets (≥56dp), generous spacing, no dense dashboards.

## Milestones

### M0 — Bootstrap (`just ci` green)

- Generate the Gradle (KTS) multi-module skeleton per the module map; Kotlin 2.x, Compose, JUnit5 + Robolectric, ktlint + detekt wired.
- Acceptance: `just ci` passes locally and in GitHub Actions on a clean clone; empty-but-compiling modules; one placeholder test per app module.

### M1 — Thin vertical slice (one staged fall, end to end)

- `app-monitor`: foreground camera service + motion gate + Pose Landmarker + **heuristic** temporal classifier (hip-drop + horizontal + 30s immobility) → siren + FCM. No Gemma yet, no billing, single hardcoded pairing.
- `app-companion`: receives and renders the alert full-screen with acknowledge.
- Acceptance: staged fall on a mat in daylight triggers companion alert in <15s; siren fires in airplane mode; 12-hour overnight soak on a 2021-era 4GB Android with zero service deaths (watchdog restarts allowed, gaps <60s).

### M2 — Trust layer (the reason to pay)

- Network ledger UI backed by `NetworkLedgerEntry`; E2E encryption on all event payloads; relay sees ciphertext only.
- CI-enforced no-egress invariant tests (see AGENTS.md invariants 1-2); honest-copy lint over string resources.
- Gemma 3n verifier behind the ≥4GB RAM gate with the 3s fail-open timeout; trained LiteRT temporal classifier replaces the heuristic; routine-deviation flow live.
- Acceptance: packet capture during a 24h session shows only relay-bound ciphertext <4KB/event; invariant tests red-team-proof (a test that tries to send a Bitmap through `transport` fails to compile/run); false-positive rate <1 alert/day in a 3-household pilot.

### M3 — Monetization wiring

- Play Billing (trial, monthly, annual, family), multi-monitor pairing, escalation chains, daily digest, offline-monitor alerts, onboarding self-test.
- Acceptance: end-to-end purchase in internal testing track; family plan pairs 2+ monitors; digest renders on a non-AICore device via the deterministic template.

## Risks & mitigations (from adversarial review)

| # | Risk | Mitigation |
|---|---|---|
| 1 | Night/bathroom blindness: falls cluster at night and in bathrooms; RGB donor phones are blind in the dark and unacceptable in bathrooms | Scope every claim to daytime living-area monitoring; routine-deviation is the headline feature, fall detection is "best-effort wellness"; offer the $15 plug-in IR illuminator as an optional accessory at M3; honest-copy lint makes overclaiming a CI failure |
| 2 | "No camera at all" (Vayyar/Butlr radar) beats "camera that promises not to upload" in elder perception | Win on $0 hardware + visible proof: the network-ledger receipt is a demo-able artifact radar vendors don't have; Vayyar's ~£13K revenue on $296M raised shows the radar channel hasn't converted — our wedge is price and instant gratification |
| 3 | A ~3B VLM can't reliably tell "fallen on floor" from "lying on couch"; tuning to crush false positives suppresses true positives | Deterministic classifier is the decision-maker; Gemma may only suppress **low/medium-confidence** candidates; high-confidence falls always alert; verifier fail-open on timeout — encoded as Invariant 3 with tests |
| 4 | Donor-phone uptime: thermal throttling, swelling batteries at 100% duty cycle, OS kills, post-outage reboots only the elder can fix | watchdog module + BOOT_COMPLETED + heartbeats; thermal governor to ~1fps; OEM charge-limit setup in onboarding; "monitor offline >15 min" is a first-class companion alert so silent failure is impossible |
| 5 | Liability + EU AI Act: a missed fall in a product positioned as a Life Alert replacement is catastrophic; elder-safety AI plausibly high-risk classified regardless of on-device inference | Never market as a medical device or pendant replacement in-product; alerting-only wellness positioning with explicit disclaimers at onboarding; the `Alert` audit trail documents best-effort delivery; maintain an AI-Act risk assessment doc from M2; optional monitored-response tier (RapidSOS-style, +$9.99/mo) deferred until a partner carries response liability |
