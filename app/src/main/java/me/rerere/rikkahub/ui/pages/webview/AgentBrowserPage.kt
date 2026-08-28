package me.rerere.rikkahub.ui.pages.webview

import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Stop
import me.rerere.rikkahub.data.ai.browser.AgentBrowserSession
import me.rerere.rikkahub.ui.components.nav.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentBrowserPage() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snapshot by AgentBrowserSession.snapshots.collectAsState()
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(snapshot.displayUrl) }
    var actionPending by remember { mutableStateOf(false) }
    LaunchedEffect(context.applicationContext) { AgentBrowserSession.initialize(context.applicationContext) }
    LaunchedEffect(snapshot.displayUrl) { address = snapshot.displayUrl }
    fun launchAction(block: () -> Unit) {
        if (actionPending) return
        actionPending = true
        scope.launch { try { withContext(Dispatchers.IO) { block() } } finally { actionPending = false } }
    }
    fun navigate() {
        val target = address.trim()
        if (target.isBlank()) return
        focusManager.clearFocus()
        launchAction { AgentBrowserSession.navigateFromUser(context.applicationContext, target) }
    }
    Scaffold(
        topBar = { TopAppBar(
            navigationIcon = { BackButton() },
            title = { OutlinedTextField(
                value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, placeholder = { Text("URL or domain") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate() }),
            ) },
        ) },
        bottomBar = { BottomAppBar {
            IconButton(enabled = snapshot.canGoBack && !actionPending,
                onClick = { launchAction { AgentBrowserSession.goBackFromUser() } }) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "Back")
            }
            IconButton(enabled = snapshot.canGoForward && !actionPending,
                onClick = { launchAction { AgentBrowserSession.goForwardFromUser() } }) {
                Icon(HugeIcons.ArrowRight01, contentDescription = "Forward")
            }
            IconButton(enabled = snapshot.available && !actionPending, onClick = {
                if (snapshot.isLoading) scope.launch(Dispatchers.IO) { AgentBrowserSession.stopFromUser() }
                else launchAction { AgentBrowserSession.reloadFromUser() }
            }) { Icon(if (snapshot.isLoading) HugeIcons.Stop else HugeIcons.Refresh01, contentDescription = "Reload") }
            Spacer(Modifier.weight(1f))
            Text(snapshot.host.ifBlank { if (snapshot.available) "Page" else "No page" },
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp))
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (snapshot.available) BrowserSessionHost(Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().padding(24.dp)) { Text("The shared Agent browser has no open page.") }
            if (snapshot.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BrowserSessionHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) { FrameLayout(context) }
    DisposableEffect(container, context) {
        AgentBrowserSession.attachTo(container, context)
        onDispose { AgentBrowserSession.detachFrom(container) }
    }
    AndroidView(factory = { container }, modifier = modifier)
}
