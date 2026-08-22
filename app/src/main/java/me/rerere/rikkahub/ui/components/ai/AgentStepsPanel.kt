package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 聊天输入框上方的「工具步骤实时窗口」。
 *
 * 订阅本次运行 in-flight 的工具调用，逐条展示 名称 + 状态图标 + 摘要；
 * 空运行自动收起，运行中有新步骤时自动滚动到底部。
 * 数据源由持有方（ChatVM）喂入 [AgentStepUi]。
 */
enum class AgentStepStatus { Running, Done, Failed, AwaitingApproval }

data class AgentStepUi(
    val id: Long,
    val toolName: String,
    val status: AgentStepStatus,
    val summary: String? = null,
)

private fun AgentStepStatus.icon() = when (this) {
    AgentStepStatus.Running -> "⚙️"
    AgentStepStatus.Done -> "✅"
    AgentStepStatus.Failed -> "❌"
    AgentStepStatus.AwaitingApproval -> "🔧"
}

@Composable
fun AgentStepsPanel(
    steps: List<AgentStepUi>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = steps.isNotEmpty(),
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically { -it / 2 },
        exit = slideOutVertically { -it / 2 },
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(steps.size) {
            if (steps.isNotEmpty()) listState.animateScrollToItem(steps.lastIndex)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(steps, key = { it.id }) { step ->
                    AgentStepRow(step)
                }
            }
        }
    }
}

@Composable
private fun AgentStepRow(step: AgentStepUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = step.status.icon(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = step.toolName,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        if (!step.summary.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = step.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}