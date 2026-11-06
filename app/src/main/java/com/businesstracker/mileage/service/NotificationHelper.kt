package com.businesstracker.mileage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.businesstracker.mileage.R
import com.businesstracker.mileage.receiver.ServiceActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_TRACKING_ID = "channel_mileage_tracking"
        const val CHANNEL_TRIPS_ID = "channel_trip_alerts"
        const val NOTIFICATION_TRACKING_ID = 1001
        const val NOTIFICATION_TRIP_DETECTED_ID = 2001

        const val ACTION_PAUSE = "com.businesstracker.mileage.ACTION_PAUSE"
        const val ACTION_RESUME = "com.businesstracker.mileage.ACTION_RESUME"
        const val ACTION_STOP = "com.businesstracker.mileage.ACTION_STOP"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackingChannel = NotificationChannel(
                CHANNEL_TRACKING_ID,
                context.getString(R.string.notification_channel_tracking),
                NotificationManager.IMPORTANCE_LOW // Low to prevent continuous sounds on GPS updates
            ).apply {
                description = context.getString(R.string.notification_channel_tracking_desc)
                setShowBadge(false)
            }

            val tripAlertsChannel = NotificationChannel(
                CHANNEL_TRIPS_ID,
                context.getString(R.string.notification_channel_trips),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_trips_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(trackingChannel)
            notificationManager.createNotificationChannel(tripAlertsChannel)
        }
    }

    fun buildTrackingNotification(
        distanceMiles: Double,
        durationMillis: Long,
        isPaused: Boolean
    ): Notification {
        val formattedDistance = String.format(Locale.US, "%.2f miles", distanceMiles)
        val formattedDuration = formatDuration(durationMillis)
        val statusText = if (isPaused) "Paused • $formattedDuration" else "$formattedDistance • $formattedDuration"

        // Pause/Resume PendingIntent
        val toggleActionIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End Trip PendingIntent
        val stopActionIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeTitle = if (isPaused) {
            context.getString(R.string.action_resume)
        } else {
            context.getString(R.string.action_pause)
        }

        return NotificationCompat.Builder(context, CHANNEL_TRACKING_ID)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, pauseResumeTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_end_trip), stopPendingIntent)
            .build()
    }

    fun updateTrackingNotification(
        distanceMiles: Double,
        durationMillis: Long,
        isPaused: Boolean
    ) {
        val notification = buildTrackingNotification(distanceMiles, durationMillis, isPaused)
        notificationManager.notify(NOTIFICATION_TRACKING_ID, notification)
    }

    fun showTripDetectedNotification(tripId: String, distanceMiles: Double) {
        // App launch or classification screen pending intent
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TRIP_ID", tripId)
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                tripId.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TRIPS_ID)
            .setContentTitle("New trip detected (${String.format(Locale.US, "%.1f", distanceMiles)} mi)")
            .setContentText("Tap to classify for MySucculentCorner.")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_TRIP_DETECTED_ID, notification)
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
