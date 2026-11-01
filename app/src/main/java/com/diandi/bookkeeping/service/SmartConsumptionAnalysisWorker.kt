package com.diandi.bookkeeping.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.SmartConsumptionAnalyzer
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class SmartConsumptionAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!AiAnalysisPrefs.isEnabled(applicationContext)) return Result.success()
        SmartConsumptionAnalyzer.analyze(applicationContext)
        // 失败原因会显示在 AI 分析页；周期任务保留，下一日继续尝试。
        return Result.success()
    }
}

object AiAnalysisScheduler {
    private const val DAILY_WORK_NAME = "diandi_smart_consumption_analysis_daily"

    /** 应用启动时使用 KEEP，避免每次打开应用都把下次执行时间往后推。 */
    fun ensureScheduled(context: Context) = enqueue(context, ExistingPeriodicWorkPolicy.KEEP)

    /** 用户开启或修改定时时间后，按当前设置重置每日任务。 */
    fun reschedule(context: Context) = enqueue(context, ExistingPeriodicWorkPolicy.REPLACE)

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(DAILY_WORK_NAME)
    }

    private fun enqueue(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val appContext = context.applicationContext
        if (!AiAnalysisPrefs.isEnabled(appContext)) {
            cancel(appContext)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SmartConsumptionAnalysisWorker>(
            1, TimeUnit.DAYS, 15, TimeUnit.MINUTES
        )
            .setInitialDelay(delayUntilNextRun(AiAnalysisPrefs.dailyTime(appContext)), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(DAILY_WORK_NAME, policy, request)
    }

    private fun delayUntilNextRun(timeText: String): Long {
        val time = runCatching { LocalTime.parse(timeText) }
            .getOrDefault(LocalTime.of(20, 0))
        val now = ZonedDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), time).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}
