package io.github.miuzarte.fhradio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RadioForegroundService: Service() {

    private var isStopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("FHRadio", "", ""))
            }

            ACTION_STOP -> {
                if (isStopping) return START_NOT_STICKY
                isStopping = true
                Radio.setStation(null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_NEXT -> {
                Radio.nextSection()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isStopping = false
        super.onDestroy()
    }

    private fun buildNotification(title: String, artist: String, station: String): Notification {
        val contentIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let {
                PendingIntent.getActivity(
                    this, 0, it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        val nextIntent = Intent(this, RadioForegroundService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, RadioForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = buildString {
            if (artist.isNotEmpty()) {
                append(artist)
                if (station.isNotEmpty()) append(" - ")
            }
            append(station)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifEmpty { "FHRadio" })
            .setContentText(contentText.ifEmpty { "正在播放" })
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_next, "下一首", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FHRadio 播放",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "FHRadio 电台播放状态"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "fhradio_playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "io.github.miuzarte.fhradio.action.START"
        private const val ACTION_STOP = "io.github.miuzarte.fhradio.action.STOP"
        private const val ACTION_NEXT = "io.github.miuzarte.fhradio.action.NEXT"

        fun start(context: Context) {
            val intent = Intent(context, RadioForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RadioForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
