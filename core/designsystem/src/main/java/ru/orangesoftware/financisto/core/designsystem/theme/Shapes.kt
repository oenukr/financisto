// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FinancistoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(FinancistoDimens.RadiusTile),
    medium = RoundedCornerShape(FinancistoDimens.RadiusPill),
    large = RoundedCornerShape(FinancistoDimens.RadiusFormCard),
    extraLarge = RoundedCornerShape(FinancistoDimens.RadiusHeroCard),
)
