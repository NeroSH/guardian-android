/*
 * Copyright 2026 NeroSH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shdev.guardian.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shdev.guardian.R
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-app splash that continues the cold-start system splash (see Theme.Splash / avd_splash_rotate)
 * with native Compose animation: the shield logo springs in and fades up while an accent ring keeps
 * spinning, so the handoff from the window splash is seamless. Shown until the role read resolves,
 * then [OnboardingNavHost] crossfades it out into the real content.
 */
@Composable
internal fun GuardianSplash(modifier: Modifier = Modifier) {
    // Drive the entrance once on first composition (spring scale + fade).
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200.milliseconds)
        entered = true
    }
    val transition = updateTransition(entered, label = "splashEnter")

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 500) },
        label = "alpha",
    ) { if (it) 1f else 0f }

    // Ring spins forever, matching the window-splash AVD rotation.
    val infinite = rememberInfiniteTransition(label = "splashRing")
    val ringAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "ringAngle",
    )

    val logo: Painter = painterResource(R.drawable.ic_launcher_foreground)
    val background = colorResource(R.color.splash_background)
    val accentColor = colorResource(R.color.splash_accent)

    Box(
        modifier = modifier
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        // Spinning accent ring behind the logo (gapped arc, so rotation reads).
        Canvas(
            modifier = Modifier
                .size(250.dp)
                .graphicsLayer { rotationZ = ringAngle; this.alpha = alpha },
        ) {
            drawArc(
                color = accentColor,
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = this.size,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Image(
            painter = logo,
            contentDescription = null,
            modifier = Modifier
                .size(288.dp)
//                .graphicsLayer {
//                    scaleX = scale
//                    scaleY = scale
//                    this.alpha = alpha
//                },
        )
    }
}
