package com.diandi.bookkeeping.service

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.diandi.bookkeeping.QuickAddActivity
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.util.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    private var restartAllowed = true

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!AppPrefs.isNotifyEnabled(this@KeepAliveService)) {
                restartAllowed = false
                stopSelf()
                return
            }
            if (!updateNotification()) return
            acquireWake()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "diandi:fgs")
                .apply { setReferenceCounted(false) }
            createChannel()
            if (!startFg(buildNotification("点滴记账", "加载中…", 0))) return
            acquireWake()
            handler.post(refreshRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Service initialization failed", e)
            restartAllowed = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppPrefs.isNotifyEnabled(this)) {
            restartAllowed = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            restartAllowed = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!foregroundStarted || !updateNotification()) {
            restartAllowed = false
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (foregroundStarted && restartAllowed && AppPrefs.isNotifyEnabled(this)) {
            scheduleRestartAlarm(this)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        releaseWake()
        if (foregroundStarted && restartAllowed && AppPrefs.isNotifyEnabled(this)) {
            scheduleRestartAlarm(this)
        }
        super.onDestroy()
    }

    private fun startFg(notification: Notification): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            foregroundStarted = true
            return true
        } catch (first: Exception) {
            Log.e(TAG, "Typed foreground start failed", first)
            try {
                startForeground(NOTIF_ID, notification)
                foregroundStarted = true
                return true
            } catch (fallback: Exception) {
                Log.e(TAG, "Foreground service could not start", fallback)
                restartAllowed = false
                stopSelf()
                return false
            }
        }
    }

    private fun acquireWake() {
        try {
            if (wakeLock?.isHeld != true) wakeLock?.acquire(15 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWake() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun updateNotification(): Boolean {
        if (!foregroundStarted) return false
        return try {
            val (title, text, progress) = computeProgressText()
            val n = buildNotification(title, text, progress)
            startFg(n)
        } catch (e: Exception) {
            Log.e(TAG, "Notification update failed", e)
            restartAllowed = false
            stopSelf()
            false
        }
    }

    private fun computeProgressText(): Triple<String, String, Int> {
        return try {
            val dao = AppDatabase.getInstance(applicationContext).transactionDao()
            val (balanceCents, targetSumCents) = runBlocking {
                val c = dao.getTotalCorrection().first()
                val inc = dao.getTotalIncome().first()
                val exp = dao.getTotalExpense().first()
                val goals = dao.getAllGoals().first()
                (c + inc - exp) to goals.sumOf { it.targetAmountCents }
            }
            val pct = if (targetSumCents > 0L) {
                (balanceCents.toDouble() / targetSumCents.toDouble() * 100).coerceIn(0.0, 999.0)
            } else {
                0.0
            }
            val pctStr = "%.0f".format(pct)
            val balText = Money.format(balanceCents, grouping = false)
            val tgtText = Money.format(targetSumCents, grouping = false)
            val text = if (targetSumCents > 0L) {
                "攒钱进度 ${pctStr}% · 余额¥$balText / 目标¥$tgtText"
            } else {
                "余额 ¥$balText · 点击记一笔"
            }
            val title = if (targetSumCents > 0L) {
                "点滴记账 · 攒钱 ${pctStr}%"
            } else {
                "点滴记账"
            }
            Triple(title, text, pct.toInt().coerceIn(0, 100))
        } catch (_: Exception) {
            Triple("点滴记账", "点滴记账 · 点击记一笔", 0)
        }
    }

    private fun buildNotification(title: String, content: String, progress: Int): Notification {
        val openQuick = PendingIntent.getActivity(
            this, 0,
            Intent(this, QuickAddActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openQuick)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (progress in 1..99) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "点滴常驻",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示攒钱进度，独立进程防杀"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "diandi_keepalive"
        const val NOTIF_ID = 10086
        const val REFRESH_MS = 2 * 60 * 1000L
        const val ACTION_STOP = "com.diandi.bookkeeping.STOP_KEEPALIVE"
        private const val TAG = "KeepAliveService"

        fun start(context: Context) {
            if (!AppPrefs.isNotifyEnabled(context)) return
            if (!canShowNotification(context)) {
                Log.w(TAG, "Notification permission or channel is disabled; service not started")
                return
            }
            val i = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not request foreground service start", e)
            }
        }

        fun canShowNotification(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = context.getSystemService(NotificationManager::class.java)
                    ?.getNotificationChannel(CHANNEL_ID)
                if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
            }
            return true
        }

        fun stop(context: Context) {
            try {
                context.stopService(
                    Intent(context, KeepAliveService::class.java).apply {
                        action = ACTION_STOP
                    }
                )
            } catch (_: Exception) {
            }
        }

        fun scheduleRestartAlarm(context: Context) {
            if (!AppPrefs.isNotifyEnabled(context)) return
            try {
                val app = context.applicationContext
                val intent = Intent(app, RestartReceiver::class.java).apply {
                    action = RestartReceiver.ACTION_RESTART
                }
                val pi = PendingIntent.getBroadcast(
                    app, 98, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val trigger = SystemClock.elapsedRealtime() + 1500L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                }
            } catch (_: Exception) {
            }
        }
    }
}
