package me.rerere.rikkahub.ui.pages.extensions

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun RootTerminalSettingsPage(
    vm: SettingVM = koinViewModel(),
    controller: AndroidRootTerminalController = koinInject(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showWarning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
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
                                    if (it) vm.updateSettings(settings.copy(rootTerminalNeedsApproval = true))
                                    else showWarning = true
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
                                onCheckedChange = {
                                    vm.updateSettings(settings.copy(accessibilityNeedsApproval = it))
                                },
                            )
                        },
                    )
                    item(
                        onClick = {
                            if (!checking) {
                                checking = true
                                status = null
                                scope.launch {
                                    status = withContext(Dispatchers.IO) {
                                        runCatching { controller.rootStatus() }.fold(
                                            onSuccess = { result ->
                                                if (result.exitCode == 0 && result.stdout.trim() == "0") {
                                                    "✓ Root available (UID 0)"
                                                } else {
                                                    "✗ " + result.stderr.ifBlank { result.stdout }.trim().ifBlank { "Root unavailable" }
                                                }
                                            },
                                            onFailure = { "✗ " + (it.message ?: "Root unavailable") },
                                        )
                                    }
                                    checking = false
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.root_terminal_check_status)) },
                        supportingContent = {
                            Text(status ?: stringResource(
                                if (checking) R.string.root_terminal_checking else R.string.root_terminal_check_status_desc
                            ))
                        },
                    )
                }
            }
        }
    }
}
