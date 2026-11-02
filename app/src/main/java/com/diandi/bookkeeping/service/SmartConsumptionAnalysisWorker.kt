package com.diandi.bookkeeping.service

import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteListenableWorker
import androidx.work.multiprocess.RemoteWorkerService
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.AiAnalysisThresholdCounter
import com.diandi.bookkeeping.data.ai.SmartConsumptionAnalyzer
import kotlinx.coroutines.CancellationException

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
                ?: AiAnalysisScheduler.SOURCE_MANUAL
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
    private const val DAILY_WORK_NAME = "diandi_smart_consumption_analysis_daily_remote_v2"
    const val IMMEDIATE_WORK_NAME = "diandi_smart_consumption_analysis_immediate"
    const val KEY_TRIGGER_SOURCE = "analysis_trigger_source"
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_THRESHOLD = "threshold"

    /** 应用升级后清理旧版每日定时任务，避免阈值模式与旧定时任务并行。 */
    fun cancelDailySchedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(LEGACY_DAILY_WORK_NAME)
        workManager.cancelUniqueWork(DAILY_WORK_NAME)
    }

    /** 账单成功入库后调用。收入、支出均计数；仅在智能分析启用时累计。 */
    fun onBillInserted(context: Context) {
        val appContext = context.applicationContext
        if (!AiAnalysisPrefs.isEnabled(appContext)) return
        try {
            val update = AiAnalysisThresholdCounter.recordNewBill(appContext)
            if (update.reachedThreshold && !analyzeNow(appContext, SOURCE_THRESHOLD)) {
                AiAnalysisPrefs.saveError(
                    appContext,
                    "新增账单已达到触发阈值，但无法将分析加入后台队列，请打开 AI 分析页查看状态。"
                )
            }
        } catch (e: Exception) {
            AiAnalysisPrefs.saveError(appContext, "更新账单触发计数失败：${e.message ?: "未知错误"}")
        }
    }

    /** 手动、首次或阈值触发的分析都在独立进程执行。阈值触发追加排队，不丢并发周期。 */
    fun analyzeNow(context: Context, source: String = SOURCE_MANUAL): Boolean {
        val appContext = context.applicationContext
        if (!AiAnalysisPrefs.isEnabled(appContext)) return false
        return try {
            if (source != SOURCE_THRESHOLD) AiAnalysisPrefs.setAnalyzing(appContext, true)
            val request = OneTimeWorkRequestBuilder<SmartConsumptionAnalysisWorker>()
                .setConstraints(networkConstraints())
                .setInputData(remoteWorkerData(appContext, source))
                .build()
            val policy = if (source == SOURCE_THRESHOLD) {
                ExistingWorkPolicy.APPEND_OR_REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                policy,
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
        val appContext = context.applicationContext
        cancelDailySchedule(appContext)
        WorkManager.getInstance(appContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        AiAnalysisPrefs.setAnalyzing(appContext, false)
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
}
