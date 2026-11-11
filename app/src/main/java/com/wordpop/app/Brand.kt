package com.wordpop.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- WordPop identity v3: clean mint palette, rounded typography, mascot ----------

/** Arabic/English UI face used throughout the refreshed identity. */
val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_bold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
    Font(R.font.readex_bold, FontWeight.ExtraBold)
)

/** Headings use the same clear rounded family; keep Lalezar for the expressive wordmark only. */
val Display = FontFamily(Font(R.font.lalezar, FontWeight.Normal))
val Wordmark = FontFamily(Font(R.font.lalezar, FontWeight.Normal))

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
