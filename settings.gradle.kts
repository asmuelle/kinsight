pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kinsight"

include(
    ":core:capture",
    ":core:pose",
    ":core:classify",
    ":core:verify",
    ":core:events",
    ":core:alerts",
    ":core:transport",
    ":core:pairing",
    ":core:design",
    ":watchdog",
)

// The app modules need the Android SDK. All safety-pipeline logic lives in
// JVM-only core modules so the product invariants stay buildable and testable
// on any machine (see TOOLS.md). CI (ubuntu-latest) has ANDROID_HOME set, so
// the apps build there; a laptop without the SDK still verifies the core.
val androidSdkFromEnv =
    sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .mapNotNull { System.getenv(it) }
        .map { file(it) }
        .firstOrNull { it.isDirectory }
val localProperties = file("local.properties")
val sdkDeclaredLocally =
    localProperties.exists() &&
        localProperties.readLines().any { it.trim().startsWith("sdk.dir=") }

if (androidSdkFromEnv != null || sdkDeclaredLocally) {
    include(":app-monitor")
    include(":app-companion")
} else {
    logger.lifecycle("Android SDK not found: building JVM core modules only (app modules excluded).")
}
