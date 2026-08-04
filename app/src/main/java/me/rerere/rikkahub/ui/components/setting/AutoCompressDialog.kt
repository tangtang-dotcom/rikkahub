package me.rerere.rikkahub.ui.components.setting

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R

@Composable
fun AutoCompressDialog(
    enabled: Boolean,
    threshold: Int,
    tokenLimit: Long,
    onDismiss: () -> Unit,
    onConfirm: (enabled: Boolean, threshold: Int, tokenLimit: Long) -> Unit
) {
    var currentEnabled by remember { mutableIntStateOf(if (enabled) 1 else 0) }
    var currentThreshold by remember { mutableFloatStateOf(threshold.toFloat()) }
    var currentTokenLimit by remember { mutableStateOf(tokenLimit.toString()) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(stringResource(R.string.setting_model_page_auto_compress))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_model_page_auto_compress_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setting_model_page_auto_compress_desc),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = currentEnabled == 1,
                        onCheckedChange = { currentEnabled = if (it) 1 else 0 }
                    )
                }

                if (currentEnabled == 1) {
                    OutlinedTextField(
                        value = currentThreshold.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let {
                                currentThreshold = it.toFloat()
                            }
                        },
                        label = { Text(stringResource(R.string.setting_model_page_auto_compress_threshold)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 对话 token 上限：与阈值并列，均在开关内（依赖自动压缩开关，开关关闭时不可设置）
                    OutlinedTextField(
                        value = currentTokenLimit,
                        onValueChange = { currentTokenLimit = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.setting_model_page_auto_compress_token_limit)) },
                        supportingText = { Text(stringResource(R.string.setting_model_page_auto_compress_token_limit_desc)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // 对话 token 上限：与阈值并列，独立于开关（开关关闭时也可单独设置，开启后生效）
                OutlinedTextField(
                    value = currentTokenLimit,
                    onValueChange = { currentTokenLimit = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.setting_model_page_auto_compress_token_limit)) },
                    supportingText = { Text(stringResource(R.string.setting_model_page_auto_compress_token_limit_desc)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "⚠️ 自动压缩将重置当前对话中的历史消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        currentEnabled == 1,
                        currentThreshold.toInt().coerceIn(50, 95),
                        currentTokenLimit.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    )
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}