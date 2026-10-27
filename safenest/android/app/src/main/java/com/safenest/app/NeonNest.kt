package com.safenest.app

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SafeNest "Solid Neon" (same as the iPhone app): flat solid hot pink, solid orange and neon magenta on deep plum.
 * No gradients, no blurred glow.
 */
internal object Neon {
    val Night = Color(0xFF0E0110)
    val Plum = Color(0xFF1C0418)
    val Raised = Color(0xFF2A0824)
    val Glass = Color(0xFF1C0418)
    val Pink = Color(0xFFFF1F8F)
    val PinkSoft = Color(0xFFFF5CAE)
    val Orange = Color(0xFFFF7A00)
    val Magenta = Color(0xFFFF2EE6)
    val Mint = Color(0xFF5CF2B2)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFC9A7BF)
    val Edge = Color(0xFF3A0E33)
}

/** Revolut-style type: Inter for text, Inter Tight for bold headings. Bangla falls back to the system Bangla font. */
internal val NeonFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_tight_bold, FontWeight.Bold),
    Font(R.font.inter_tight_extrabold, FontWeight.ExtraBold),
)
internal val NeonTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = NeonFont), displayMedium = t.displayMedium.copy(fontFamily = NeonFont),
        displaySmall = t.displaySmall.copy(fontFamily = NeonFont), headlineLarge = t.headlineLarge.copy(fontFamily = NeonFont),
        headlineMedium = t.headlineMedium.copy(fontFamily = NeonFont), headlineSmall = t.headlineSmall.copy(fontFamily = NeonFont),
        titleLarge = t.titleLarge.copy(fontFamily = NeonFont), titleMedium = t.titleMedium.copy(fontFamily = NeonFont),
        titleSmall = t.titleSmall.copy(fontFamily = NeonFont), bodyLarge = t.bodyLarge.copy(fontFamily = NeonFont),
        bodyMedium = t.bodyMedium.copy(fontFamily = NeonFont), bodySmall = t.bodySmall.copy(fontFamily = NeonFont),
        labelLarge = t.labelLarge.copy(fontFamily = NeonFont), labelMedium = t.labelMedium.copy(fontFamily = NeonFont),
        labelSmall = t.labelSmall.copy(fontFamily = NeonFont),
    )
}

/** True when Android's "Remove animations" accessibility setting is on. */
@Composable
internal fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false }
    }
}

/** Background: one flat solid colour (kept as a function so callers stay unchanged). */
@Composable
internal fun NeonAurora(@Suppress("UNUSED_PARAMETER") animate: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.background(Neon.Night))
}

/**
 * The Nest Orb, flat: solid hot-pink core with one gloss highlight, a solid orange segmented ring and a neon ring
 * turning in opposite directions. The core pulses while protection is on; everything turns plum when off.
 */
@Composable
internal fun NestOrb(active: Boolean, size: Dp = 210.dp, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val transition = rememberInfiniteTransition(label = "orb")
    val spin = if (still) 0f else transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "spin").value
    val pulse = if (still || !active) 0f else transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse").value
    val neon = if (active) Neon.Magenta else Color(0xFF4A2342)
    val orange = if (active) Neon.Orange else Color(0xFF5A2A50)
    val core = if (active) Neon.Pink else Color(0xFF3A1033)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(spin)) {
            val r = this.size.minDimension / 2 * 0.92f
            drawCircle(neon, r, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 2.2f, r * 0.7f))))
        }
        Canvas(Modifier.fillMaxSize().rotate(-spin)) {
            val r = this.size.minDimension / 2 * 0.78f
            drawCircle(orange, r, style = Stroke(8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.34f, r * 0.16f))))
        }
        Canvas(Modifier.fillMaxSize(0.56f).scale(1f + 0.06f * pulse)) {
            val r = this.size.minDimension / 2
            drawCircle(core, r)
            drawOval(Color.White.copy(alpha = if (active) 0.9f else 0.25f), Offset(r * 0.5f, r * 0.34f), Size(r * 0.56f, r * 0.32f))
        }
    }
}

/** Solid hot-pink block for the one thing that matters most on a screen. */
@Composable
internal fun GlossCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Neon.Pink).padding(22.dp), content = content)
}

/** Status chip under the orb. */
@Composable
internal fun OrbStatus(text: String, good: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Neon.Raised).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, color = if (good) Neon.Mint else Neon.Muted)
    }
}
