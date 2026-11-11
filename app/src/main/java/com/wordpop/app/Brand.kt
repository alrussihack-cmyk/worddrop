package com.wordpop.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- WordPop identity v2: fonts, hard shadow, mascot ----------

/** Rounded display face for the word and headings. */
val Display = FontFamily(Font(R.font.lalezar, FontWeight.Normal))

/** Body face. */
val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_bold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
    Font(R.font.readex_bold, FontWeight.ExtraBold)
)

fun brandTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Readex),
        displayMedium = t.displayMedium.copy(fontFamily = Readex),
        displaySmall = t.displaySmall.copy(fontFamily = Readex),
        headlineLarge = t.headlineLarge.copy(fontFamily = Readex),
        headlineMedium = t.headlineMedium.copy(fontFamily = Readex),
        headlineSmall = t.headlineSmall.copy(fontFamily = Readex),
        titleLarge = t.titleLarge.copy(fontFamily = Readex),
        titleMedium = t.titleMedium.copy(fontFamily = Readex),
        titleSmall = t.titleSmall.copy(fontFamily = Readex),
        bodyLarge = t.bodyLarge.copy(fontFamily = Readex),
        bodyMedium = t.bodyMedium.copy(fontFamily = Readex),
        bodySmall = t.bodySmall.copy(fontFamily = Readex),
        labelLarge = t.labelLarge.copy(fontFamily = Readex),
        labelMedium = t.labelMedium.copy(fontFamily = Readex),
        labelSmall = t.labelSmall.copy(fontFamily = Readex)
    )
}

/** Solid ink shadow under a panel or button, like a sticker. Draws outside the bounds. */
fun Modifier.hardShadow(shape: Shape, depth: Dp = 4.dp): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(top = depth.toPx()) { drawOutline(outline, Night) }
}

enum class Mood { Happy, Cheer, Think, Wow, Sleep }

/** Boby, the mascot. */
@Composable
fun Boby(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val res = when (mood) {
        Mood.Happy -> R.drawable.ic_boby_happy
        Mood.Cheer -> R.drawable.ic_boby_cheer
        Mood.Think -> R.drawable.ic_boby_think
        Mood.Wow -> R.drawable.ic_boby_wow
        Mood.Sleep -> R.drawable.ic_boby_sleep
    }
    Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
}

/** Gentle floating motion for the mascot. Pass `enabled = false` to keep it still. */
fun Modifier.bob(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    val transition = rememberInfiniteTransition(label = "bobyBob")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobyLift"
    )
    this.graphicsLayer { translationY = lift.dp.toPx() }
}

/**
 * A segmented switch with a mint pill that slides to the chosen item.
 * The first label sits at the start edge, so it is the right-most one in Arabic.
 */
@Composable
fun StepTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val frame = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(frame)
            .background(Color(0xFFEEF4F5))
            .border(1.5.dp, Line, frame)
            .padding(4.dp)
    ) {
        val itemWidth = maxWidth / labels.size
        val offsetX by animateDpAsState(
            targetValue = itemWidth * selected,
            animationSpec = if (animate) spring(dampingRatio = 0.7f, stiffness = 420f) else tween(0),
            label = "stepTabOffset"
        )
        Box(
            Modifier
                .offset(x = offsetX)
                .width(itemWidth)
                .height(42.dp)
                .clip(pill)
                .background(Teal)
                .border(2.dp, Night, pill)
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(pill)
                        .clickable(role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = Night,
                        fontSize = 13.sp,
                        maxLines = 1,
                        fontWeight = if (index == selected) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
