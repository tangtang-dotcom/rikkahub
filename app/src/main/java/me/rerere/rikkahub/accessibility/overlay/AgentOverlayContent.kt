package me.rerere.rikkahub.accessibility.overlay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
private val SuccessColor=Color(0xFF34C759)
@Composable internal fun AgentOverlayGlow(state: AgentOverlayState){val c=when(state.phase){AgentOverlayPhase.FAILED->MaterialTheme.colorScheme.error;AgentOverlayPhase.FINISHED->SuccessColor;else->MaterialTheme.colorScheme.primary};Box(Modifier.fillMaxSize().background(c.copy(alpha=.1f)))}
@Composable internal fun AgentOverlayOrb(state: AgentOverlayState,onToggleCollapse:()->Unit){AssistChip(onClick=onToggleCollapse,label={Text(state.status.localizedText(),maxLines=1)},leadingIcon={Text("●",color=MaterialTheme.colorScheme.primary)})}
@Composable internal fun AgentOverlayBubble(state:AgentOverlayState,onToggleCollapse:()->Unit={},onCancel:()->Unit={},onPause:()->Unit={},onResume:()->Unit={},onSupplement:()->Unit={},onStop:()->Unit={},onClose:()->Unit={}){Card(Modifier.fillMaxWidth().padding(12.dp),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(state.status.localizedText(),style=MaterialTheme.typography.titleMedium);if(state.detailText.isNotBlank())Text(state.detailText,maxLines=6);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(state.phase==AgentOverlayPhase.RUNNING){TextButton(onClick=onPause){Text(stringResource(R.string.overlay_pause))};TextButton(onClick=onStop){Text(stringResource(R.string.action_stop))}}else if(state.phase==AgentOverlayPhase.PAUSED)TextButton(onClick=onResume){Text(stringResource(R.string.overlay_resume))};TextButton(onClick=onClose){Text(stringResource(R.string.action_close))}}}}}
@Composable internal fun AgentResultCard(state:AgentOverlayState,onClose:()->Unit){Card(Modifier.fillMaxWidth().padding(12.dp),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(state.phase==AgentOverlayPhase.FAILED)stringResource(R.string.overlay_substatus_failed)else stringResource(R.string.overlay_substatus_finished),style=MaterialTheme.typography.labelLarge);Text(state.detailText.ifBlank{state.status.localizedText()},style=MaterialTheme.typography.bodyLarge);TextButton(onClick=onClose){Text(stringResource(R.string.action_close))}}}}
