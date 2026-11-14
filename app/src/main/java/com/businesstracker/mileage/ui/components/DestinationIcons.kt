package com.businesstracker.mileage.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.businesstracker.mileage.ui.theme.SpeedRed
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Creates custom Google Maps BitmapDescriptor markers for Origin (GPS Radar Beacon)
 * and Destination (Finish Flag).
 */
object MapMarkerFactory {

    fun createStartBeaconBitmapDescriptor(): BitmapDescriptor {
        return try {
            val size = 72
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val cx = size / 2f
            val cy = size / 2f

            // Outer pulse circle
            val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(70, 16, 185, 129) // SucculentPrimary translucent
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 32f, pulsePaint)

            // Middle beacon ring
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(16, 185, 129) // SucculentPrimary
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawCircle(cx, cy, 22f, ringPaint)

            // Inner solid dot
            val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(16, 185, 129)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 11f, centerPaint)

            // Center white specular pip
            val whitePip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 4f, whitePip)

            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (_: Throwable) {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        }
    }

    fun createEndFlagBitmapDescriptor(): BitmapDescriptor = try {
        BitmapDescriptorFactory.fromBitmap(createEndPinBitmap())
    } catch (_: Throwable) {
        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)
    }

    fun createEndPinBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(72, 88, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(204, 91, 119)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(36f, 79f)
            cubicTo(27f, 63f, 14f, 48f, 14f, 34f)
            cubicTo(14f, 21f, 24f, 11f, 36f, 11f)
            cubicTo(48f, 11f, 58f, 21f, 58f, 34f)
            cubicTo(58f, 48f, 45f, 63f, 36f, 79f)
            close()
        }
        canvas.drawPath(path, outline)
        canvas.drawCircle(36f, 34f, 8f, outline)
        return bitmap
    }

}

/**
 * Origin GPS Radar Beacon Composable for list rows and card previews.
 */
@Composable
fun StartBeaconIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = SucculentPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size * 0.75f)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            // Middle ring
            drawCircle(
                color = tint,
                radius = this.size.width * 0.40f,
                style = Stroke(width = 2.dp.toPx())
            )
            // Inner core dot
            drawCircle(
                color = tint,
                radius = this.size.width * 0.22f
            )
        }
    }
}

/**
 * Destination Soft Red Map Pin Composable matching the Google Maps marker style:
 * - Soft rose-red outline (default: #D7737D matching the map pin)
 * - Interior fill with card/surface background so connector lines sit cleanly behind it
 * - Small center red dot
 * - Downward-pointing teardrop pin tip
 */
@Composable
fun EndPinIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color(0xFFD7737D),
    fillColor: Color = Color.White
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val headRadius = this.size.width * 0.35f
        val headCenterY = this.size.height * 0.40f
        val tipY = this.size.height * 0.95f
        val pinStroke = 1.8.dp.toPx()

        val pinPath = androidx.compose.ui.graphics.Path().apply {
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

        // 1. Interior fill
        drawPath(path = pinPath, color = fillColor, style = androidx.compose.ui.graphics.drawscope.Fill)
        // 2. Soft red outline
        drawPath(path = pinPath, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = pinStroke))
        // 3. Small center red dot
        drawCircle(
            color = tint,
            radius = headRadius * 0.35f,
            center = Offset(cx, headCenterY)
        )
    }
}

/**
 * Destination Finish Pin Composable for list rows and card previews.
 */
@Composable
fun EndFlagIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color(0xFFD7737D),
    fillColor: Color = Color.White
) {
    EndPinIcon(
        modifier = modifier,
        size = size,
        tint = tint,
        fillColor = fillColor
    )
}
