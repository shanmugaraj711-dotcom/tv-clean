package ai.promptstudio.tvclean.ui.design.component

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import ai.promptstudio.tvclean.ui.design.R
import ai.promptstudio.tvclean.ui.design.theme.CytMotion
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// DESIGN-SYSTEM 8.5. One undo message at a time; a new one replaces the old. 5 s, 10 s with
// TalkBack on. Never used for errors that need a decision.
// look2: `action` false is a message with no button ("Copied."); `onGone` runs once when the
// message leaves (timed out, its action tapped, dismissed or replaced by a newer one).
@Immutable
class UndoMessage(val id: Long, val message: String, val actionLabel: String?, val onAction: () -> Unit, val action: Boolean = true,
    val onGone: () -> Unit = {})

@Stable
class UndoSnackbarState {
    var current: UndoMessage? by mutableStateOf(null)
        private set
    private var next = 0L

    // actionLabel null uses "Undo".
    fun show(message: String, actionLabel: String? = null, onAction: () -> Unit) {
        replace(UndoMessage(++next, message, actionLabel, onAction))
    }

    // look2: a message with an optional action (null: no button) and a callback when it leaves.
    fun note(message: String, actionLabel: String?, onAction: (() -> Unit)?, onGone: () -> Unit = {}) {
        replace(UndoMessage(++next, message, actionLabel, onAction ?: {}, action = onAction != null, onGone = onGone))
    }

    fun dismiss() {
        val gone = current
        current = null
        gone?.onGone?.invoke()
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) dismiss()
    }

    private fun replace(m: UndoMessage) {
        val gone = current
        current = m
        gone?.onGone?.invoke()
    }
}

@Composable
fun rememberUndoSnackbarState(): UndoSnackbarState = remember { UndoSnackbarState() }

// Place at the bottom of the screen; it sits 16 dp above whatever is below it (pass the primary
// action's height plus 16 dp as bottom padding when a primary is shown).
@Composable
fun UndoSnackbarHost(state: UndoSnackbarState, modifier: Modifier = Modifier) {
    val msg = state.current
    val context = LocalContext.current
    val reduced = CytTheme.reducedMotion
    if (msg != null) {
        LaunchedEffect(msg.id) {
            delay(if (context.touchExplorationOn()) 10_000 else 5_000)
            state.dismiss(msg.id)
        }
    }
    var last by remember { mutableStateOf<UndoMessage?>(null) }
    if (msg != null) last = msg
    val slide = with(LocalDensity.current) { 16.dp.roundToPx() }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = if (reduced) fadeIn(tween(CytMotion.REDUCED_FADE_MS)) else slideInVertically(tween(CytMotion.SNACKBAR_IN_MS)) { slide } + fadeIn(tween(CytMotion.SNACKBAR_IN_MS)),
            exit = fadeOut(tween(CytMotion.SNACKBAR_OUT_MS)),
        ) {
            val shown = last ?: return@AnimatedVisibility
            UndoSnackbar(shown.message, onAction = { shown.onAction(); state.dismiss(shown.id) }, actionLabel = shown.actionLabel ?: stringResource(R.string.tvclean_undo),
                showAction = shown.action)
        }
    }
}

// The pill itself: surface.3, rim, the one soft shadow on dark (it floats over content), 52 dp,
// body.medium message and the accent label.medium action.
@Composable
fun UndoSnackbar(message: String, onAction: () -> Unit, modifier: Modifier = Modifier, actionLabel: String = stringResource(R.string.tvclean_undo),
    showAction: Boolean = true) {
    val c = CytTheme.colors
    // A pill at its one-line height (half of 52 dp), and still a rounded box when a long message
    // wraps at a big font size, so its text is never cut off by the round ends.
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .background(c.surface3, shape).border(1.dp, c.rim, shape).clip(shape)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 0.dp).padding(end = if (showAction) 0.dp else 14.dp), color = c.textPrimary,
            style = CytTheme.type.bodyMedium)
        if (showAction) Box(
            Modifier.heightIn(min = 48.dp).clip(CytTheme.shapes.pill).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(actionLabel, color = c.accent, style = CytTheme.type.labelMedium)
        }
    }
}

private fun Context.touchExplorationOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
