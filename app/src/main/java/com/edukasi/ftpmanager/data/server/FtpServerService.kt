package com.edukasi.ftpmanager.data.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.edukasi.ftpmanager.FtpApplication
import com.edukasi.ftpmanager.MainActivity
import com.edukasi.ftpmanager.R
import com.edukasi.ftpmanager.data.preferences.ProfilePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FtpServerService : Service() {

    companion object {
        const val ACTION_START = "com.edukasi.ftpmanager.ACTION_START"
        const val ACTION_STOP = "com.edukasi.ftpmanager.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow = _isRunningFlow.asStateFlow()

        private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
        val logsFlow = _logsFlow.asStateFlow()

        fun clearLogs() {
            _logsFlow.value = emptyList()
        }

        fun addLog(msg: String) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val formatted = "[$timestamp] $msg"
            val current = _logsFlow.value.takeLast(100).toMutableList()
            current.add(formatted)
            _logsFlow.value = current
        }

        fun startService(context: Context) {
            val intent = Intent(context, FtpServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FtpServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var ftpServer: SimpleFtpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopServer()
            ACTION_START -> startServer()
            else -> startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (_isRunningFlow.value) return

        val prefs = ProfilePreferences(this)
        val config = prefs.getServerConfig()
        val ip = NetworkUtils.getLocalIpAddress(this) ?: "127.0.0.1"

        // WakeLock & WifiLock to prevent sleep
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FtpManager:FtpWakeLock")?.apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FtpManager:WifiLock")?.apply {
            acquire()
        }

        val notification = buildNotification("ftp://$ip:${config.port}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            ftpServer = SimpleFtpServer(config) { logMsg ->
                addLog(logMsg)
            }
            ftpServer?.start()
            _isRunningFlow.value = true
            addLog("Server FTP siap digunakan: ftp://$ip:${config.port}")
        } catch (e: Exception) {
            addLog("Gagal start server: ${e.message}")
            stopServer()
        }
    }

    private fun stopServer() {
        ftpServer?.stop()
        ftpServer = null
        _isRunningFlow.value = false

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(url: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FtpServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FtpApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.server_running))
            .setContentText("Akses file melalui: $url")
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop_server), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}

