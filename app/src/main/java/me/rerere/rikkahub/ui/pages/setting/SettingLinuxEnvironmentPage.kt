package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.terminal.GlobalLinuxEnvironmentManager
import me.rerere.rikkahub.data.terminal.LinuxInstallProgress
import me.rerere.rikkahub.data.terminal.LinuxInstallStage
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.compose.koinInject

@Composable
fun SettingLinuxEnvironmentPage() {
    val manager: GlobalLinuxEnvironmentManager = koinInject()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(manager.status()) }
    var progress by remember { mutableStateOf<LinuxInstallProgress?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Linux 工作环境") }, navigationIcon = { BackButton() }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (status.ready) "全局环境已就绪" else if (status.baseReady) "基础环境已安装，工具尚未完成" else "尚未安装", style = MaterialTheme.typography.titleLarge)
            status.version?.let { Text("Alpine $it · 所有助手和 Root 终端共用") }
            Text("这是 Root chroot 全局工具环境，不属于任何工作空间，也不是 PRoot 沙箱。Linux 命令默认工作目录为 /workspace，可访问 /sdcard 和 /data/local/tmp。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress?.let { value ->
                Text(stageText(value.stage))
                if (value.stage == LinuxInstallStage.DOWNLOADING && value.total > 0) {
                    LinearProgressIndicator(progress = { value.downloaded.toFloat() / value.total }, modifier = Modifier.fillMaxWidth())
                } else {
                    CircularProgressIndicator()
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        message = null
                        manager.install { progress = it }.onFailure { message = it.message ?: "安装失败" }
                        progress = null
                        status = manager.status()
                    }
                },
                enabled = progress == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (status.ready) "修复或升级工具" else "下载并安装") }
            if (status.baseReady) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            progress = LinuxInstallProgress(LinuxInstallStage.CHECKING)
                            manager.remove().onFailure { message = it.message }
                            progress = null
                            status = manager.status()
                        }
                    },
                    enabled = progress == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除全局 Linux 环境") }
            }
        }
    }
}

private fun stageText(stage: LinuxInstallStage) = when (stage) {
    LinuxInstallStage.CHECKING -> "正在检查 Root 和 BusyBox…"
    LinuxInstallStage.DOWNLOADING -> "正在下载 Alpine…"
    LinuxInstallStage.VERIFYING -> "正在校验 SHA-256…"
    LinuxInstallStage.EXTRACTING -> "正在安装基础环境…"
    LinuxInstallStage.INSTALLING_TOOLS -> "正在安装常用工具…"
    LinuxInstallStage.COMPLETE -> "安装完成"
}
