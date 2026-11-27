package com.businesstracker.mileage.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Home
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
    val surface: Color,
    val text: Color,
    val muted: Color,
    val business: Color,
    val personal: Color,
    val mint: Color,
    val divider: Color
)

@Composable
private fun rememberMileageCardColors(): MileageCardColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        MileageCardColors(
            surface = MaterialTheme.colorScheme.surface,
            text = MaterialTheme.colorScheme.onSurface,
            muted = MaterialTheme.colorScheme.onSurfaceVariant,
            business = Color(0xFF43D99A),
            personal = Color(0xFF75B9FF),
            mint = Color(0xFF14372B),
            divider = MaterialTheme.colorScheme.outlineVariant
        )
    } else {
        MileageCardColors(
            surface = Color.White,
            text = Color(0xFF18283E),
            muted = Color(0xFF64748B),
            business = Color(0xFF10A66B),
            personal = Color(0xFF3B82F6),
            mint = Color(0xFFECFDF5),
            divider = Color(0xFFE2E8F0)
        )
    }
}

@Composable
internal fun MileageBreakdownCard(
    summary: DashboardSummary,
    periodLabel: String = "",
    onCategoryClick: ((category: String) -> Unit)? = null,
    onDeductionClick: (() -> Unit)? = null
) {
    val colors = rememberMileageCardColors()
    val business = summary.rawBusinessMiles
    val personal = summary.rawPersonalMiles
    val total = business + personal
    val fraction = if (total > 0) (business / total).toFloat().coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(fraction, tween(350), label = "mileage_distribution")
    val businessPercent = if (total > 0) (business / total * 100).roundToInt() else 0
    val personalPercent = if (total > 0) 100 - businessPercent else 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Total classified mileage & Trips directly on canvas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Total classified mileage",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.muted
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%,.1f", total),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "mi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${summary.totalTripsCount}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                )
                Text(
                    text = "Trips",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.muted
                )
            }
        }

        // Compact white breakdown container
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val periodSuffix = if (periodLabel.isNotBlank()) " for $periodLabel" else ""
                    val businessA11y = "Business, ${String.format(Locale.US, "%,.1f", business)} miles, $businessPercent percent. View Business trips$periodSuffix."
                    val personalA11y = "Personal, ${String.format(Locale.US, "%,.1f", personal)} miles, $personalPercent percent. View Personal trips$periodSuffix."

                    MileageCategoryColumn(
                        title = "Business",
                        miles = business,
                        percent = businessPercent,
                        icon = Icons.Outlined.BusinessCenter,
                        accent = colors.business,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = if (onCategoryClick != null) { { onCategoryClick("BUSINESS") } } else null,
                        accessibilityDescription = businessA11y
                    )
                    MileageCategoryColumn(
                        title = "Personal",
                        miles = personal,
                        percent = personalPercent,
                        icon = Icons.Outlined.Home,
                        accent = colors.personal,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = if (onCategoryClick != null) { { onCategoryClick("PERSONAL") } } else null,
                        accessibilityDescription = personalA11y
                    )
                }

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .semantics {
                            contentDescription = "Business $businessPercent percent, personal $personalPercent percent"
                        }
                ) {
                    if (total <= 0) {
                        drawRect(colors.divider)
                    } else {
                        val boundary = size.width * animatedFraction
                        drawRect(colors.business, size = Size(boundary, size.height))
                        drawRect(
                            colors.personal,
                            topLeft = Offset(boundary, 0f),
                            size = Size(size.width - boundary, size.height)
                        )
                        if (animatedFraction > 0.001f && animatedFraction < 0.999f) {
                            val sepWidth = 2.dp.toPx()
                            val sepLeft = (boundary - sepWidth / 2f).coerceIn(0f, size.width - sepWidth)
                            drawRect(
                                color = colors.surface,
                                topLeft = Offset(sepLeft, 0f),
                                size = Size(sepWidth, size.height)
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.divider.copy(alpha = 0.7f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (onDeductionClick != null) {
                                Modifier.clickable(onClickLabel = "Open deduction reports", onClick = onDeductionClick)
                            } else Modifier
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = colors.business,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Estimated tax deduction",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", summary.estimatedDeduction)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.business,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                    )
                    if (onDeductionClick != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MileageCategoryColumn(
    title: String,
    miles: Double,
    percent: Int,
    icon: ImageVector,
    accent: Color,
    colors: MileageCardColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accessibilityDescription: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
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
            )
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(
                text = "$title · $percent%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted
            )
        }
        Text(
            text = "${String.format(Locale.US, "%,.1f", miles)} mi",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
internal fun AnnualSummaryCard(
    year: Int,
    annualSummary: DashboardSummary,
    modifier: Modifier = Modifier
) {
    val colors = rememberMileageCardColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Annual summary",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text
                )
                Text(
                    text = "$year YTD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.muted
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Business mileage",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted
                    )
                    Text(
                        text = "${String.format(Locale.US, "%,.1f", annualSummary.rawBusinessMiles)} mi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Est. tax deduction",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", annualSummary.estimatedDeduction)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.business,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}
