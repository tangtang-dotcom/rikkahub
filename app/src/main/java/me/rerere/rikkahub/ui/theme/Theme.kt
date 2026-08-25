package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit,
) {
    val settings by rememberUserSettingsState()

    val darkTheme =
        when (colorMode) {
            ColorMode.SYSTEM -> isSystemInDarkTheme()
            ColorMode.LIGHT -> false
            ColorMode.DARK -> true
        }
    val amoledDarkMode by rememberAmoledDarkMode()

    val colorScheme =
        when {
            settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            else -> {
                val theme =
                    findThemeById(settings.themeId, settings.customThemes)
                        ?: findPresetTheme(settings.themeId)
                theme.getColorScheme(dark = darkTheme)
            }
        }
    val colorSchemeConverted =
        remember(darkTheme, amoledDarkMode, colorScheme) {
            if (darkTheme && amoledDarkMode) {
                colorScheme.copy(
                    background = AMOLED_DARK_BACKGROUND,
                    surface = AMOLED_DARK_BACKGROUND,
                )
            } else {
                colorScheme
            }
        }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    // Keep system-bar icon contrast tied to the app theme, rather than the system theme
    // sampled once by enableEdgeToEdge(). Some OEMs reset these flags when a dialog,
    // permission screen, or another activity gives the window focus back. Use decorView
    // (the actual system-bar host) and restore the flags on every focus regain.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as Activity
        val window = activity.window
        val decorView = window.decorView
        val applySystemBarAppearance = {
            WindowCompat.getInsetsController(window, decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }

        SideEffect(applySystemBarAppearance)
        DisposableEffect(window, decorView, darkTheme) {
            val deferredApply = Runnable { applySystemBarAppearance() }
            val scheduleSystemBarAppearance = {
                // ColorOS reapplies its own icon mode after the first focus callback while a
                // mini-window is expanding. Cover both the next traversal and the end of the
                // bounds animation; repeated layout callbacks coalesce the delayed restore.
                decorView.removeCallbacks(deferredApply)
                applySystemBarAppearance()
                decorView.postOnAnimation(deferredApply)
                decorView.postDelayed(deferredApply, 500L)
            }
            val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) scheduleSystemBarAppearance()
            }
            val layoutListener = View.OnLayoutChangeListener {
                    _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    scheduleSystemBarAppearance()
                }
            }
            decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            decorView.addOnLayoutChangeListener(layoutListener)
            onDispose {
                decorView.removeCallbacks(deferredApply)
                decorView.removeOnLayoutChangeListener(layoutListener)
                if (decorView.viewTreeObserver.isAlive) {
                    decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive(),
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
