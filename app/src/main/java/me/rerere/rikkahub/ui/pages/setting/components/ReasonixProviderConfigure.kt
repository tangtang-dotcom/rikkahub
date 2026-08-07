package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.R

/**
 * Reasonix Provider 配置页。
 * - baseUrl：Reasonix serve 入口（:10002 nginx Basic Auth 或直连 :9899 token）
 * - username/password：nginx Basic Auth（与 reasonix-android 客户端一致）
 * - token：Reasonix serve token 模式（留空则走 Basic Auth）
 */
@Composable
fun ReasonixProviderConfigure(
    provider: ProviderSetting.Reasonix,
    onEdit: (ProviderSetting.Reasonix) -> Unit,
) {
    provider.description()

    OutlinedTextField(
        value = provider.name,
        onValueChange = { onEdit(provider.copy(name = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_name)) },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
        modifier = Modifier.fillMaxWidth(),
        isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
    )

    OutlinedTextField(
        value = provider.username,
        onValueChange = { onEdit(provider.copy(username = it.trim())) },
        label = { Text("用户名（Basic Auth）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = provider.password,
        onValueChange = { onEdit(provider.copy(password = it)) },
        label = { Text("密码（Basic Auth）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    if (passwordVisible) HugeIcons.ViewOff else HugeIcons.View,
                    contentDescription = null,
                )
            }
        },
    )

    var tokenVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = provider.token,
        onValueChange = { onEdit(provider.copy(token = it.trim())) },
        label = { Text("Token（serve token 模式，可选）") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                Icon(
                    if (tokenVisible) HugeIcons.ViewOff else HugeIcons.View,
                    contentDescription = null,
                )
            }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.setting_provider_page_enable))
        Switch(
            checked = provider.enabled,
            onCheckedChange = { onEdit(provider.copy(enabled = it)) },
        )
    }

    Text(
        text = "Reasonix 会话由服务端管理（自动压缩/缓存优化继承）。" +
            "关闭本开关即继续使用原客户端，互不影响。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
