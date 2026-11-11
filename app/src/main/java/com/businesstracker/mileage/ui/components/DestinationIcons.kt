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

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun createEndFlagBitmapDescriptor(): BitmapDescriptor {
        val width = 76
        val height = 76
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Drop shadow for flagpole anchor
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(80, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(14f, 66f, 32f, 74f, shadowPaint)

        // Pole (dark slate)
        val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(51, 65, 85)
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(23f, 10f, 23f, 68f, polePaint)

        // Ball top of flagpole
        val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(239, 68, 68) // SpeedRed
            style = Paint.Style.FILL
        }
        canvas.drawCircle(23f, 9f, 6f, ballPaint)

        // Waving Flag Path (Red background)
        val flagPath = Path().apply {
            moveTo(23f, 14f)
            cubicTo(36f, 11f, 50f, 19f, 66f, 15f)
            lineTo(66f, 42f)
            cubicTo(50f, 46f, 36f, 38f, 23f, 41f)
            close()
        }

        val flagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(239, 68, 68) // SpeedRed
            style = Paint.Style.FILL
        }
        canvas.drawPath(flagPath, flagBgPaint)

        // White checkered squares on waving flag
        val whiteCheckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }

        // Draw checkered boxes
        canvas.save()
        canvas.clipPath(flagPath)
        val cols = 4
        val rows = 3
        val colWidth = 44f / cols
        val rowHeight = 28f / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((r + c) % 2 == 0) {
                    val left = 23f + c * colWidth
                    val top = 13f + r * rowHeight
                    canvas.drawRect(left, top, left + colWidth, top + rowHeight, whiteCheckPaint)
                }
            }
        }
        canvas.restore()

        // Border around flag
        val flagBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(185, 28, 28)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawPath(flagPath, flagBorderPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
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
 * Destination Finish Flag Composable for list rows and card previews.
 */
@Composable
fun EndFlagIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = SpeedRed
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size * 0.70f)) {
            val w = this.size.width
            val h = this.size.height
            val poleX = w * 0.28f

            // Draw flagpole
            drawLine(
                color = Color(0xFF334155),
                start = Offset(poleX, h * 0.12f),
                end = Offset(poleX, h * 0.92f),
                strokeWidth = 2.dp.toPx()
            )

            // Draw flag pole finial (top circle)
            drawCircle(
                color = tint,
                radius = 1.5.dp.toPx(),
                center = Offset(poleX, h * 0.10f)
            )

            // Draw flag
            val flagWidth = w * 0.60f
            val flagHeight = h * 0.45f
            val flagTop = h * 0.14f

            val flagPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(poleX, flagTop)
                lineTo(poleX + flagWidth, flagTop)
                lineTo(poleX + flagWidth * 0.85f, flagTop + flagHeight / 2f)
                lineTo(poleX + flagWidth, flagTop + flagHeight)
                lineTo(poleX, flagTop + flagHeight)
                close()
            }

            drawPath(
                path = flagPath,
                color = tint
            )

            // Inner checkered square
            drawRect(
                color = Color.White,
                topLeft = Offset(poleX + 1.dp.toPx(), flagTop + 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(flagWidth * 0.45f, flagHeight * 0.45f)
            )
        }
    }
}
