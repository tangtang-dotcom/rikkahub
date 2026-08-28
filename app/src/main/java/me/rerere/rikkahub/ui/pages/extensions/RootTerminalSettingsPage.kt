package me.rerere.rikkahub.ui.pages.extensions

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import me.rerere.rikkahub.data.terminal.ApkAnalysisInstallProgress
import me.rerere.rikkahub.data.terminal.ApkAnalysisInstallResult
import me.rerere.rikkahub.data.terminal.LinuxApkAnalysisInstaller
import me.rerere.rikkahub.data.terminal.LinuxEnvironmentHealth
import me.rerere.rikkahub.data.terminal.LinuxEnvironmentInstaller
import me.rerere.rikkahub.data.terminal.LinuxEnvironmentState
import me.rerere.rikkahub.data.terminal.LinuxInstallProgress
import me.rerere.rikkahub.data.terminal.LinuxInstallResult
import me.rerere.rikkahub.data.terminal.LinuxPackageProfile
import me.rerere.rikkahub.data.terminal.LinuxPackageProfileInstaller
import me.rerere.rikkahub.data.terminal.LinuxPackageProfiles
import me.rerere.rikkahub.data.terminal.PackageProfileInstallResult
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class LinuxInstallTarget { BASE, PYTHON, NODE, SSH, APK_ANALYSIS }

private data class LinuxProfileUi(
    val target: LinuxInstallTarget,
    val profile: LinuxPackageProfile,
    val title: Int,
    val description: Int,
)

@Composable
fun RootTerminalSettingsPage(
    vm: SettingVM = koinViewModel(),
    controller: AndroidRootTerminalController = koinInject(),
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val linuxInstaller = remember(context.applicationContext) { LinuxEnvironmentInstaller(context.applicationContext) }
    val apkInstaller = remember(context.applicationContext) { LinuxApkAnalysisInstaller(context.applicationContext) }
    val profiles = remember {
        listOf(
            LinuxProfileUi(LinuxInstallTarget.PYTHON, LinuxPackageProfiles.PYTHON, R.string.linux_python_tools, R.string.linux_python_tools_desc),
            LinuxProfileUi(LinuxInstallTarget.NODE, LinuxPackageProfiles.NODE, R.string.linux_node_tools, R.string.linux_node_tools_desc),
            LinuxProfileUi(LinuxInstallTarget.SSH, LinuxPackageProfiles.SSH, R.string.linux_ssh_tools, R.string.linux_ssh_tools_desc),
        )
    }
    val profileInstallers = remember(context.applicationContext) {
        profiles.associate { it.target to LinuxPackageProfileInstaller(context.applicationContext, it.profile) }
    }

    var showWarning by remember { mutableStateOf(false) }
    var rootStatus by remember { mutableStateOf<String?>(null) }
    var checkingRoot by remember { mutableStateOf(false) }
    var linuxStatus by remember { mutableStateOf(linuxInstaller.status()) }
    var health by remember { mutableStateOf<LinuxEnvironmentHealth?>(null) }
    var checkingHealth by remember { mutableStateOf(false) }
    var busyTarget by remember { mutableStateOf<LinuxInstallTarget?>(null) }
    var linuxProgress by remember { mutableStateOf<LinuxInstallProgress?>(null) }
    var apkProgress by remember { mutableStateOf<ApkAnalysisInstallProgress?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var profileReady by remember {
        mutableStateOf(profiles.associate { it.target to profileInstallers.getValue(it.target).isReady() })
    }
    var apkReady by remember { mutableStateOf(apkInstaller.isReady()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun refreshLinuxState() {
        linuxStatus = linuxInstaller.status()
        profileReady = profiles.associate { it.target to profileInstallers.getValue(it.target).isReady() }
        apkReady = apkInstaller.isReady()
        health = null
    }

    fun installBase(force: Boolean) {
        if (busyTarget != null) return
        busyTarget = LinuxInstallTarget.BASE
        resultMessage = null
        scope.launch {
            val result = linuxInstaller.install(forceToolInstall = force) { update ->
                withContext(Dispatchers.Main.immediate) { linuxProgress = update }
            }
            refreshLinuxState()
            linuxProgress = null
            busyTarget = null
            resultMessage = linuxResultMessage(context, result)
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.root_terminal_disable_approval_title)) },
            text = { Text(stringResource(R.string.root_terminal_disable_approval_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings(settings.copy(rootTerminalNeedsApproval = false))
                    showWarning = false
                }) { Text(stringResource(R.string.root_terminal_disable_approval_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.root_terminal_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.root_terminal_enabled)) },
                        supportingContent = { Text(stringResource(R.string.root_terminal_enabled_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.rootTerminalEnabled,
                                onCheckedChange = {
                                    vm.updateSettings(settings.copy(
                                        rootTerminalEnabled = it,
                                        accessibilityProtectionEnabled = settings.accessibilityProtectionEnabled && it,
                                    ))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.root_terminal_require_approval)) },
                        supportingContent = { Text(stringResource(R.string.root_terminal_require_approval_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.rootTerminalNeedsApproval,
                                enabled = settings.rootTerminalEnabled,
                                onCheckedChange = {
                                    if (it) vm.updateSettings(settings.copy(rootTerminalNeedsApproval = true)) else showWarning = true
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.accessibility_protection_enabled)) },
                        supportingContent = { Text(stringResource(R.string.accessibility_protection_enabled_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.accessibilityProtectionEnabled,
                                enabled = settings.rootTerminalEnabled,
                                onCheckedChange = { vm.updateSettings(settings.copy(accessibilityProtectionEnabled = it)) },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.accessibility_require_approval)) },
                        supportingContent = { Text(stringResource(R.string.accessibility_require_approval_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.accessibilityNeedsApproval,
                                onCheckedChange = { vm.updateSettings(settings.copy(accessibilityNeedsApproval = it)) },
                            )
                        },
                    )
                    item(
                        onClick = {
                            if (!checkingRoot) {
                                checkingRoot = true
                                rootStatus = null
                                scope.launch {
                                    rootStatus = withContext(Dispatchers.IO) {
                                        runCatching { controller.rootStatus() }.fold(
                                            onSuccess = { if (it.exitCode == 0 && it.stdout.trim() == "0") "✓ Root available (UID 0)" else "✗ " + it.stderr.ifBlank { it.stdout }.trim() },
                                            onFailure = { "✗ " + (it.message ?: "Root unavailable") },
                                        )
                                    }
                                    checkingRoot = false
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.root_terminal_check_status)) },
                        supportingContent = { Text(rootStatus ?: stringResource(if (checkingRoot) R.string.root_terminal_checking else R.string.root_terminal_check_status_desc)) },
                    )
                }
            }

            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.linux_environment_title)) },
                        supportingContent = { Text(linuxProgress?.summary(context) ?: linuxStatusSummary(context, linuxStatus.state, linuxStatus.version)) },
                        trailingContent = {
                            TextButton(
                                enabled = settings.rootTerminalEnabled && busyTarget == null && linuxStatus.state != LinuxEnvironmentState.READY,
                                onClick = { installBase(false) },
                            ) {
                                Text(stringResource(when {
                                    busyTarget == LinuxInstallTarget.BASE -> R.string.linux_environment_busy
                                    linuxStatus.state == LinuxEnvironmentState.BASE_READY -> R.string.linux_environment_continue
                                    else -> R.string.linux_environment_install
                                }))
                            }
                        },
                    )
                    if (linuxStatus.state == LinuxEnvironmentState.READY) {
                        item(
                            headlineContent = { Text(stringResource(R.string.linux_environment_health)) },
                            supportingContent = { Text(healthSummary(context, health)) },
                            trailingContent = {
                                TextButton(
                                    enabled = busyTarget == null && !checkingHealth,
                                    onClick = {
                                        if (health?.healthy == false) installBase(true) else scope.launch {
                                            checkingHealth = true
                                            health = linuxInstaller.inspectHealth()
                                            checkingHealth = false
                                        }
                                    },
                                ) { Text(stringResource(if (health?.healthy == false) R.string.linux_environment_repair else R.string.linux_environment_check)) }
                            },
                        )
                    }
                    resultMessage?.let { message -> item(headlineContent = { Text(message) }) }
                }
            }

            if (linuxStatus.state == LinuxEnvironmentState.READY) {
                item {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        profiles.forEach { profileUi ->
                            val ready = profileReady[profileUi.target] == true
                            item(
                                headlineContent = { Text(stringResource(profileUi.title)) },
                                supportingContent = { Text(stringResource(profileUi.description)) },
                                trailingContent = {
                                    TextButton(
                                        enabled = busyTarget == null && !ready,
                                        onClick = {
                                            busyTarget = profileUi.target
                                            resultMessage = null
                                            scope.launch {
                                                val result = profileInstallers.getValue(profileUi.target).install()
                                                profileReady = profileReady + (profileUi.target to profileInstallers.getValue(profileUi.target).isReady())
                                                busyTarget = null
                                                resultMessage = packageResultMessage(context, result)
                                            }
                                        },
                                    ) { Text(stringResource(if (ready) R.string.linux_installed else if (busyTarget == profileUi.target) R.string.linux_environment_busy else R.string.linux_install)) }
                                },
                            )
                        }
                        item(
                            headlineContent = { Text(stringResource(R.string.linux_apk_tools)) },
                            supportingContent = { Text(apkProgress?.summary(context) ?: stringResource(R.string.linux_apk_tools_desc)) },
                            trailingContent = {
                                TextButton(
                                    enabled = busyTarget == null && !apkReady,
                                    onClick = {
                                        busyTarget = LinuxInstallTarget.APK_ANALYSIS
                                        resultMessage = null
                                        scope.launch {
                                            val result = apkInstaller.install { update -> withContext(Dispatchers.Main.immediate) { apkProgress = update } }
                                            apkReady = apkInstaller.isReady()
                                            apkProgress = null
                                            busyTarget = null
                                            resultMessage = apkResultMessage(context, result)
                                        }
                                    },
                                ) { Text(stringResource(if (apkReady) R.string.linux_installed else if (busyTarget == LinuxInstallTarget.APK_ANALYSIS) R.string.linux_environment_busy else R.string.linux_install)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun linuxStatusSummary(context: Context, state: LinuxEnvironmentState, version: String?): String = when (state) {
    LinuxEnvironmentState.NOT_INSTALLED -> context.getString(R.string.linux_environment_desc) + "\n" + context.getString(R.string.linux_environment_not_installed)
    LinuxEnvironmentState.BASE_READY -> context.getString(R.string.linux_environment_base_ready)
    LinuxEnvironmentState.READY -> context.getString(R.string.linux_environment_ready, version?.let { " ($it)" }.orEmpty())
}

private fun healthSummary(context: Context, health: LinuxEnvironmentHealth?): String = when {
    health == null -> context.getString(R.string.linux_environment_health_desc)
    health.healthy -> context.getString(R.string.linux_environment_healthy, health.availableTools.size)
    else -> context.getString(R.string.linux_environment_unhealthy, health.missingTools.joinToString(", ").ifBlank { "mounts" })
}

private fun LinuxInstallProgress.summary(context: Context): String = when {
    totalBytes > 0 -> "${stage.name.lowercase().replace('_', ' ')}: ${Formatter.formatFileSize(context, downloadedBytes)} / ${Formatter.formatFileSize(context, totalBytes)}"
    else -> stage.name.lowercase().replace('_', ' ')
}

private fun ApkAnalysisInstallProgress.summary(context: Context): String = when {
    totalBytes > 0 -> "${artifactName.orEmpty()}: ${Formatter.formatFileSize(context, downloadedBytes)} / ${Formatter.formatFileSize(context, totalBytes)}"
    else -> stage.name.lowercase().replace('_', ' ')
}

private fun linuxResultMessage(context: Context, result: LinuxInstallResult): String = when (result) {
    LinuxInstallResult.AlreadyReady -> context.getString(R.string.linux_install_complete)
    is LinuxInstallResult.Installed -> context.getString(R.string.linux_install_complete)
    is LinuxInstallResult.UnsupportedAbi -> context.getString(R.string.linux_abi_unsupported, result.abi)
    LinuxInstallResult.RootUnavailable -> context.getString(R.string.linux_root_unavailable)
    LinuxInstallResult.BusyBoxUnavailable -> context.getString(R.string.linux_busybox_unavailable)
    LinuxInstallResult.EnvironmentUnavailable -> context.getString(R.string.linux_install_failed, "environment")
    is LinuxInstallResult.Failed -> context.getString(R.string.linux_install_failed, result.stage.name)
}

private fun packageResultMessage(context: Context, result: PackageProfileInstallResult): String = when (result) {
    PackageProfileInstallResult.AlreadyReady, PackageProfileInstallResult.Installed -> context.getString(R.string.linux_install_complete)
    PackageProfileInstallResult.EnvironmentNotReady -> context.getString(R.string.linux_environment_not_installed)
    is PackageProfileInstallResult.Failed -> context.getString(R.string.linux_install_failed, result.stage.name)
}

private fun apkResultMessage(context: Context, result: ApkAnalysisInstallResult): String = when (result) {
    ApkAnalysisInstallResult.AlreadyReady, ApkAnalysisInstallResult.Installed -> context.getString(R.string.linux_install_complete)
    ApkAnalysisInstallResult.EnvironmentNotReady -> context.getString(R.string.linux_environment_not_installed)
    is ApkAnalysisInstallResult.InsufficientSpace -> context.getString(
        R.string.linux_space_insufficient,
        Formatter.formatFileSize(context, result.requiredBytes),
        Formatter.formatFileSize(context, result.availableBytes),
    )
    is ApkAnalysisInstallResult.Failed -> context.getString(R.string.linux_install_failed, result.stage.name)
}
