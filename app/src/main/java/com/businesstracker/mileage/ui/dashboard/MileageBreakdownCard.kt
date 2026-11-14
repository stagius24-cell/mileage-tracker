package com.businesstracker.mileage.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.domain.model.DashboardSummary
import java.util.Locale
import kotlin.math.roundToInt

private data class MileageCardColors(
    val surface: Color, val text: Color, val muted: Color, val business: Color,
    val personal: Color, val mint: Color, val blue: Color, val divider: Color
)

@Composable
internal fun MileageBreakdownCard(
    summary: DashboardSummary,
    periodLabel: String = "",
    onCategoryClick: ((category: String) -> Unit)? = null,
    onDeductionClick: (() -> Unit)? = null
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = if (dark) MileageCardColors(MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant,
        Color(0xFF43D99A), Color(0xFF75B9FF), Color(0xFF14372B), Color(0xFF172E49), MaterialTheme.colorScheme.outlineVariant)
    else MileageCardColors(Color.White, Color(0xFF18283E), Color(0xFF718096), Color(0xFF10A66B),
        Color(0xFF2788E9), Color(0xFFF0FAF5), Color(0xFFEFF6FF), Color(0xFFEDF1F5))
    val business = summary.rawBusinessMiles
    val personal = summary.rawPersonalMiles
    val total = business + personal
    val fraction = if (total > 0) (business / total).toFloat().coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(fraction, tween(350), label = "mileage_distribution")
    val businessPercent = if (total > 0) (business / total * 100).roundToInt() else 0
    val personalPercent = if (total > 0) 100 - businessPercent else 0
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total classified mileage", modifier = Modifier.weight(1f), fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = colors.muted)
                Surface(shape = RoundedCornerShape(12.dp), color = colors.divider) {
                    Text("${summary.totalTripsCount} trips", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp, color = colors.muted)
                }
            }
            Text("${String.format(Locale.US, "%,.1f", total)} mi", fontSize = 32.sp,
                fontWeight = FontWeight.Bold, color = colors.text,
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"))
            Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).semantics {
                contentDescription = "Business $businessPercent percent, personal $personalPercent percent"
            }) {
                if (total <= 0) {
                    drawRect(colors.divider)
                } else {
                    val boundary = size.width * animatedFraction
                    drawRect(colors.business, size = Size(boundary, size.height))
                    drawRect(colors.personal, topLeft = Offset(boundary, 0f), size = Size(size.width - boundary, size.height))
                    // Draw a crisp 2dp white separator between Business and Personal segments
                    // Only display when both categories have mileage (disappears for 100% Business, 100% Personal, or zero mileage)
                    if (animatedFraction > 0.001f && animatedFraction < 0.999f) {
                        val sepWidth = 2.dp.toPx()
                        val sepLeft = (boundary - sepWidth / 2f).coerceIn(0f, size.width - sepWidth)
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(sepLeft, 0f),
                            size = Size(sepWidth, size.height)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val periodSuffix = if (periodLabel.isNotBlank()) " for $periodLabel" else ""
                val businessA11y = "Business, ${String.format(Locale.US, "%,.1f", business)} miles, $businessPercent percent. View Business trips$periodSuffix."
                val personalA11y = "Personal, ${String.format(Locale.US, "%,.1f", personal)} miles, $personalPercent percent. View Personal trips$periodSuffix."

                MileageCategory(
                    title = "Business",
                    miles = business,
                    percent = businessPercent,
                    icon = Icons.Outlined.BusinessCenter,
                    accent = colors.business,
                    background = colors.mint,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = if (onCategoryClick != null) { { onCategoryClick("BUSINESS") } } else null,
                    accessibilityDescription = businessA11y
                )
                MileageCategory(
                    title = "Personal",
                    miles = personal,
                    percent = personalPercent,
                    icon = Icons.Outlined.Home,
                    accent = colors.personal,
                    background = colors.blue,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = if (onCategoryClick != null) { { onCategoryClick("PERSONAL") } } else null,
                    accessibilityDescription = personalA11y
                )
            }
            HorizontalDivider(color = colors.divider)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                    .then(if (onDeductionClick != null) Modifier.clickable(onClickLabel = "Open deduction reports", onClick = onDeductionClick) else Modifier)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.mint) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = colors.business,
                        modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Estimated tax deduction", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.muted)
                    Text("$${String.format(Locale.US, "%,.2f", summary.estimatedDeduction)}", fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, color = colors.text,
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
                }
                if (onDeductionClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.muted)
            }
        }
    }
}

@Composable
private fun MileageCategory(
    title: String,
    miles: Double,
    percent: Int,
    icon: ImageVector,
    accent: Color,
    background: Color,
    colors: MileageCardColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilityDescription: String? = null
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "View $title trips",
                            onClick = onClick
                        )
                } else Modifier
            )
            .then(
                if (accessibilityDescription != null) {
                    Modifier.semantics { contentDescription = accessibilityDescription }
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = background
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent)
            }
            Text("${String.format(Locale.US, "%,.1f", miles)} mi", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = colors.text, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            Text("$percent% of total", fontSize = 11.sp, color = colors.muted)
        }
    }
}
