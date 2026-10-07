package com.safenest.app

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Animates the approved still artwork. No video, network call or protection-state mutation. */
@Composable
internal fun SafeNestBrandIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val finish by rememberUpdatedState(onFinished)
    val motionEnabled = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
            .getOrDefault(true)
    }
    val reveal = remember { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motionEnabled) {
            reveal.animateTo(1f, tween(1250, easing = FastOutSlowInEasing))
            delay(700)
        } else delay(150)
        finish()
    }
    val progress = reveal.value
    val nameProgress = ((progress - .28f) / .72f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().background(Color(0xFF470C24)).semantics {
        contentDescription = "SafeNest"
    }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.safenest_intro_backdrop), null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.06f - progress * .06f
                scaleY = 1.06f - progress * .06f
            }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xB5280617), Color(0x335F1233), Color(0x99450B23)
        ))))
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(.94f).aspectRatio(1f).clip(RoundedCornerShape(26.dp))) {
                Image(painterResource(R.drawable.safenest_brand), null,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = .12f + .88f * progress
                        scaleX = .82f + .18f * progress
                        scaleY = .82f + .18f * progress
                        translationY = (1f - progress) * 30f * density
                        rotationY = (1f - progress) * -10f
                        cameraDistance = 18f * density
                    }, contentScale = ContentScale.Fit)
                // A soft reflection travels across the photographed metal during reveal.
                if (motionEnabled && progress < 1f) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        translationX = (-1.2f + progress * 2.4f) * 320f * density
                    }.background(Brush.horizontalGradient(listOf(
                        Color.Transparent, Color(0x26FFD0E2), Color.Transparent
                    ))))
                }
            }
            Spacer(Modifier.height(22.dp))
            Image(painterResource(R.drawable.safenest_wordmark), null,
                Modifier.fillMaxWidth(.94f).aspectRatio(880f / 190f).graphicsLayer {
                    alpha = nameProgress
                    translationY = (1f - nameProgress) * 16f * density
                }, contentScale = ContentScale.Fit)
        }
    }
}
