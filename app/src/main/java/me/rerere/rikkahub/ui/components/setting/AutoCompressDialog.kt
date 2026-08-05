package me.rerere.rikkahub.ui.components.setting

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R

/**
 * 自动压缩设置弹窗。
 *
 * 两种触发模式（单选二选一）：
 *  - 模式 0（百分比模式）：基准 token 数（M）× 触发百分比（50-95）
 *  - 模式 1（token 消耗模式）：会话累计 token 消耗（M）超过上限即触发
 *
 * 输入框一律以 M 为单位（1M = 1,000,000 token）：显示时除以 1e6，确认时乘以 1e6。
 */
@Composable
fun AutoCompressDialog(
    enabled: Boolean,
    mode: Int,
    base: Long,
    threshold: Int,
    tokenLimit: Long,
    onDismiss: () -> Unit,
    onConfirm: (enabled: Boolean, mode: Int, base: Long, threshold: Int, tokenLimit: Long) -> Unit,
) {
    var currentEnabled by remember { mutableStateOf(enabled) }
    var currentMode by remember { mutableIntStateOf(mode) }
    var currentBase by remember { mutableStateOf(formatMillion(base)) }
    // 以 String 保存输入值，支持自由删除/编辑，避免 5→50→500 追加问题
    var currentThreshold by remember { mutableStateOf(threshold.toString()) }
    var currentTokenLimit by remember { mutableStateOf(formatMillion(tokenLimit)) }

    // Composable 作用域获取 Context（供 onClick 等非 Composable 回调使用）
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(stringResource(R.string.setting_model_page_auto_compress))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.setting_model_page_auto_compress_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 总开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.setting_model_page_auto_compress_enable),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = currentEnabled,
                        onCheckedChange = { currentEnabled = it },
                    )
                }

                if (currentEnabled) {
                    // 模式 A：百分比模式（基准 + 百分比）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = currentMode == 0,
                            onClick = { currentMode = 0 },
                        )
                        Text(
                            text = stringResource(R.string.setting_model_page_auto_compress_mode_percent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (currentMode == 0) {
                        OutlinedTextField(
                            value = currentBase,
                            onValueChange = { value ->
                                if (value.matches(MILLION_INPUT_REGEX)) {
                                    currentBase = value
                                }
                            },
                            label = { Text(stringResource(R.string.setting_model_page_auto_compress_base)) },
                            supportingText = { Text(stringResource(R.string.setting_model_page_auto_compress_m_unit)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = currentThreshold,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() }) {
                                    currentThreshold = value
                                }
                            },
                            label = { Text(stringResource(R.string.setting_model_page_auto_compress_threshold)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    // 模式 B：token 消耗模式
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = currentMode == 1,
                            onClick = { currentMode = 1 },
                        )
                        Text(
                            text = stringResource(R.string.setting_model_page_auto_compress_mode_token),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (currentMode == 1) {
                        OutlinedTextField(
                            value = currentTokenLimit,
                            onValueChange = { value ->
                                if (value.matches(MILLION_INPUT_REGEX)) {
                                    currentTokenLimit = value
                                }
                            },
                            label = { Text(stringResource(R.string.setting_model_page_auto_compress_token_limit)) },
                            supportingText = { Text(stringResource(R.string.setting_model_page_auto_compress_m_unit)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.auto_compress_reset_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentEnabled) {
                        when (currentMode) {
                            0 -> {
                                if (currentBase.isBlank() || parseMillion(currentBase) <= 0L) {
                                    Toast
                                        .makeText(
                                            ctx,
                                            ctx.getString(R.string.auto_compress_invalid_input),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@TextButton
                                }
                            }

                            1 -> {
                                if (currentTokenLimit.isBlank() || parseMillion(currentTokenLimit) <= 0L) {
                                    Toast
                                        .makeText(
                                            ctx,
                                            ctx.getString(R.string.auto_compress_invalid_input),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@TextButton
                                }
                            }
                        }
                    }
                    onConfirm(
                        currentEnabled,
                        currentMode,
                        parseMillion(currentBase),
                        currentThreshold.toIntOrNull()?.coerceIn(50, 95) ?: 80,
                        parseMillion(currentTokenLimit),
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

/** 输入框允许的格式：非负整数或小数（可带一位小数点） */
private val MILLION_INPUT_REGEX = Regex("^\\d+(\\.\\d+)?$")

/** 以 M 为单位展示（1M = 1,000,000）：整数时省略小数部分 */
private fun formatMillion(value: Long): String {
    val m = value / 1_000_000.0
    return if (m == m.toLong().toDouble()) m.toLong().toString() else m.toString()
}

/** 从 M 单位的输入解析为原始 token 数（×1e6），非法输入按 0 处理 */
private fun parseMillion(text: String): Long {
    val v = text.toDoubleOrNull() ?: 0.0
    return (v * 1_000_000).toLong().coerceAtLeast(0L)
}
