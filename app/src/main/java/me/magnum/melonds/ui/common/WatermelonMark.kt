package me.magnum.melonds.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import kotlin.math.roundToInt

@Composable
fun WatermelonMark(modifier: Modifier = Modifier, width: Dp = 38.dp) {
    val image = ImageBitmap.imageResource(R.drawable.widemelon_logo)
    Canvas(modifier.size(width, width * image.height / image.width)) {
        drawImage(
            image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
