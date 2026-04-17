package com.discordia.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ServerForegroundService : Service() {

    private var server: LocalServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Discordia Local Server")
            .setContentText("Running on port $port")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()
        startForeground(NOTIF_ID, notification)
        server = LocalServer(port)
        server?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Local Server", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "discordia_server"
        private const val NOTIF_ID = 42
    }
}
