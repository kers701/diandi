package com.diandi.bookkeeping.data.ai

import android.content.Context
import com.diandi.bookkeeping.data.db.AppDatabase
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 每次请求都是新对话；候选模型按模型组次序逐一回退。 */
object SmartConsumptionAnalyzer {
    private const val MAX_TRANSACTIONS = 120
    private const val MAX_INPUT_CHARS = 12000
    private const val MAX_OUTPUT_TOKENS = 2000
    private val mutex = Mutex()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { analyzeLocked(context.applicationContext) }
    }

    private suspend fun analyzeLocked(context: Context): Boolean {
        if (!AiAnalysisPrefs.isEnabled(context)) return false
        AiAnalysisPrefs.saveError(context, "")
        val candidates = AiAnalysisPrefs.enabledCandidates(context)
        if (candidates.isEmpty()) {
            AiAnalysisPrefs.saveError(context, "没有可用模型：请在模型配置中启用提供商和模型，并填写 API Key。")
            return false
        }

        val today = LocalDate.now()
        val startDate = today.minusDays(29)
        val dao = AppDatabase.getInstance(context).transactionDao()
        val transactions = dao.getTransactionsForAiAnalysis(
            startDate.toString(), today.toString(), MAX_TRANSACTIONS
        )
        if (transactions.isEmpty()) {
            AiAnalysisPrefs.saveError(context, "最近 30 天没有账单，暂无可分析的数据。")
            return false
        }
        val goals = dao.getGoalsForBackup()
        val userContent = buildUserContent(startDate, today, transactions, goals)
        val maxChars = AiAnalysisPrefs.maxOutputChars(context)
        val systemContent = "你是个人记账应用中的消费行为分析助手。只依据用户提供的账单与攒钱目标做客观分析，不臆测缺失信息，不提供投资或借贷建议。回复请使用简体中文，最多 $maxChars 个汉字，控制在 3 至 5 条要点：指出消费结构或趋势、给出具体可执行的节流建议；若提供了攒钱目标，再给出与目标相关的储蓄建议。不要复述全部流水，不要追问，不使用表格。"
        val failures = mutableListOf<String>()

        for (candidate in candidates) {
            try {
                val response = request(candidate, systemContent, userContent, maxChars)
                if (response.isNotBlank()) {
                    AiAnalysisPrefs.saveResult(context, response.take(maxChars))
                    return true
                }
                failures += "${candidate.model.modelName}: 空回复"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "${candidate.model.modelName}: ${e.message.orEmpty().take(80)}"
            }
        }
        AiAnalysisPrefs.saveError(
            context,
            "所有已启用模型均未返回有效分析。" + failures.joinToString("；").take(180)
        )
        return false
    }

    private fun request(
        candidate: AiAnalysisPrefs.Candidate,
        systemContent: String,
        userContent: String,
        maxChars: Int
    ): String {
        val baseUrl = candidate.provider.baseUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            throw IOException("Base URL 必须使用 HTTPS")
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemContent))
            .put(JSONObject().put("role", "user").put("content", userContent))
        val requestJson = JSONObject()
            .put("model", candidate.model.modelName)
            .put("messages", messages)
            .put("temperature", 0.2)
            .put("max_tokens", (maxChars * 2).coerceIn(256, MAX_OUTPUT_TOKENS))
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer ${candidate.provider.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
        }
    }

    private fun buildUserContent(
        startDate: LocalDate,
        endDate: LocalDate,
        transactions: List<Transaction>,
        goals: List<SavingsGoal>
    ): String {
        val bills = buildString {
            transactions.forEach { tx ->
                val typeText = if (tx.type == "INCOME") "收入" else "支出"
                val amount = String.format(Locale.US, "%.2f", tx.amount)
                val tags = tx.tags.compact(40)
                val note = tx.note.compact(60)
                append(tx.toLocalDate())
                append(" | ").append(typeText)
                append(" | ¥").append(amount)
                append(" | 类目：").append(tx.category.compact(30))
                if (tags.isNotBlank()) append(" | 标签：").append(tags)
                if (note.isNotBlank()) append(" | 备注：").append(note)
                appendLine()
            }
        }
        val goalText = buildString {
            appendLine("攒钱目标：")
            if (goals.isEmpty()) {
                appendLine("暂无攒钱目标。")
            } else {
                goals.take(20).forEach { goal ->
                    append("- ").append(goal.title.compact(60))
                        .append("：当前 ¥").append(String.format(Locale.US, "%.2f", goal.currentAmount))
                        .append(" / 目标 ¥").append(String.format(Locale.US, "%.2f", goal.targetAmount))
                    if (goal.deadline.isNotBlank()) append("，截止 ").append(goal.deadline.compact(20))
                    appendLine()
                }
            }
        }
        val heading = "分析区间：$startDate 至 $endDate（近 30 天）。账单摘要最多 ${transactions.size} 笔，按时间倒序，超长内容会截断：\n"
        val ending = "\n请仅给出简短的消费分析与建议，不需要逐条复述账单。"
        val billsBudget = (MAX_INPUT_CHARS - heading.length - goalText.length - ending.length - 1)
            .coerceAtLeast(0)
        return heading + bills.take(billsBudget) + "\n" + goalText + ending
    }

    private fun String.compact(maxChars: Int): String =
        replace(Regex("\\s+"), " ").trim().take(maxChars)
}
