package com.diandi.bookkeeping.service

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
import androidx.core.app.NotificationCompat
import com.diandi.bookkeeping.MainActivity
import com.diandi.bookkeeping.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 常驻前台通知：每 2 分钟刷新攒钱进度，提供「消费 / 收益」快捷入口。
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification("加载中…", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        handler.post(refreshRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    private fun updateNotification() {
        val (text, progress) = computeProgressText()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text, progress))
    }

    private fun computeProgressText(): Pair<String, Int> {
        return try {
            val dao = AppDatabase.getInstance(applicationContext).transactionDao()
            val (balance, targetSum) = runBlocking {
                val c = dao.getTotalCorrection().first()
                val inc = dao.getTotalIncome().first()
                val exp = dao.getTotalExpense().first()
                val goals = dao.getAllGoals().first()
                val bal = c + inc - exp
                val target = goals.sumOf { it.targetAmount }
                bal to target
            }
            val pct = if (targetSum > 0) {
                ((balance / targetSum) * 100).coerceIn(0.0, 999.0)
            } else 0.0
            val text = if (targetSum > 0) {
                "攒钱进度 ${"%.0f".format(pct)}% · 余额¥${"%.0f".format(balance)} / 目标¥${"%.0f".format(targetSum)}"
            } else {
                "余额 ¥${"%.2f".format(balance)} · 暂无攒钱目标"
            }
            text to pct.toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            ("点滴记账运行中") to 0
        }
    }

    private fun buildNotification(content: String, progress: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val expensePi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_ADD_EXPENSE
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val incomePi = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_ADD_INCOME
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("点滴记账")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "消费", expensePi)
            .addAction(0, "收益", incomePi)

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
                description = "显示攒钱进度，防止进程被系统回收"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "diandi_keepalive"
        const val NOTIF_ID = 10086
        const val REFRESH_MS = 2 * 60 * 1000L
        const val ACTION_ADD_EXPENSE = "com.diandi.bookkeeping.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "com.diandi.bookkeeping.ADD_INCOME"

        fun start(context: Context) {
            val i = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
