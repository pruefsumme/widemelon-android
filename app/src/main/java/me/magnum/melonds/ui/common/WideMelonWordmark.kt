package me.magnum.melonds.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.SpaceGrotesk

/**
 * "Wide" in the text colour followed by "Melon" in green, the same everywhere the name is shown.
 */
@Composable
fun WideMelonWordmark(
    color: Color,
    accent: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = (-0.3).sp,
) {
    Row(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_brand_wide),
            color = color,
            fontFamily = SpaceGrotesk,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = letterSpacing,
        )
        Text(
            text = stringResource(R.string.app_brand_melon),
            color = accent,
            fontFamily = SpaceGrotesk,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = letterSpacing,
        )
    }
}
