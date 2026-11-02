package com.diandi.bookkeeping.service

import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteListenableWorker
import androidx.work.multiprocess.RemoteWorkerService
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.SmartConsumptionAnalyzer
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class SmartConsumptionAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : RemoteCoroutineWorker(appContext, params) {
    override suspend fun doRemoteWork(): ListenableWorker.Result {
        val context = applicationContext
        if (!AiAnalysisPrefs.isEnabled(context)) return ListenableWorker.Result.success()
        AiAnalysisPrefs.markTriggered(
            context,
            inputData.getString(AiAnalysisScheduler.KEY_TRIGGER_SOURCE)
                ?: AiAnalysisScheduler.SOURCE_SCHEDULED
        )
        return try {
            val success = SmartConsumptionAnalyzer.analyze(context)
            ListenableWorker.Result.success(
                Data.Builder()
                    .putBoolean(KEY_ANALYSIS_SUCCEEDED, success)
                    .putString(KEY_ANALYSIS_RESULT, AiAnalysisPrefs.result(context))
                    .putString(KEY_ANALYSIS_ERROR, AiAnalysisPrefs.lastError(context))
                    .build()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiAnalysisPrefs.saveError(context, e.message ?: "后台分析失败，请重试。")
            ListenableWorker.Result.success(
                Data.Builder()
                    .putBoolean(KEY_ANALYSIS_SUCCEEDED, false)
                    .putString(KEY_ANALYSIS_ERROR, AiAnalysisPrefs.lastError(context))
                    .build()
            )
        } finally {
            AiAnalysisPrefs.setAnalyzing(context, false)
        }
    }

    companion object {
        const val KEY_ANALYSIS_SUCCEEDED = "analysis_succeeded"
        const val KEY_ANALYSIS_RESULT = "analysis_result"
        const val KEY_ANALYSIS_ERROR = "analysis_error"
    }
}

object AiAnalysisScheduler {
    private const val LEGACY_DAILY_WORK_NAME = "diandi_smart_consumption_analysis_daily"
    const val DAILY_WORK_NAME = "diandi_smart_consumption_analysis_daily_remote_v2"
    const val IMMEDIATE_WORK_NAME = "diandi_smart_consumption_analysis_immediate"
    const val KEY_TRIGGER_SOURCE = "analysis_trigger_source"
    const val SOURCE_SCHEDULED = "scheduled"
    const val SOURCE_MANUAL = "manual"

    /** 应用启动时使用 KEEP，避免每次打开应用都把下次执行时间往后推。 */
    fun ensureScheduled(context: Context) = enqueue(context, ExistingPeriodicWorkPolicy.KEEP)

    /** 用户开启或修改定时时间后，按当前设置重置每日任务。 */
    fun reschedule(context: Context) = enqueue(context, ExistingPeriodicWorkPolicy.REPLACE)

    /** 将手动/首次分析也放入独立进程，主界面退出不会取消正在执行的请求。 */
    fun analyzeNow(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!AiAnalysisPrefs.isEnabled(appContext)) return false
        return try {
            AiAnalysisPrefs.setAnalyzing(appContext, true)
            val request = OneTimeWorkRequestBuilder<SmartConsumptionAnalysisWorker>()
                .setConstraints(networkConstraints())
                .setInputData(remoteWorkerData(appContext, SOURCE_MANUAL))
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            true
        } catch (e: Exception) {
            AiAnalysisPrefs.setAnalyzing(appContext, false)
            AiAnalysisPrefs.saveError(appContext, e.message ?: "无法加入后台分析队列。")
            false
        }
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(LEGACY_DAILY_WORK_NAME)
        workManager.cancelUniqueWork(DAILY_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        AiAnalysisPrefs.setAnalyzing(context.applicationContext, false)
    }

    private fun enqueue(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelUniqueWork(LEGACY_DAILY_WORK_NAME)
        if (!AiAnalysisPrefs.isEnabled(appContext)) {
            cancel(appContext)
            return
        }
        val request = PeriodicWorkRequestBuilder<SmartConsumptionAnalysisWorker>(
            1, TimeUnit.DAYS, 15, TimeUnit.MINUTES
        )
            .setInitialDelay(delayUntilNextRun(AiAnalysisPrefs.dailyTime(appContext)), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints())
            .setInputData(remoteWorkerData(appContext, SOURCE_SCHEDULED))
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(DAILY_WORK_NAME, policy, request)
    }

    private fun remoteWorkerData(context: Context, triggerSource: String): Data {
        val service = ComponentName(context, RemoteWorkerService::class.java)
        return Data.Builder()
            .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, service.packageName)
            .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, service.className)
            .putString(KEY_TRIGGER_SOURCE, triggerSource)
            .build()
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun delayUntilNextRun(timeText: String): Long {
        val time = runCatching { LocalTime.parse(timeText) }
            .getOrDefault(LocalTime.of(20, 0))
        val now = ZonedDateTime.now()
        var next = LocalDateTime.of(now.toLocalDate(), time).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}
