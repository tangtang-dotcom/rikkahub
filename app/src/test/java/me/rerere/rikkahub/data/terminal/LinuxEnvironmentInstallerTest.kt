package me.rerere.rikkahub.data.terminal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LinuxEnvironmentInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun artifactSelectionUsesFirstSupportedAbiWithPinnedIntegrityMetadata() {
        val artifact = LinuxEnvironmentInstaller.artifactForAbis(
            listOf("armeabi-v7a", "arm64-v8a", "x86_64"),
        )

        requireNotNull(artifact)
        assertEquals("3.24.1", artifact.version)
        assertTrue(artifact.fileName.endsWith("-aarch64.tar.gz"))
        assertTrue(artifact.url.startsWith("https://dl-cdn.alpinelinux.org/alpine/v3.24/"))
        assertEquals(64, artifact.sha256.length)
        assertEquals(4_023_732L, artifact.sizeBytes)
    }

    @Test
    fun unsupportedAbiDoesNotGuessAnArtifact() {
        assertNull(LinuxEnvironmentInstaller.artifactForAbis(listOf("armeabi-v7a", "x86")))
    }

    @Test
    fun readinessRequiresMarkerAndBusyBoxAndTracksCommonToolsSeparately() {
        val rootfs = temporaryFolder.newFolder("rootfs")
        val bin = File(rootfs, "bin").apply { mkdirs() }
        val busyBox = File(bin, "busybox")
        val ready = File(rootfs, LinuxEnvironmentPaths.READY_MARKER)

        assertFalse(LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath))
        busyBox.writeText("busybox")
        assertFalse(LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath))
        ready.writeText("version=3.24.1\n")
        assertTrue(LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath))
        assertFalse(LinuxEnvironmentPaths.commonToolsReady(rootfs.absolutePath))

        File(rootfs, LinuxEnvironmentPaths.COMMON_TOOLS_MARKER).writeText("3.24.1\n")
        assertFalse(LinuxEnvironmentPaths.commonToolsReady(rootfs.absolutePath))

        File(rootfs, LinuxEnvironmentPaths.COMMON_TOOLS_MARKER).writeText(
            "alpine=3.24.1\ntoolset=${LinuxEnvironmentPaths.TOOLSET_REVISION}\nprofiles=agent\n",
        )
        assertTrue(LinuxEnvironmentPaths.commonToolsReady(rootfs.absolutePath))
    }

    @Test
    fun baseToolsetContainsAgentEssentialsWithoutPythonOrInteractiveEditors() {
        val packages = LinuxEnvironmentInstaller.AGENT_PACKAGES

        assertTrue(packages.containsAll(listOf("ripgrep", "fd", "diffutils", "patch", "rsync")))
        assertFalse(packages.contains("python3"))
        assertFalse(packages.contains("uv"))
        assertFalse(packages.contains("vim"))
        assertFalse(packages.contains("nano"))
        assertEquals(packages.distinct(), packages)
    }

    @Test
    fun packageProfilesCoverPythonNodeAndSshToolchains() {
        assertTrue(
            LinuxPackageProfiles.PYTHON.packages
                .containsAll(listOf("python3", "py3-virtualenv", "pipx", "uv", "ruff")),
        )
        assertTrue(LinuxPackageProfiles.NODE.packages.containsAll(listOf("nodejs", "npm")))
        assertTrue(LinuxPackageProfiles.SSH.packages.contains("openssh"))
        LinuxPackageProfiles.ALL.forEach { profile ->
            assertEquals(profile.packages.distinct(), profile.packages)
            assertTrue(profile.markerName.startsWith(".rikkahub-"))
            assertTrue(profile.verifyCommands.isNotEmpty())
        }
        assertEquals(LinuxPackageProfiles.ALL.map { it.id }.distinct(), LinuxPackageProfiles.ALL.map { it.id })
    }

    @Test
    fun packageProfileReadinessHonoursMarkerRevisionAndLegacyBinaryInstalls() {
        val rootfs = temporaryFolder.newFolder("python-rootfs")
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/busybox").writeText("busybox")
        File(rootfs, LinuxEnvironmentPaths.READY_MARKER).writeText("version=3.24.1\n")
        File(rootfs, LinuxEnvironmentPaths.COMMON_TOOLS_MARKER).writeText(
            "toolset=${LinuxEnvironmentPaths.TOOLSET_REVISION}\n",
        )
        val python = LinuxPackageProfiles.PYTHON

        assertFalse(LinuxEnvironmentPaths.packageProfileReady(rootfs.absolutePath, python))

        File(rootfs, python.markerName).writeText("profile=0\n")
        assertFalse(LinuxEnvironmentPaths.packageProfileReady(rootfs.absolutePath, python))

        File(rootfs, python.markerName).writeText("profile=${python.revision}\n")
        assertTrue(LinuxEnvironmentPaths.packageProfileReady(rootfs.absolutePath, python))

        // toolset 2 及更早的环境把 Python 装进基础工具集且无独立 marker，靠二进制存在性识别。
        File(rootfs, python.markerName).delete()
        File(rootfs, "usr/bin").mkdirs()
        File(rootfs, "usr/bin/python3").writeText("python3")
        assertFalse(LinuxEnvironmentPaths.packageProfileReady(rootfs.absolutePath, python))
        File(rootfs, "usr/bin/uv").writeText("uv")
        assertTrue(LinuxEnvironmentPaths.packageProfileReady(rootfs.absolutePath, python))
    }

    @Test
    fun apkAnalysisReadinessRequiresCurrentMarkerAndManagedFiles() {
        val rootfs = temporaryFolder.newFolder("analysis-rootfs")
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/busybox").writeText("busybox")
        File(rootfs, LinuxEnvironmentPaths.READY_MARKER).writeText("version=3.24.1\n")
        File(rootfs, LinuxEnvironmentPaths.COMMON_TOOLS_MARKER).writeText(
            "toolset=${LinuxEnvironmentPaths.TOOLSET_REVISION}\n",
        )
        val current = File(rootfs, "opt/rikkahub/apk-analysis/current")
        listOf("bin/java", "jadx/bin/jadx", "bin/apktool", "bin/smali", "bin/baksmali").forEach { path ->
            File(current, path).apply {
                parentFile?.mkdirs()
                writeText(path)
            }
        }

        File(rootfs, LinuxEnvironmentPaths.APK_ANALYSIS_MARKER).writeText("profile=0\n")
        assertFalse(LinuxEnvironmentPaths.apkAnalysisReady(rootfs.absolutePath))

        File(rootfs, LinuxEnvironmentPaths.APK_ANALYSIS_MARKER).writeText(
            "profile=${LinuxEnvironmentPaths.APK_ANALYSIS_REVISION}\n",
        )
        assertTrue(LinuxEnvironmentPaths.apkAnalysisReady(rootfs.absolutePath))
        File(current, "jadx/bin/jadx").delete()
        assertFalse(LinuxEnvironmentPaths.apkAnalysisReady(rootfs.absolutePath))
    }
}
