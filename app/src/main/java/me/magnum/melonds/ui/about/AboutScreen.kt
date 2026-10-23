package me.magnum.melonds.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R
import me.magnum.melonds.ui.common.WatermelonCard
import me.magnum.melonds.ui.common.WatermelonMark
import me.magnum.melonds.ui.common.WatermelonRowSeparator
import me.magnum.melonds.ui.common.WatermelonScreenScaffold
import me.magnum.melonds.ui.common.WatermelonSectionLabel
import me.magnum.melonds.ui.common.WideMelonWordmark
import me.magnum.melonds.ui.theme.SpaceGrotesk
import me.magnum.melonds.ui.theme.watermelon

private const val REPO_URL = "https://github.com/pruefsumme/widemelon-android"

private data class Credit(val name: String, val by: String, val url: String)

private val credits = listOf(
    Credit("melonDS", "Arisotura and contributors", "https://github.com/melonDS-emu/melonDS"),
    Credit("melonDS Android", "rafaelvcaetano", "https://github.com/rafaelvcaetano/melonDS-android"),
    Credit("WatermelonDS", "SapphireRhodonite", "https://github.com/SapphireRhodonite/WatermelonDS"),
    Credit("librashader", "SnowflakePowered", "https://github.com/SnowflakePowered/librashader"),
    Credit("rcheevos", "RetroAchievements", "https://github.com/RetroAchievements/rcheevos"),
)

@Composable
fun AboutScreen(version: String, onOpenLink: (String) -> Unit, onShowWelcome: () -> Unit, onBack: () -> Unit) {
    val colors = watermelon
    WatermelonScreenScaffold(title = stringResource(R.string.about_widemelon), onBack = onBack) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            WatermelonMark(width = 88.dp)
            Spacer(Modifier.height(12.dp))
            WideMelonWordmark(color = colors.text, accent = colors.green, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version, version),
                color = colors.text3,
                fontSize = 13.sp,
            )

            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                WatermelonSectionLabel(stringResource(R.string.about_links))
                WatermelonCard {
                    AboutRow(stringResource(R.string.about_source_code), "github.com/pruefsumme/widemelon-android") { onOpenLink(REPO_URL) }
                    WatermelonRowSeparator()
                    AboutRow(stringResource(R.string.about_report_problem), null) { onOpenLink("$REPO_URL/issues") }
                    WatermelonRowSeparator()
                    AboutRow(stringResource(R.string.about_desktop_version), "github.com/pruefsumme/widemelon") {
                        onOpenLink("https://github.com/pruefsumme/widemelon")
                    }
                    WatermelonRowSeparator()
                    AboutRow(stringResource(R.string.about_show_welcome), null, onClick = onShowWelcome)
                }

                WatermelonSectionLabel(stringResource(R.string.about_built_on))
                WatermelonCard {
                    credits.forEachIndexed { index, credit ->
                        if (index > 0) WatermelonRowSeparator()
                        AboutRow(credit.name, credit.by) { onOpenLink(credit.url) }
                    }
                }

                WatermelonSectionLabel(stringResource(R.string.about_license))
                WatermelonCard {
                    AboutRow(stringResource(R.string.about_license_gpl), stringResource(R.string.about_license_summary)) {
                        onOpenLink("$REPO_URL/blob/main/LICENSE")
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.about_trademark_notice),
                    color = colors.text3,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AboutRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(text = subtitle, color = colors.text3, fontSize = 12.sp)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.size(20.dp),
        )
    }
}
