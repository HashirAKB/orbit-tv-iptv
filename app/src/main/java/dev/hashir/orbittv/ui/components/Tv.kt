package dev.hashir.orbittv.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** True on Fire TV / Android TV, where input is a D-pad remote rather than touch. */
fun Context.isTv(): Boolean {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTv() }
}

val FocusRingColor = Color.White

/**
 * A [clickable] that draws a clear ring while it holds D-pad focus. On touch
 * devices nothing ever takes focus, so it looks identical to a plain clickable.
 * Put it where `.clickable` was: after the clip/background of the element.
 */
fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    this
        .border(if (focused) 3.dp else 0.dp, if (focused) FocusRingColor else Color.Transparent, shape)
        .clickable(
            interactionSource = interaction,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}
