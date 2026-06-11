package app.kinsight.core.transport

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Repo-level product invariants (AGENTS.md) enforced as tests so they gate
 * every `just ci` run forever:
 * - Invariant 1/AGENTS dependency rule: core:transport is the ONLY module
 *   that may touch network APIs or declare the INTERNET permission.
 * - Invariant 2: no file-write APIs anywhere near the frame/pose pipeline.
 * - Invariant 6: honest copy — no shipped string may overclaim coverage.
 * - Cost discipline: no cloud-LLM SDK in any dependency declaration.
 */
class RepoInvariantsTest {
    private val repoRoot: Path = findRepoRoot()
    private val excludedDirNames = setOf("build", ".gradle", ".tooling", ".git", ".idea", ".kotlin")
    private val selfName = "RepoInvariantsTest.kt"

    private fun findRepoRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("settings.gradle.kts not found above ${Paths.get("")}")
        }
        return current
    }

    private fun walk(
        under: Path,
        extensions: Set<String>,
    ): List<Path> =
        Files.walk(under).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { path -> path.extension in extensions }
                .filter { path -> path.none { part -> part.name in excludedDirNames } }
                .toList()
        }

    @Test
    fun `only the transport module may reference network APIs`() {
        // Arrange
        val bannedNetworkTokens = listOf("java.net.", "okhttp", "httpurlconnection", "retrofit2", "socket(")
        val guardedModules = listOf("core", "watchdog", "app-monitor", "app-companion")
        val sources =
            guardedModules
                .map(repoRoot::resolve)
                .filter(Files::isDirectory)
                .flatMap { walk(it, setOf("kt")) }
                .filterNot { it.name == selfName }
                .filterNot { it.startsWith(repoRoot.resolve("core/transport")) }

        // Act / Assert
        sources.forEach { file ->
            val content = file.readText().lowercase()
            bannedNetworkTokens.forEach { token ->
                assertTrue(!content.contains(token)) {
                    "Dependency rule violation: network token '$token' outside core:transport in $file"
                }
            }
        }
    }

    @Test
    fun `no manifest outside transport declares the internet permission`() {
        // Arrange
        val manifests =
            walk(repoRoot, setOf("xml"))
                .filter { it.name == "AndroidManifest.xml" }
                .filterNot { it.startsWith(repoRoot.resolve("core/transport")) }

        // Act / Assert — match real <uses-permission> declarations, not comments
        // documenting the invariant (which legitimately name the permission).
        val internetDeclaration = Regex("""<uses-permission[^>]*android\.permission\.INTERNET""")
        manifests.forEach { manifest ->
            assertTrue(!internetDeclaration.containsMatchIn(manifest.readText())) {
                "Invariant 1 violation: INTERNET permission outside core:transport in $manifest"
            }
        }
    }

    @Test
    fun `frame pipeline modules contain no file-write APIs`() {
        // Arrange — Invariant 2: nothing image-like can even reach disk code
        val bannedIoTokens =
            listOf("java.io.file", "fileoutputstream", "filewriter", "openfileoutput", "files.write")
        val pipelineModules = listOf("core/capture", "core/pose", "core/classify")
        val sources =
            pipelineModules
                .map(repoRoot::resolve)
                .flatMap { walk(it.resolve("src/main"), setOf("kt")) }

        // Act / Assert
        assertTrue(sources.isNotEmpty()) { "pipeline sources missing — wrong repo root?" }
        sources.forEach { file ->
            val content = file.readText().lowercase()
            bannedIoTokens.forEach { token ->
                assertTrue(!content.contains(token)) {
                    "Invariant 2 violation: file IO '$token' in frame pipeline at $file"
                }
            }
        }
    }

    @Test
    fun `shipped string resources never overclaim coverage`() {
        // Arrange — Invariant 6 banned-phrase list (AGENTS.md)
        val bannedPhrases =
            listOf(
                "night",
                "bathroom",
                "medical device",
                "medical-grade",
                "never miss",
                "guaranteed",
                "pendant replacement",
                "life alert",
            )
        val stringResources =
            walk(repoRoot, setOf("xml")).filter { path ->
                path.name.startsWith("strings") && path.any { it.name == "res" }
            }

        // Act / Assert
        stringResources.forEach { file ->
            val content = file.readText().lowercase()
            bannedPhrases.forEach { phrase ->
                assertTrue(!content.contains(phrase)) {
                    "Invariant 6 violation: banned phrase '$phrase' in $file"
                }
            }
        }
    }

    @Test
    fun `no cloud llm sdk appears in any dependency declaration`() {
        // Arrange — DESIGN.md cost discipline: frontier models never in runtime
        val bannedCoordinates =
            listOf(
                "com.anthropic",
                "com.openai",
                "com.azure.ai",
                "google-cloud-aiplatform",
                "google-cloud-vertexai",
                "com.google.cloud.vertexai",
                "generativelanguage",
            )
        val dependencyFiles = walk(repoRoot, setOf("kts", "toml"))

        // Act / Assert
        dependencyFiles.forEach { file ->
            val content = file.readText().lowercase()
            bannedCoordinates.forEach { coordinate ->
                assertTrue(!content.contains(coordinate)) {
                    "Cost-discipline violation: '$coordinate' found in $file"
                }
            }
        }
    }
}
