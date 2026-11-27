package com.spendingtracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Ledger" calm-precision fintech palette — matched 1:1 with the Web UI
private val LightColors = lightColorScheme(
    primary = Color(0xFF0D5C4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F2EF),
    onPrimaryContainer = Color(0xFF094538),
    secondary = Color(0xFF6B6D67),
    onSecondary = Color.White,
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF141513),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141513),
    surfaceVariant = Color(0xFFF1F1ED),
    onSurfaceVariant = Color(0xFF6B6D67),
    outline = Color(0xFFE6E5E0),
    outlineVariant = Color(0xFFD4D3CC),
    error = Color(0xFFB91C1C),
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFFB91C1C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF052E24),
    primaryContainer = Color(0xFF12352C),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = Color(0xFF8F9690),
    onSecondary = Color(0xFF0E100F),
    background = Color(0xFF0E100F),
    onBackground = Color(0xFFF2F2EE),
    surface = Color(0xFF151816),
    onSurface = Color(0xFFF2F2EE),
    surfaceVariant = Color(0xFF1C201E),
    onSurfaceVariant = Color(0xFF8F9690),
    outline = Color(0xFF252A27),
    outlineVariant = Color(0xFF343B37),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3B1517),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LedgerTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
)

private val LedgerShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

fun categoryAccentColor(name: String?): Color = when (name) {
    "Groceries" -> Color(0xFF0D9488)
    "Dining" -> Color(0xFFD97706)
    "Utilities" -> Color(0xFF2563EB)
    "Transport" -> Color(0xFF7C3AED)
    "Shopping" -> Color(0xFFDB2777)
    "Entertainment" -> Color(0xFFEA580C)
    "Health" -> Color(0xFF059669)
    "Housing" -> Color(0xFF4F46E5)
    "Transfer" -> Color(0xFF475569)
    "Subscriptions" -> Color(0xFF0284C7)
    "Education" -> Color(0xFF6366F1)
    "Travel" -> Color(0xFF0891B2)
    "Personal Care" -> Color(0xFFEC4899)
    "Family & Kids" -> Color(0xFFF59E0B)
    "Gifts & Donations" -> Color(0xFFE11D48)
    "Insurance" -> Color(0xFF3B82F6)
    "Fees & Interest" -> Color(0xFFDC2626)
    "Investments" -> Color(0xFF16A34A)
    "Salary" -> Color(0xFF10B981)
    "Income" -> Color(0xFF059669)
    "Refund" -> Color(0xFF14B8A6)
    "Debt Repayment" -> Color(0xFFB45309)
    else -> Color(0xFF94A3B8)
}

fun categoryIcon(name: String?): ImageVector = when ((name ?: "").lowercase()) {
    "groceries" -> Icons.Rounded.LocalGroceryStore
    "dining" -> Icons.Rounded.Restaurant
    "utilities" -> Icons.Rounded.Bolt
    "transport" -> Icons.Rounded.DirectionsCar
    "shopping" -> Icons.Rounded.ShoppingBag
    "entertainment" -> Icons.Rounded.Movie
    "health" -> Icons.Rounded.FavoriteBorder
    "housing" -> Icons.Rounded.Home
    "transfer" -> Icons.Rounded.SwapHoriz
    "subscriptions" -> Icons.Rounded.Subscriptions
    "education" -> Icons.Rounded.School
    "travel" -> Icons.Rounded.Flight
    "gifts & donations" -> Icons.Rounded.CardGiftcard
    "insurance", "fees & interest" -> Icons.Rounded.AccountBalance
    "investments" -> Icons.Rounded.TrendingUp
    "salary", "income", "refund" -> Icons.Rounded.Payments
    "debt repayment" -> Icons.Rounded.CreditCard
    else -> Icons.Rounded.ReceiptLong
}

fun heroGradientColors(accentPalette: String): List<Color> = when (accentPalette) {
    "ocean" -> listOf(Color(0xFF123166), Color(0xFF0B1F44))
    "indigo" -> listOf(Color(0xFF262063), Color(0xFF17133F))
    "terracotta" -> listOf(Color(0xFF5A2A0C), Color(0xFF381806))
    "obsidian" -> listOf(Color(0xFF1E2022), Color(0xFF101113))
    else -> listOf(Color(0xFF0F3D33), Color(0xFF0A2922))
}

@Composable
fun SpendingTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentPalette: String = "forest",
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val scheme = when (accentPalette) {
        "ocean" -> baseScheme.copy(
            primary = if (darkTheme) Color(0xFF60A5FA) else Color(0xFF1D4ED8),
            primaryContainer = if (darkTheme) Color(0xFF172554) else Color(0xFFDBEAFE),
            onPrimaryContainer = if (darkTheme) Color(0xFFBFDBFE) else Color(0xFF1E3A8A)
        )
        "indigo" -> baseScheme.copy(
            primary = if (darkTheme) Color(0xFF818CF8) else Color(0xFF4338CA),
            primaryContainer = if (darkTheme) Color(0xFF1E1B4B) else Color(0xFFE0E7FF),
            onPrimaryContainer = if (darkTheme) Color(0xFFC7D2FE) else Color(0xFF312E81)
        )
        "terracotta" -> baseScheme.copy(
            primary = if (darkTheme) Color(0xFFFBBF24) else Color(0xFFB45309),
            primaryContainer = if (darkTheme) Color(0xFF451A03) else Color(0xFFFEF3C7),
            onPrimaryContainer = if (darkTheme) Color(0xFFFDE68A) else Color(0xFF78350F)
        )
        "obsidian" -> baseScheme.copy(
            primary = if (darkTheme) Color(0xFFE4E4E7) else Color(0xFF18181B),
            primaryContainer = if (darkTheme) Color(0xFF27272A) else Color(0xFFF4F4F5),
            onPrimaryContainer = if (darkTheme) Color(0xFFF4F4F5) else Color(0xFF18181B)
        )
        else -> baseScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = LedgerTypography,
        shapes = LedgerShapes,
        content = content
    )
}
