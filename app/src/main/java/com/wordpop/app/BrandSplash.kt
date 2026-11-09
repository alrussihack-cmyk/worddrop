package com.wordpop.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Short branded intro shown on a cold launch.
 * Uses the same Boby mascot, colors and display font as the main app.
 */
@Composable
fun WordPopStartup(onFinished: () -> Unit) {
    var brandVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(140)
        brandVisible = true
        delay(2_160)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "bobyFloat")
    val floatY by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobyFloatY"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobyPulse"
    )
    val entranceScale by animateFloatAsState(
        targetValue = if (brandVisible) 1f else 0.68f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 280f),
        label = "bobyEntrance"
    )
    val entranceRotation by animateFloatAsState(
        targetValue = if (brandVisible) 0f else -12f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "bobyRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Cloud),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Mint.copy(alpha = 0.12f),
                radius = size.minDimension * 0.34f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.43f)
            )
            drawCircle(
                color = Spark.copy(alpha = 0.9f),
                radius = 7.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.31f)
            )
            drawCircle(
                color = Sky.copy(alpha = 0.9f),
                radius = 5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.79f, size.height * 0.55f)
            )
            drawCircle(
                color = Coral.copy(alpha = 0.85f),
                radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.23f, size.height * 0.61f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(224.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Boby(
                    mood = Mood.Cheer,
                    size = 166.dp,
                    modifier = Modifier.graphicsLayer {
                        translationY = floatY.dp.toPx()
                        scaleX = entranceScale * pulse
                        scaleY = entranceScale * pulse
                        rotationZ = entranceRotation
                    }
                )
            }

            Spacer(Modifier.height(26.dp))

            AnimatedVisibility(
                visible = brandVisible,
                enter = fadeIn(tween(520)) + expandVertically(tween(520))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Ltr {
                        Text(
                            text = "WordPop",
                            fontFamily = Display,
                            fontSize = 43.sp,
                            fontWeight = FontWeight.Normal,
                            color = Night,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "كلمة كل شوية، وبوبي معاك لحد ما تثبت.",
                        fontSize = 15.sp,
                        color = Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
