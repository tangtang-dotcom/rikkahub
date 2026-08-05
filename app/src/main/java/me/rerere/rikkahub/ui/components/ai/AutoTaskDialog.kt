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
 *  - 模式 0：固定时间触发 —— 设置定时器，到点自动发送
 *  - 模式 1：不定时触发 —— 监听会话空闲，空闲达设定值后自动发送
 */
@Composable
fun AutoTaskDialog(
    config: AutoTaskConfig,
    onDismiss: () -> Unit,
    onConfirm: (AutoTaskConfig) -> Unit,
) {
    var currentMessage by remember { mutableStateOf(config.message) }
    var currentMode by remember { mutableIntStateOf(config.mode) }
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
                    text = "设置自动回复消息，App 将在满足条件时自动发送，无需手动操作。触发后自动任务即清除（一次性触发）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 消息内容输入
                OutlinedTextField(
                    value = currentMessage,
                    onValueChange = { currentMessage = it },
                    label = { Text("自动回复内容") },
                    placeholder = { Text("继续") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // 模式 A：固定时间触发
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = currentMode == 0,
                        onClick = { currentMode = 0 },
                    )
                    Text(
                        text = "固定时间触发",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (currentMode == 0) {
                    OutlinedTextField(
                        value = currentInterval,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
                                currentInterval = value
                            }
                        },
                        label = { Text("定时秒数") },
                        supportingText = { Text("设置后到达指定秒数自动发送消息") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                // 模式 B：不定时触发（空闲）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = currentMode == 1,
                        onClick = { currentMode = 1 },
                    )
                    Text(
                        text = "不定时触发（会话空闲后）",
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
                        supportingText = { Text("会话无新消息且无生成中回复达到指定秒数后自动发送") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "提示：触发后自动任务即清除，需重新设置以再次触发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val interval = currentInterval.toIntOrNull()?.coerceAtLeast(1) ?: 60
                    onConfirm(
                        AutoTaskConfig(
                            message = currentMessage.ifBlank { "继续" },
                            mode = currentMode,
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
