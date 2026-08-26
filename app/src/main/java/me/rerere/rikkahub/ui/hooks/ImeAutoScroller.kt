package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ImeLazyListAutoScroller(lazyListState: LazyListState) {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    var previousImeBottom by remember { mutableIntStateOf(0) }

    LaunchedEffect(lazyListState, density) {
        snapshotFlow { ime.getBottom(density) }
            .distinctUntilChanged()
            .collect { imeBottom ->
                val delta = imeBottom - previousImeBottom
                previousImeBottom = imeBottom
                if (delta == 0 || !lazyListState.canScrollForward) return@collect
                // Apply the inset delta directly. Avoid the old delay and 1/-1 nudge,
                // which caused extra measure/layout passes and jank while the IME closed.
                lazyListState.scrollBy(delta.toFloat())
            }
    }
}
