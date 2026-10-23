package me.magnum.melonds.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.romlist.composables.DsBoxArtAspectRatio
import me.magnum.melonds.ui.romlist.composables.ScanlinesOverlay
import me.magnum.melonds.ui.romlist.composables.formatHoursLabel
import me.magnum.melonds.ui.romlist.composables.romDisplayName
import me.magnum.melonds.ui.common.achievements.ui.model.AchievementUiModel
import me.magnum.melonds.ui.emulator.ui.isUnlocked
import me.magnum.melonds.ui.romlist.composables.WatermelonRomArt
import me.magnum.melonds.ui.romlist.composables.romGradient
import me.magnum.melonds.ui.romlist.composables.romIconRequest
import me.magnum.melonds.ui.romlist.composables.romInitials
import me.magnum.melonds.ui.romlist.composables.romPlatformLabel
import me.magnum.melonds.ui.theme.SpaceGrotesk
import me.magnum.melonds.ui.theme.WatermelonMono
import kotlin.time.Duration

@Composable
fun ExternalLibraryGameInfo(
    rom: Rom,
    boxArtUrl: String?,
    raCoverUrl: String? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundUrl = boxArtUrl ?: raCoverUrl
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(romGradient(romDisplayName(rom))))
        if (backgroundUrl != null) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize().blur(18.dp),
            )
        } else {
            AsyncImage(
                model = romIconRequest(context, rom),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.4f,
                modifier = Modifier.fillMaxSize().blur(22.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.35f))),
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 30.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(DsBoxArtAspectRatio)
                    .shadow(14.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            ) {
                WatermelonRomArt(
                    rom = rom,
                    boxArtUrl = boxArtUrl,
                    raCoverUrl = raCoverUrl,
                    initialsFontSize = 44.sp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = romPlatformLabel(rom),
                            color = Color.White,
                            fontFamily = WatermelonMono,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                    if (rom.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD23F),
                            modifier = Modifier.padding(start = 9.dp).size(16.dp),
                        )
                    }
                }
                Text(
                    text = romDisplayName(rom),
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (rom.developerName.isNotBlank()) {
                    Text(
                        text = rom.developerName,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (rom.totalPlayTime != Duration.ZERO) {
                    Text(
                        text = formatHoursLabel(rom.totalPlayTime),
                        color = Color.White.copy(alpha = 0.55f),
                        fontFamily = WatermelonMono,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    ExternalButtonHint("A", stringResource(R.string.external_hint_open))
                    ExternalButtonHint("Y", stringResource(R.string.external_hint_favorite))
                }
            }
        }
    }
}

@Composable
fun ExternalBootInfo(
    rom: Rom,
    boxArtUrl: String?,
    statusText: String? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(Color(0xFF121116))) {
        Box(Modifier.fillMaxSize().background(romGradient(romDisplayName(rom))))
        if (boxArtUrl != null) {
            AsyncImage(
                model = boxArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.40f,
                modifier = Modifier.fillMaxSize().blur(22.dp),
            )
        } else {
            AsyncImage(
                model = romIconRequest(context, rom),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.38f,
                modifier = Modifier.fillMaxSize().blur(24.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.42f))),
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 30.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(DsBoxArtAspectRatio)
                    .shadow(14.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            ) {
                WatermelonRomArt(
                    rom = rom,
                    boxArtUrl = boxArtUrl,
                    raCoverUrl = null,
                    initialsFontSize = 44.sp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = romPlatformLabel(rom),
                        color = Color.White,
                        fontFamily = WatermelonMono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    )
                }
                Text(
                    text = romDisplayName(rom),
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (rom.developerName.isNotBlank()) {
                    Text(
                        text = rom.developerName,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(
                        text = (statusText?.takeIf { it.isNotBlank() } ?: "Iniciando juego").uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = WatermelonMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    ExternalLoadingDots(color = Color(0xFF6FBF4A))
                }
            }
        }
    }
}

@Composable
private fun ExternalLoadingDots(color: Color) {
    val infinite = rememberInfiniteTransition(label = "externalBootDots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)),
        label = "externalBootDotsPhase",
    )
    Row(Modifier.padding(start = 9.dp)) {
        repeat(3) { i ->
            val active = phase.toInt() % 3 == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (active) 0.95f else 0.28f)),
            )
        }
    }
}

@Composable
fun ExternalAchievementInfo(achievementModel: AchievementUiModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val achievement = achievementModel.actualAchievement()
    val unlocked = achievementModel.isUnlocked()
    val gold = me.magnum.melonds.ui.theme.WatermelonColors.gold
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(Color(0xFF121116)).padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        AsyncImage(
            model = if (unlocked) achievement.badgeUrlUnlocked.toString() else achievement.badgeUrlLocked.toString(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Text(
            text = achievement.getCleanTitle(),
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontSize = 25.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "${stringResource(if (unlocked) R.string.retro_achievements_unlocked else R.string.retro_achievements_locked)} · ${achievement.points} ${stringResource(R.string.points_abbreviated)}".uppercase(),
            color = if (unlocked) Color(0xFF6FBF4A) else Color.White.copy(alpha = 0.5f),
            fontFamily = WatermelonMono,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (achievement.description.isNotBlank()) {
            Text(
                text = achievement.description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp).width(440.dp),
            )
        }
        if (achievement.isMissable()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 14.dp),
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_status_warn),
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.retro_achievements_filter_missable).uppercase(),
                    color = gold,
                    fontFamily = WatermelonMono,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

@Composable
fun ExternalIdleInfo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 30.dp),
    ) {
        WatermelonMark(width = 84.dp)
        WideMelonWordmark(
            color = Color.White,
            accent = Color(0xFF6FBF4A),
            fontSize = 34.sp,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun ExternalCrumbInfo(
    icon: ImageVector,
    title: String,
    description: String?,
    crumb: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 30.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF6FBF4A),
            modifier = Modifier.size(46.dp),
        )
        Text(
            text = title,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (description != null) {
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp).width(400.dp),
            )
        }
        Text(
            text = crumb.uppercase(),
            color = Color.White.copy(alpha = 0.35f),
            fontFamily = WatermelonMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun ExternalSettingInfo(
    iconDrawable: android.graphics.drawable.Drawable?,
    title: String,
    description: String?,
    crumb: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 30.dp),
    ) {
        if (iconDrawable != null) {
            val painter = remember(iconDrawable) {
                androidx.compose.ui.graphics.painter.BitmapPainter(
                    iconDrawable.toBitmap(width = 96, height = 96).asImageBitmap(),
                )
            }
            Icon(
                painter = painter,
                contentDescription = null,
                tint = Color(0xFF6FBF4A),
                modifier = Modifier.size(46.dp),
            )
        } else {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Settings,
                contentDescription = null,
                tint = Color(0xFF6FBF4A),
                modifier = Modifier.size(46.dp),
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp).width(420.dp),
            )
        }
        Text(
            text = crumb.uppercase(),
            color = Color.White.copy(alpha = 0.35f),
            fontFamily = WatermelonMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun ExternalSaveStatesInfo(
    title: String,
    slots: List<me.magnum.melonds.domain.model.SaveStateSlot>,
    footer: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xF2121116)).padding(horizontal = 30.dp, vertical = 24.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            slots.filter { it.exists }.take(4).ifEmpty { slots.take(4) }.forEach { slot ->
                Column(Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(
                                width = 2.dp,
                                color = if (slot.slot == me.magnum.melonds.domain.model.SaveStateSlot.QUICK_SAVE_SLOT && slot.exists) {
                                    Color(0xFFF44336)
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(9.dp),
                            ),
                    ) {
                        if (slot.exists && slot.screenshot != null) {
                            AsyncImage(
                                model = slot.screenshot,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 7.dp, top = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (slot.slot == me.magnum.melonds.domain.model.SaveStateSlot.QUICK_SAVE_SLOT) "Q" else slot.slot.toString(),
                                color = Color.White,
                                fontFamily = WatermelonMono,
                                fontSize = 8.5.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    val whenLabel = slot.lastUsedDate?.let {
                        android.text.format.DateUtils.getRelativeTimeSpanString(it.time).toString()
                    } ?: "—"
                    Text(
                        text = whenLabel,
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = WatermelonMono,
                        fontSize = 9.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = footer.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = WatermelonMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun ExternalButtonHint(button: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = button,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = WatermelonMono,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
