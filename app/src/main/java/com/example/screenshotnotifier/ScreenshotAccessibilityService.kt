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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.sqrt

class ScreenshotAccessibilityService : AccessibilityService(), SensorEventListener {

    companion object {
        const val ACTION_TAKE_SCREENSHOT = "com.example.screenshotnotifier.TAKE_SCREENSHOT"
        const val CHANNEL_ID = "screenshot_channel"
        const val NOTIFICATION_ID = 1001
        private const val SHAKE_THRESHOLD = 13f       // m/s² — ne kadar sert sallanmalı
        private const val SHAKE_COOLDOWN_MS = 2000L   // iki sallama arası minimum süre
        var instance: ScreenshotAccessibilityService? = null
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L

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
        startShakeDetection()
    }

    private fun startShakeDetection() {
        try {
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                // SENSOR_DELAY_UI: MIUI'da GAME frekansı servisi öldürüyor
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (e: Exception) {
            // Sensör başlatılamazsa butona basma yöntemi çalışmaya devam eder
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Yerçekimi etkisini çıkar ve ivmeyi hesapla
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (acceleration > SHAKE_THRESHOLD) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now
                takeScreenshotNow()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(screenshotReceiver)
        } catch (e: Exception) {}
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}
