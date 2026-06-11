# KinSight — donor-phone elder monitor (Android-first). See TOOLS.md for details.

# Prefer the locally provisioned Temurin 21 (gitignored .tooling/) when it
# exists: Gradle/Kotlin/detekt need JDK 17-24, and the system JDK may be newer.
_jdk21 := justfile_directory() / ".tooling/jdk-21.0.11+10/Contents/Home"

export JAVA_HOME := if path_exists(_jdk21) == "true" { _jdk21 } else { env("JAVA_HOME", "") }

# List available recipes
default:
    @just --list

# Print bootstrap state and local requirements (project is bootstrapped — M0 done)
bootstrap:
    @echo "KinSight is bootstrapped: Gradle wrapper + module skeleton are committed."
    @echo "Local requirements:"
    @echo "  - JDK 17-24 on JAVA_HOME (CI uses Temurin 21; .tooling/ JDK is used"
    @echo "    automatically when present)."
    @echo "  - Android SDK (ANDROID_HOME or local.properties sdk.dir) to include"
    @echo "    app-monitor + app-companion; without it, builds cover the JVM core."
    @echo "Modules: app-monitor, app-companion (Android, SDK-gated) +"
    @echo "  core/{capture,pose,classify,verify,events,alerts,transport,pairing,"
    @echo "  design} and watchdog (pure JVM, always built and tested)."
    @echo "Run 'just ci' for the full local gate."

# Assemble debug APKs for app-monitor + app-companion (JVM jars only without the SDK)
build: _require-gradlew
    @if { test -n "${ANDROID_HOME:-}" && test -d "${ANDROID_HOME:-/nonexistent}"; } || grep -qs '^sdk.dir=' local.properties; then \
        ./gradlew assembleDebug; \
    else \
        echo "notice: Android SDK not found — assembling JVM core modules only (apps excluded)."; \
        ./gradlew assemble; \
    fi

# Run JVM unit tests (JUnit5)
test: _require-gradlew
    ./gradlew test

# Static analysis: ktlint + detekt
lint: _require-gradlew
    ./gradlew ktlintCheck detekt

# Auto-format Kotlin sources with ktlint
format: _require-gradlew
    ./gradlew ktlintFormat

# Full gate: lint + build + test (what CI runs; must be green before commit)
ci: lint build test

# (internal) Fail with guidance if the Gradle wrapper is missing
_require-gradlew:
    @if [ ! -x ./gradlew ]; then \
        echo "error: ./gradlew not found — the Android project is not bootstrapped yet." >&2; \
        echo "hint: run 'just bootstrap' for instructions (milestone M0 in DESIGN.md)." >&2; \
        exit 1; \
    fi
