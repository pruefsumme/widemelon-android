package me.magnum.melonds.ui.common

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.watermelon
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class BrandIntroTarget {
    var bounds by mutableStateOf(Rect.Zero)
}

val LocalBrandIntroTarget = staticCompositionLocalOf<BrandIntroTarget?> { null }

private const val INTRO_DURATION_MS = 1300

// The melon starts squeezed into a 4:3 box, the same shape as a DS screen
private const val NARROW_SCALE = 0.73f

private const val TILE_RADIUS = .62f

private val LightTile = Color(0xFFF6F1E7)
private val DarkTile = Color(0xFF1E1D24)

/**
 * Opening animation: the logo starts at 4:3 between two black bars, gets stretched to its real width while the bars
 * are pushed out, then flies into the header logo. Tapping or pressing back skips it.
 */
@Composable
fun WideMelonLaunchIntro(startedAtMillis: Long?, darkIcon: Boolean, onFinished: () -> Unit, content: @Composable () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(false) }
    val running = startedAtMillis != null && !finished
    val finish = {
        finished = true
        onFinished()
    }
    val target = remember { BrandIntroTarget() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBrandIntroTarget provides if (running) target else null) {
            content()
        }
        if (running) {
            // The splash screen already used up part of the time, so pick up where it left off
            val remaining = remember(startedAtMillis) {
                (INTRO_DURATION_MS - (SystemClock.uptimeMillis() - requireNotNull(startedAtMillis))).coerceIn(0L, INTRO_DURATION_MS.toLong()).toInt()
            }
            val progress = remember { Animatable(1f - remaining.toFloat() / INTRO_DURATION_MS) }
            LaunchedEffect(Unit) {
                progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                finish()
            }
            BackHandler(onBack = finish)

            val logo = ImageBitmap.imageResource(R.drawable.widemelon_logo)
            val logoAspect = logo.height.toFloat() / logo.width
            val background = watermelon.bg
            val barColor = watermelon.text
            val tileColor = if (darkIcon) DarkTile else LightTile

            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { finish() }
            }) {
                val t = progress.value
                val travel = FastOutSlowInEasing.transform(t.phase(.62f, 1f))
                drawRect(background.copy(alpha = 1f - FastOutSlowInEasing.transform(t.phase(.55f, .94f))))

                // Everything below is drawn with the logo one unit wide
                val startWidth = minOf(220.dp.toPx(), size.minDimension * .6f)
                val hasTarget = target.bounds != Rect.Zero
                val endWidth = if (hasTarget) target.bounds.width else startWidth
                val width = startWidth + (endWidth - startWidth) * travel
                val start = Offset((size.width - startWidth) / 2, (size.height - startWidth * logoAspect) / 2)
                val end = if (hasTarget) target.bounds.topLeft else start
                val origin = start + (end - start) * travel

                translate(origin.x, origin.y) {
                    scale(width, width, Offset.Zero) {
                        drawStretch(logo, logoAspect, tileColor, barColor, t)
                    }
                }
            }
        }
    }
}

private fun Float.phase(start: Float, end: Float) = ((this - start) / (end - start)).coerceIn(0f, 1f)

// Slight overshoot so the stretch feels springy rather than linear
private fun easeOutBack(x: Float): Float {
    val c1 = 1.4f
    val c3 = c1 + 1f
    val p = x - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

private fun DrawScope.drawStretch(logo: ImageBitmap, logoAspect: Float, tileColor: Color, barColor: Color, t: Float) {
    val centre = Offset(.5f, logoAspect / 2f)
    val stretch = easeOutBack(t.phase(.12f, .5f))
    val widthScale = NARROW_SCALE + (1f - NARROW_SCALE) * stretch
    // a small squash while it widens, like it's being pulled
    val heightScale = 1f - .08f * sin(PI.toFloat() * t.phase(.12f, .5f))

    // icon tile behind the melon, fades as the logo heads for the header
    val tileAlpha = 1f - t.phase(.45f, .7f)
    if (tileAlpha > 0f) {
        drawCircle(tileColor, radius = TILE_RADIUS, center = centre, alpha = tileAlpha)
    }

    // the 4:3 "black bars", pushed outwards by the melon and gone by the end of the stretch. Clipped to the tile so
    // they slide off its edge instead of poking out past it
    val barAlpha = (1f - t.phase(.3f, .55f)) * .85f
    if (barAlpha > 0f) {
        val barWidth = .07f
        val barHeight = logoAspect * 1.1f
        val gap = widthScale / 2f + .02f
        val tile = Path().apply { addOval(Rect(centre, TILE_RADIUS)) }
        clipPath(tile) {
            repeat(2) { i ->
                val x = if (i == 0) centre.x - gap - barWidth else centre.x + gap
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, centre.y - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(.02f),
                    alpha = barAlpha,
                )
            }
        }
    }

    scale(widthScale, heightScale, centre) {
        // drawn at a fixed pixel size and scaled down, otherwise the bitmap gets sampled at 1x1
        scale(1f / logo.width, 1f / logo.width, Offset.Zero) {
            drawImage(
                logo,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(logo.width, logo.height),
                dstSize = IntSize(logo.width, (logo.width * logoAspect).roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }
}
