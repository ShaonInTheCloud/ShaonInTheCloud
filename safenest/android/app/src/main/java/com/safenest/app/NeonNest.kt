package com.safenest.app

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** SafeNest "Neon Nest": glossy hot pink and solar orange on night plum, with neon glow (same as the iPhone app). */
internal object Neon {
    val Night = Color(0xFF14030F)
    val Plum = Color(0xFF22061A)
    val Raised = Color(0xFF2E0A24)
    val Glass = Color(0xE61E0517)
    val Pink = Color(0xFFFF1E8A)
    val PinkSoft = Color(0xFFFF6FB4)
    val Orange = Color(0xFFFF8A1F)
    val Magenta = Color(0xFFFF5CE1)
    val Mint = Color(0xFF5CF2B2)
    val Text = Color(0xFFFFF3F9)
    val Muted = Color(0xFFC7A3B9)
    val Edge = Color(0x40FF5CE1)
    val gloss = Brush.linearGradient(listOf(Color(0xFFFF3D9A), Pink, Orange))
}

/** True when Android's "Remove animations" accessibility setting is on. */
@Composable
internal fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false }
    }
}

/** Slow aurora of pink, orange and magenta light behind every screen. */
@Composable
internal fun NeonAurora(animate: Boolean, modifier: Modifier = Modifier) {
    val still = !animate || reducedMotion()
    val t = if (still) 0.25f else rememberInfiniteTransition(label = "aurora").animateFloat(0f, 1f,
        infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "drift").value
    Canvas(modifier.background(Neon.Night)) {
        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) =
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        val w = size.width; val h = size.height
        blob(Neon.Pink, w * (0.05f + 0.25f * t), h * (0.08f + 0.08f * t), w * 0.95f, 0.42f)
        blob(Neon.Orange, w * (1.0f - 0.25f * t), h * (0.48f - 0.1f * t), w * 0.8f, 0.22f)
        blob(Neon.Magenta, w * (0.1f + 0.2f * t), h * (0.92f - 0.1f * t), w * 0.75f, 0.18f)
    }
}

/**
 * The Nest Orb: glossy pink-to-orange core inside two counter-rotating neon rings.
 * Breathes while protection is on; dims to plum when it is off.
 */
@Composable
internal fun NestOrb(active: Boolean, size: Dp = 210.dp, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val spinT = rememberInfiniteTransition(label = "orb")
    val spin = if (still) 0f else spinT.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin").value
    val breath = if (still || !active) 0f else spinT.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath").value
    val ringA = if (active) Neon.Magenta else Color(0xFF6B4A62)
    val ringB = if (active) Neon.Orange else Color(0xFF4F3049)
    val core = if (active) listOf(Color(0xFFFFB3D6), Neon.Pink, Neon.Orange) else listOf(Color(0xFF7A5A70), Color(0xFF4A2440), Neon.Raised)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2); val r = this.size.minDimension / 2
            val haloAlpha = if (active) 0.45f + 0.35f * breath else 0.18f
            drawCircle(Brush.radialGradient(listOf((if (active) Neon.Pink else Color(0xFF4A2440)).copy(alpha = haloAlpha), Color.Transparent), c, r), r, c)
        }
        Canvas(Modifier.fillMaxSize().rotate(spin)) {
            val r = this.size.minDimension / 2 * 0.86f
            drawCircle(Brush.sweepGradient(listOf(ringA, ringA.copy(alpha = 0.05f), ringA)), r, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 1.6f, r * 0.5f))))
        }
        Canvas(Modifier.fillMaxSize().rotate(-spin)) {
            val r = this.size.minDimension / 2 * 0.74f
            drawCircle(ringB.copy(alpha = 0.8f), r, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.22f, r * 0.16f))))
        }
        Canvas(Modifier.fillMaxSize(0.56f).scale(1f + 0.045f * breath)) {
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(core, Offset(r * 0.7f, r * 0.6f), r * 1.5f), r)
            drawOval(Color.White.copy(alpha = if (active) 0.55f else 0.2f), Offset(r * 0.45f, r * 0.18f), Size(r * 0.8f, r * 0.42f))
        }
    }
}

/** Glossy candy surface for the one thing that matters most on a screen. */
@Composable
internal fun GlossCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = Neon.Pink, spotColor = Neon.Pink)
        .clip(RoundedCornerShape(26.dp)).background(Neon.gloss)) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.45f to Color.Transparent)))
        Column(Modifier.padding(22.dp), content = content)
    }
}

/** Big tabular status line under the orb. */
@Composable
internal fun OrbStatus(text: String, good: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(50)).background((if (good) Neon.Mint else Neon.Muted).copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, color = if (good) Neon.Mint else Neon.Muted)
    }
}
