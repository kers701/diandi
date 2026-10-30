package com.diandi.bookkeeping.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.diandi.bookkeeping.QuickAddActivity
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.util.AppPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 常驻前台通知（独立进程 :notify），策略对齐 wallpaper_app：
 * - PARTIAL_WAKE_LOCK
 * - START_STICKY
 * - onTaskRemoved 后 AlarmManager 自启
 * - 点击通知打开独立记账页（不进主界面栈）
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!AppPrefs.isNotifyEnabled(this@KeepAliveService)) {
                stopSelf()
                return
            }
            updateNotification()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "diandi:fgs")
            .apply { setReferenceCounted(false) }
        createChannel()
        val notification = buildNotification("加载中…", 0)
        startFg(notification)
        acquireWake()
        handler.post(refreshRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppPrefs.isNotifyEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!AppPrefs.isNotifyEnabled(this)) return
        try {
            val restart = Intent(applicationContext, KeepAliveService::class.java)
            val pi = PendingIntent.getService(
                applicationContext, 99, restart,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1200L,
                pi
            )
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        releaseWake()
        super.onDestroy()
    }

    private fun startFg(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIF_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    private fun acquireWake() {
        try {
            if (wakeLock?.isHeld != true) wakeLock?.acquire(10 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWake() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun updateNotification() {
        val (text, progress) = computeProgressText()
        val n = buildNotification(text, progress)
        startFg(n)
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, n)
    }

    private fun computeProgressText(): Pair<String, Int> {
        return try {
            val dao = AppDatabase.getInstance(applicationContext).transactionDao()
            val (balance, targetSum) = runBlocking {
                val c = dao.getTotalCorrection().first()
                val inc = dao.getTotalIncome().first()
                val exp = dao.getTotalExpense().first()
                val goals = dao.getAllGoals().first()
                (c + inc - exp) to goals.sumOf { it.targetAmount }
            }
            val pct = if (targetSum > 0) {
                ((balance / targetSum) * 100).coerceIn(0.0, 999.0)
            } else 0.0
            val text = if (targetSum > 0) {
                "攒钱进度 ${"%.0f".format(pct)}% · 余额¥${"%.0f".format(balance)} / 目标¥${"%.0f".format(targetSum)}"
            } else {
                "余额 ¥${"%.2f".format(balance)} · 点击记一笔"
            }
            text to pct.toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            "点滴记账 · 点击记一笔" to 0
        }
    }

    private fun buildNotification(content: String, progress: Int): Notification {
        val openQuick = PendingIntent.getActivity(
            this, 0,
            Intent(this, QuickAddActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("点滴记账")
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

        fun start(context: Context) {
            if (!AppPrefs.isNotifyEnabled(context)) return
            val i = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
