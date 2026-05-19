package com.example.screenshotnotifier

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_TAKE_SCREENSHOT = "com.example.screenshotnotifier.TAKE_SCREENSHOT"
        const val CHANNEL_ID = "screenshot_channel"
        const val NOTIFICATION_ID = 1001
        var instance: ScreenshotAccessibilityService? = null
    }

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TAKE_SCREENSHOT) {
                takeScreenshotNow()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        createNotificationChannel()
        registerBroadcastReceiver()
        showPersistentNotification()
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter(ACTION_TAKE_SCREENSHOT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showPersistentNotification() {
        val screenshotIntent = Intent(ACTION_TAKE_SCREENSHOT)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            screenshotIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_camera,
                    getString(R.string.take_screenshot),
                    pendingIntent
                ).build()
            )
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun takeScreenshotNow() {
        // Bildirim paneli kapandıktan sonra mevcut ekranın görüntüsünü al
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Toast.makeText(this, getString(R.string.screenshot_taken), Toast.LENGTH_SHORT).show()
        }, 600)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            unregisterReceiver(screenshotReceiver)
        } catch (e: Exception) {
            // Receiver zaten kayıtlı değil
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}
