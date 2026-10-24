package me.magnum.melonds.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.RequestInitialFocus
import me.magnum.melonds.ui.common.WatermelonCard
import me.magnum.melonds.ui.common.WatermelonMark
import me.magnum.melonds.ui.common.WideMelonWordmark
import me.magnum.melonds.ui.theme.SpaceGrotesk
import me.magnum.melonds.ui.theme.watermelon

@Composable
fun WelcomeScreen(hasSecondScreen: Boolean, onDone: () -> Unit) {
    val colors = watermelon
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            WatermelonMark(width = 96.dp)
            Spacer(Modifier.height(14.dp))
            WideMelonWordmark(color = colors.text, accent = colors.green, fontSize = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.welcome_tagline),
                color = colors.text2,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WelcomeItem(
                    icon = R.drawable.ic_widescreen,
                    title = stringResource(R.string.welcome_widescreen_title),
                    text = stringResource(R.string.welcome_widescreen_text),
                )
                if (hasSecondScreen) {
                    WelcomeItem(
                        icon = R.drawable.ic_video,
                        title = stringResource(R.string.welcome_dual_screen_title),
                        text = stringResource(R.string.welcome_dual_screen_text),
                    )
                } else {
                    WelcomeItem(
                        icon = R.drawable.button_bottom_screen_shown,
                        title = stringResource(R.string.welcome_bottom_screen_title),
                        text = stringResource(R.string.welcome_bottom_screen_text),
                        tintIcon = false,
                    )
                }
                WelcomeItem(
                    icon = R.drawable.ic_settings,
                    title = stringResource(R.string.welcome_settings_title),
                    text = stringResource(R.string.welcome_settings_text),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.widescreen_compatibility_note) + "\n\n" + stringResource(R.string.widescreen_photosensitivity_note),
                color = colors.text3,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            val focusRequester = remember { FocusRequester() }
            RequestInitialFocus(focusRequester)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.red)
                    .focusRequester(focusRequester)
                    .clickable(onClick = onDone),
            ) {
                Text(
                    text = stringResource(R.string.welcome_start),
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun WelcomeItem(icon: Int, title: String, text: String, tintIcon: Boolean = true) {
    val colors = watermelon
    WatermelonCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = if (tintIcon) ColorFilter.tint(colors.green) else null,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = text,
                    color = colors.text2,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
