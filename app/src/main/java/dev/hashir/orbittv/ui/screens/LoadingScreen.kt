package dev.hashir.orbittv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.AccentSoft
import dev.hashir.orbittv.ui.theme.Ink
import dev.hashir.orbittv.ui.theme.TextSecondary

/**
 * Cold-start screen. Doubles as the error screen: pass a negative [fraction]
 * and an [onRetry] and it turns into a retry prompt.
 */
@Composable
fun LoadingScreen(
    message: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val failed = fraction < 0f
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .scale(if (failed) 1f else scale)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Accent, AccentSoft))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.LiveTv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            Text(
                text = "Orbit TV",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 20.dp),
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (!failed) {
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.08f),
                )
            } else if (onRetry != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 26.dp, vertical = 11.dp),
                ) {
                    Text("Try again", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}
