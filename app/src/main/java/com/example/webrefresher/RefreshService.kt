package com.example.webrefresher

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.random.Random

class RefreshService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private var notificationTickerJob: Job? = null
    
    private var backgroundWebView: WebView? = null
    private var nextRefreshElapsedRealtime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d("RefreshService", "onCreate")
        createNotificationChannel()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebRefresher::AutomationLock")
        wakeLock?.acquire(24 * 3600 * 1000L)

        // Initialize background WebView with Desktop UA to keep session alive
        backgroundWebView = WebView(applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            webViewClient = WebViewClient()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("RefreshService", "onStartCommand: action=$action")
        
        if (action == "STOP") {
            stopAutomation()
            return START_NOT_STICKY
        }

        if (action == "REFRESH_TICK") {
            performRefresh()
            scheduleNextRefresh()
            return START_STICKY
        }

        if (isServiceRunning) return START_STICKY

        val interval = intent?.getIntExtra("interval", 60) ?: 60
        
        val prefs = getSharedPreferences("refresher_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("last_url", "") ?: ""
        
        if (url.isNotEmpty()) {
            backgroundWebView?.loadUrl(url)
        }

        startForeground(NOTIFICATION_ID, updateNotification())
        isServiceRunning = true
        scheduleNextRefresh()
        startNotificationTicker()

        return START_STICKY
    }

    private fun performRefresh() {
        Log.d("RefreshService", "Performing Refresh Signal")
        // 1. Notify Activity
        val refreshIntent = Intent("com.example.webrefresher.REFRESH_WEBVIEW").apply {
            setPackage(packageName)
        }
        sendBroadcast(refreshIntent)

        // 2. Refresh background WebView
        backgroundWebView?.reload()
    }

    private fun startNotificationTicker() {
        notificationTickerJob?.cancel()
        notificationTickerJob = serviceScope.launch {
            while (isActive) {
                val notification = updateNotification()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                delay(1000L)
            }
        }
    }

    private fun updateNotification(): Notification {
        val prefs = getSharedPreferences("refresher_prefs", Context.MODE_PRIVATE)
        val endTime = prefs.getLong("end_time", 0L)
        
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowMillis = System.currentTimeMillis()
        
        val totalRemaining = maxOf(0, (endTime - nowMillis) / 1000L)
        val nextRefreshRemaining = maxOf(0, (nextRefreshElapsedRealtime - nowRealtime) / 1000L)

        val totalStr = String.format("%02d:%02d:%02d", totalRemaining / 3600, (totalRemaining % 3600) / 60, totalRemaining % 60)
        val nextStr = String.format("%02d:%02d", nextRefreshRemaining / 60, nextRefreshRemaining % 60)

        val content = "🔄 Next: $nextStr  |  ⌛ Ends: $totalStr"

        val stopIntent = Intent(this, RefreshService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Web Refresher Pro")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true) 
            .build()
    }

    private fun scheduleNextRefresh() {
        val prefs = getSharedPreferences("refresher_prefs", Context.MODE_PRIVATE)
        val interval = prefs.getInt("interval", 60)
        val endTime = prefs.getLong("end_time", 0L)
        val isSafetyEnabled = prefs.getBoolean("safety_enabled", false)
        
        if (System.currentTimeMillis() >= endTime) {
            stopAutomation()
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, RefreshService::class.java).apply {
            action = "REFRESH_TICK"
        }
        
        val pendingIntent = PendingIntent.getService(
            this, 1001, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        var delaySeconds = interval.toLong()
        if (isSafetyEnabled) {
            delaySeconds += Random.nextInt(1, 31)
        }
        
        nextRefreshElapsedRealtime = SystemClock.elapsedRealtime() + (delaySeconds * 1000L)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextRefreshElapsedRealtime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextRefreshElapsedRealtime, pendingIntent)
            }
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextRefreshElapsedRealtime, pendingIntent)
        }
    }

    private fun stopAutomation() {
        isServiceRunning = false
        notificationTickerJob?.cancel()
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, RefreshService::class.java).apply { action = "REFRESH_TICK" }
        val pendingIntent = PendingIntent.getService(this, 1001, intent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)

        if (wakeLock?.isHeld == true) wakeLock?.release()
        
        getSharedPreferences("refresher_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_running", false).apply()
        
        val stopIntent = Intent("com.example.webrefresher.STOP_AUTOMATION").apply {
            setPackage(packageName)
        }
        sendBroadcast(stopIntent)

        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Refresh Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        backgroundWebView?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "RefreshServiceChannel"
        const val NOTIFICATION_ID = 1
    }
}
