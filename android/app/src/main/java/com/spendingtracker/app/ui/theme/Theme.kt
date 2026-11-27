package com.spendingtracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

@Composable
fun SpendingTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LedgerTypography,
        shapes = LedgerShapes,
        content = content
    )
}
