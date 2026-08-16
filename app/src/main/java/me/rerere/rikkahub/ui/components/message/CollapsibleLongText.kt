package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.rikkahub.R

/**
 * 长消息折叠容器：内容超过 [collapseThreshold] 字符且 [autoCollapse] 开启时，
 * 默认折叠为高度受限的预览 + 「展开」按钮，点击展开/收起。
 *
 * 设计参考 ChatGPT 代码块折叠：
 * - 折叠态：内容裁剪到 [COLLAPSED_MAX_HEIGHT]，下方显示居中的「展开」按钮
 * - 展开态：完整内容 + 下方「收起」按钮
 *
 * 流式生成期间调用方应传 autoCollapse = false，避免生成过程中反复折叠。
 */
private const val COLLAPSED_MAX_HEIGHT = 180

@Composable
fun CollapsibleLongText(
    text: String,
    autoCollapse: Boolean,
    modifier: Modifier = Modifier,
    collapseThreshold: Int = 600,
    content: @Composable () -> Unit,
) {
    val longEnough = text.length > collapseThreshold
    // 以 (text, autoCollapse) 为 key：流式结束时 text 稳定/autoCollapse 翻转，
    // 自动按新状态初始化；用户手动展开后，相同 key 保持用户选择。
    var expanded by remember(text, autoCollapse) {
        mutableStateOf(!(autoCollapse && longEnough))
    }

    if (!longEnough || !autoCollapse) {
        content()
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (expanded) {
            content()
            CollapseToggleRow(
                expanded = true,
                onClick = { expanded = false },
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = COLLAPSED_MAX_HEIGHT.dp)
                        .clipToBounds(),
            ) {
                content()
            }
            CollapseToggleRow(
                expanded = false,
                onClick = { expanded = true },
            )
        }
    }
}

@Composable
private fun CollapseToggleRow(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick) {
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text =
                    stringResource(
                        if (expanded) R.string.code_block_collapse else R.string.code_block_expand,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
