// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object StitchColors {
    val Primary = Color(0xFF5B4394)
    val PrimaryDark = Color(0xFF4D3282)
    val ActiveBorder = Color(0xFF583E8E)
    val PrimaryContainer = Color(0xFFEADBFF)
    val PrimaryTonal = Color(0xFFEFEBFA)
    val PrimaryTonalStrong = Color(0xFFECE3F8)
    val OnPrimaryContainer = Color(0xFF21005D)
    val ChipText = Color(0xFF4E397B)

    val ScreenBg = Color(0xFFFCFBFC)
    val SurfaceWhite = Color(0xFFFFFFFF)
    val HeroSurface = Color(0xFFFBF7FD)
    val HeroBorder = Color(0xFFECE2F0)
    val CardBorder = Color(0xFFE9E4ED)
    val PillSurface = Color(0xFFF8F6F9)
    val TopChipBg = Color(0xFFF3EDF7)
    val IconTileBg = Color(0xFFEBE7F1)

    val TextPrimary = Color(0xFF1D1B20)
    val TextSecondary = Color(0xFF49454F)
    val TextTertiary = Color(0xFF79747E)

    val ExpenseBadgeBg = Color(0xFFF9ECE8)
    val ExpenseBadgeText = Color(0xFF86382E)
    val IncomeBadgeBg = Color(0xFFE8F5E9)
    val IncomeBadgeText = Color(0xFF1B7F49)
    val TransferBadgeBg = Color(0xFFECE3F8)
    val TransferBadgeText = Color(0xFF4F2D96)

    val ClearedBg = Color(0xFFFFF9E6)
    val ClearedBorder = Color(0xFFF2DE96)
    val ClearedText = Color(0xFF8C6B00)
    val ClearedGreen = Color(0xFF16A34A)

    val DebitChipBg = Color(0xFFFFDAD6)
    val DebitText = Color(0xFFBA1A1A)
    val SplitWarmBg = Color(0xFFFDEEE3)
    val SplitWarmText = Color(0xFFB55928)
    val ErrorText = Color(0xFFB3261E)
    val ErrorBg = Color(0xFFFCEEEE)
    val WarningBg = Color(0xFFFFF4E5)
    val WarningText = Color(0xFF8A5300)
}

object StitchDimens {
    val RadiusHeroCard = 24.dp
    val RadiusFormCard = 22.dp
    val RadiusPill = 16.dp
    val RadiusInnerCard = 14.dp
    val RadiusTile = 12.dp
    val ScreenHorizontalPadding = 16.dp
    val CardSpacing = 12.dp
    val CardPadding = 14.dp
    val HeroPadding = 16.dp
    val TileSize = 40.dp
    val SmallTileSize = 32.dp
    val ActiveBorderWidth = 2.dp
    val DefaultBorderWidth = 1.dp
}

private val StitchLightColorScheme = lightColorScheme(
    primary = StitchColors.Primary,
    onPrimary = Color.White,
    primaryContainer = StitchColors.PrimaryContainer,
    onPrimaryContainer = StitchColors.OnPrimaryContainer,
    secondary = Color(0xFF625B71),
    secondaryContainer = StitchColors.PrimaryTonal,
    onSecondaryContainer = StitchColors.TextPrimary,
    background = StitchColors.ScreenBg,
    onBackground = StitchColors.TextPrimary,
    surface = StitchColors.SurfaceWhite,
    onSurface = StitchColors.TextPrimary,
    surfaceVariant = StitchColors.TopChipBg,
    onSurfaceVariant = StitchColors.TextSecondary,
    outline = StitchColors.TextTertiary,
    outlineVariant = StitchColors.HeroBorder,
)

@Composable
fun TransactionStitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StitchLightColorScheme,
        content = content,
    )
}
