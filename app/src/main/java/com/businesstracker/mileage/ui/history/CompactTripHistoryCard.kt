package com.businesstracker.mileage.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.domain.usecase.SmartTripNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact floating trip card matching the reference design:
 * - Upper-left: Category capsule badge (Business / Personal / Unreviewed)
 * - Upper-right: Deduction (Business only, #009F59) + Mileage (#192A40 bold)
 * - Row 2: Starting location: Green double-ring circle, start address, start timestamp
 * - Row 3: Ending location: Soft red outlined pin, end address, end timestamp
 * - Single thin vertical connector line (#D9E3EB) between the two route icons
 * - Card corner radius: 20dp, solid white surface, no heavy shadow or borders
 */
@Composable
fun CompactTripHistoryCard(
    trip: TripEntity,
    smartNames: SmartTripNames? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardBg = if (dark) MaterialTheme.colorScheme.surface else Color.White

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val startTime = remember(trip.startTime) { timeFormatter.format(Date(trip.startTime)) }
    val endTime = remember(trip.endTime) { timeFormatter.format(Date(trip.endTime)) }

    val startAddress = smartNames?.startDisplayName ?: (trip.startAddress?.takeIf { it.isNotBlank() } ?: "Starting location unavailable")
    val endAddress = smartNames?.endDisplayName ?: (trip.endAddress?.takeIf { it.isNotBlank() } ?: "Destination unavailable")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Row 1: Category Badge & Mileage / Deduction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TripCategoryBadge(classification = trip.classification)

                TripMileageSummary(
                    classification = trip.classification,
                    distanceMiles = trip.totalDistanceMiles,
                    deduction = trip.calculatedDeduction
                )
            }

            Spacer(Modifier.height(12.dp))

            // Row 2 & 3: Route Timeline & Location Rows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TripRouteTimeline(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(),
                    cardBackgroundColor = cardBg
                )

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TripLocationRow(
                        address = startAddress,
                        timestamp = startTime
                    )
                    TripLocationRow(
                        address = endAddress,
                        timestamp = endTime
                    )
                }
            }
        }
    }
}

/**
 * Pill-shaped classification badge for Business (mint/green), Personal (light-blue/blue),
 * or Unreviewed (neutral gray).
 */
@Composable
fun TripCategoryBadge(
    classification: String,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isBusiness = classification.equals("BUSINESS", ignoreCase = true)
    val isPersonal = classification.equals("PERSONAL", ignoreCase = true)

    val bgColor = when {
        isBusiness -> if (dark) Color(0xFF14372B) else Color(0xFFE7F8EE)
        isPersonal -> if (dark) Color(0xFF172E49) else Color(0xFFEAF3FF)
        else -> if (dark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F5F9)
    }
    val contentColor = when {
        isBusiness -> if (dark) Color(0xFF43D99A) else Color(0xFF009F59)
        isPersonal -> if (dark) Color(0xFF75B9FF) else Color(0xFF168CE0)
        else -> if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF718096)
    }
    val icon = when {
        isBusiness -> Icons.Outlined.BusinessCenter
        isPersonal -> Icons.Outlined.Person
        else -> Icons.Outlined.HelpOutline
    }
    val label = when {
        isBusiness -> "Business"
        isPersonal -> "Personal"
        else -> "Unreviewed"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

/**
 * Upper-right mileage and deduction display:
 * - Business: "$1.94  2.6 mi" (deduction in green #009F59 16sp semibold, mileage in #192A40 20sp bold)
 * - Personal / Unreviewed: "0.4 mi" (mileage only, aligned right)
 */
@Composable
fun TripMileageSummary(
    classification: String,
    distanceMiles: Double,
    deduction: Double,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isBusiness = classification.equals("BUSINESS", ignoreCase = true)
    val mileageColor = if (dark) MaterialTheme.colorScheme.onSurface else Color(0xFF192A40)
    val deductionColor = if (dark) Color(0xFF43D99A) else Color(0xFF009F59)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isBusiness && deduction > 0.0) {
            Text(
                text = String.format(Locale.US, "$%.2f", deduction),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = deductionColor,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
            )
        }
        Text(
            text = "${String.format(Locale.US, "%.1f", distanceMiles)} mi",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = mileageColor,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
        )
    }
}

/**
 * Three-column location row: [Icon Column] [Address] [Timestamp]
 * Ensures long addresses truncate with an ellipsis without pushing the timestamp outside.
 */
@Composable
fun TripLocationRow(
    address: String,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val addressColor = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF526277)
    val timestampColor = if (dark) MaterialTheme.colorScheme.outline else Color(0xFF718096)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = address,
            modifier = Modifier.weight(1f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = addressColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = timestamp,
            modifier = Modifier.widthIn(min = 68.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = timestampColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
        )
    }
}

/**
 * Custom vector timeline drawing:
 * 1. Green starting double-ring circle (outer green stroke #009F59, separation gap, solid green center #00A75B)
 * 2. Soft red destination map pin (outline #D7737D, white interior, small red dot)
 * 3. 2dp vertical connector line (#D9E3EB) connecting both icons without drift
 */
@Composable
fun TripRouteTimeline(
    modifier: Modifier = Modifier,
    cardBackgroundColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val startCenterY = 10.dp.toPx()
        val endCenterY = size.height - 10.dp.toPx()

        // 1. Vertical connector line
        if (endCenterY > startCenterY + 14.dp.toPx()) {
            drawLine(
                color = Color(0xFFD9E3EB),
                start = Offset(cx, startCenterY + 7.dp.toPx()),
                end = Offset(cx, endCenterY - 7.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 2. Green starting circle:
        val outerRadius = 8.dp.toPx()
        val outerStroke = 2.dp.toPx()
        val innerRadius = 3.5.dp.toPx()

        // Erase connector line behind starting circle with card background
        drawCircle(
            color = cardBackgroundColor,
            radius = outerRadius,
            center = Offset(cx, startCenterY),
            style = Fill
        )
        // Outer green stroke (#009F59)
        drawCircle(
            color = Color(0xFF009F59),
            radius = outerRadius - outerStroke / 2f,
            center = Offset(cx, startCenterY),
            style = Stroke(width = outerStroke)
        )
        // Inner solid green circle (#00A75B)
        drawCircle(
            color = Color(0xFF00A75B),
            radius = innerRadius,
            center = Offset(cx, startCenterY),
            style = Fill
        )

        // 3. Soft red destination pin:
        val pinColor = Color(0xFFD7737D)
        val headRadius = 5.5.dp.toPx()
        val headCenterY = endCenterY - 1.8.dp.toPx()
        val tipY = endCenterY + 7.5.dp.toPx()
        val pinStroke = 1.8.dp.toPx()

        val pinPath = Path().apply {
            moveTo(cx, tipY)
            val dx = headRadius * 0.88f
            val dy = headRadius * 0.45f
            lineTo(cx - dx, headCenterY + dy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    cx - headRadius,
                    headCenterY - headRadius,
                    cx + headRadius,
                    headCenterY + headRadius
                ),
                startAngleDegrees = 155f,
                sweepAngleDegrees = 230f,
                forceMoveTo = false
            )
            lineTo(cx, tipY)
            close()
        }

        // Fill interior with card background so connector line doesn't show through
        drawPath(path = pinPath, color = cardBackgroundColor, style = Fill)
        // Soft red outline (#D7737D)
        drawPath(path = pinPath, color = pinColor, style = Stroke(width = pinStroke))
        // Small red center dot (#D7737D)
        drawCircle(
            color = pinColor,
            radius = 2.dp.toPx(),
            center = Offset(cx, headCenterY)
        )
    }
}
