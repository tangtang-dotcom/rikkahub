package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R

/**
 * 自动任务设置弹窗。
 *
 * 两种触发模式（RadioButton 单选二选一）：
 *  - 模式 0：可触发次数 —— 会话空闲时自动发送，达到设置次数或次数上限（[MAX_AUTO_TASK_TRIGGER_COUNT]）后自动停止
 *  - 模式 1：定时触发 —— 监听会话空闲，空闲达设定秒数后自动发送
 */
@Composable
fun AutoTaskDialog(
    config: AutoTaskConfig,
    onDismiss: () -> Unit,
    onConfirm: (AutoTaskConfig) -> Unit,
) {
    var currentMessage by remember { mutableStateOf(config.message) }
    var currentMode by remember { mutableIntStateOf(config.mode) }
    var currentCount by remember {
        mutableStateOf(config.triggerCount.coerceIn(1, MAX_AUTO_TASK_TRIGGER_COUNT).toString())
    }
    var currentInterval by remember { mutableStateOf(config.intervalSeconds.toString()) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text("⚡ 自动任务")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    text = "设置自动回复消息，App 将在满足条件时自动发送，无需手动操作。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 回复内容输入
                OutlinedTextField(
                    value = currentMessage,
                    onValueChange = { currentMessage = it },
                    label = { Text("回复内容") },
                    placeholder = { Text("继续") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // 模式 A：可触发次数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = currentMode == 0,
                        onClick = { currentMode = 0 },
                    )
                    Text(
                        text = "可触发次数",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (currentMode == 0) {
                    OutlinedTextField(
                        value = currentCount,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
                                currentCount = value
                            }
                        },
                        label = { Text("触发次数") },
                        supportingText = {
                            Text("可设置次数上限为 100 次，到达设置次数或次数上限后自动停止触发")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                // 模式 B：定时触发（会话空闲）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = currentMode == 1,
                        onClick = { currentMode = 1 },
                    )
                    Text(
                        text = "定时触发",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (currentMode == 1) {
                    OutlinedTextField(
                        value = currentInterval,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
                                currentInterval = value
                            }
                        },
                        label = { Text("空闲秒数") },
                        supportingText = { Text("会话空闲达到指定秒数后自动发送") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "提示：次数模式触发满设置次数后自动停止；定时模式触发后自动任务即清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val count =
                        currentCount.toIntOrNull()?.coerceIn(1, MAX_AUTO_TASK_TRIGGER_COUNT) ?: 1
                    val interval = currentInterval.toIntOrNull()?.coerceAtLeast(1) ?: 60
                    onConfirm(
                        AutoTaskConfig(
                            message = currentMessage.ifBlank { "继续" },
                            mode = currentMode,
                            triggerCount = count,
                            intervalSeconds = interval,
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
