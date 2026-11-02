package com.diandi.bookkeeping.data.ai

import android.content.Context
import android.content.Intent

/** 智能消费分析的本机配置与最近结果；API Key 不写入账单备份。 */
object AiAnalysisPrefs {
    private const val PREFS_NAME = "smart_consumption_analysis"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_DAILY_TIME = "daily_time"
    private const val KEY_PROVIDERS_JSON = "providers_json"
    private const val KEY_MODELS_JSON = "models_json"
    private const val KEY_MAX_OUTPUT_CHARS = "max_output_chars"
    private const val KEY_RESULT = "result"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ANALYZING = "analyzing"
    private const val KEY_LAST_TRIGGER_AT = "last_trigger_at"
    private const val KEY_LAST_TRIGGER_SOURCE = "last_trigger_source"

    const val ACTION_STATE_CHANGED = "com.diandi.bookkeeping.action.AI_ANALYSIS_STATE_CHANGED"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_RESULT = "result"
    const val EXTRA_LAST_SUCCESS_AT = "last_success_at"
    const val EXTRA_LAST_ERROR = "last_error"
    const val EXTRA_ANALYZING = "analyzing"
    const val EXTRA_LAST_TRIGGER_AT = "last_trigger_at"
    const val EXTRA_LAST_TRIGGER_SOURCE = "last_trigger_source"

    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DEFAULT_DAILY_TIME = "20:00"
    const val DEFAULT_MAX_OUTPUT_CHARS = 500
    const val MIN_OUTPUT_CHARS = 100
    const val MAX_OUTPUT_CHARS = 999

    data class Candidate(val provider: AiProviderConfig, val model: AiModelEntry)

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
        notifyStateChanged(context)
    }

    fun apiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "").orEmpty()
    fun baseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty().ifBlank { DEFAULT_BASE_URL }
    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
    fun dailyTime(context: Context): String =
        prefs(context).getString(KEY_DAILY_TIME, DEFAULT_DAILY_TIME).orEmpty().ifBlank { DEFAULT_DAILY_TIME }

    /** 旧版单服务商配置仅在新版配置键从未写入时迁移显示。 */
    fun providers(context: Context): List<AiProviderConfig> {
        val preferences = prefs(context)
        if (preferences.contains(KEY_PROVIDERS_JSON)) {
            return AiModelConfigCodec.providersFromJson(preferences.getString(KEY_PROVIDERS_JSON, "[]"))
        }
        val legacyKey = apiKey(context).trim()
        if (legacyKey.isBlank()) return emptyList()
        val legacyModel = model(context).trim()
        return listOf(
            AiProviderConfig(
                id = "legacy",
                name = "默认提供商",
                apiKey = legacyKey,
                baseUrl = baseUrl(context),
                modelNamesText = legacyModel
            )
        )
    }

    /** 旧配置可迁移为一个提供商和一个默认模型。 */
    fun models(context: Context): List<AiModelEntry> {
        val preferences = prefs(context)
        if (preferences.contains(KEY_MODELS_JSON)) {
            return AiModelConfigCodec.modelsFromJson(preferences.getString(KEY_MODELS_JSON, "[]"))
                .sortedBy { it.priority }
        }
        var priority = 0
        return providers(context).flatMap { provider ->
            val names = provider.modelNames().ifEmpty {
                if (provider.id == "legacy") listOf(model(context).trim()).filter { it.isNotBlank() }
                else emptyList()
            }
            names.map { name ->
                AiModelEntry(providerId = provider.id, modelName = name, priority = priority++)
            }
        }
    }

    fun enabledCandidates(context: Context): List<Candidate> {
        val providerMap = providers(context)
            .filter { it.enabled && it.apiKey.isNotBlank() && it.baseUrl.startsWith("https://", true) }
            .associateBy { it.id }
        return models(context)
            .filter { it.enabled && it.modelName.isNotBlank() }
            .sortedBy { it.priority }
            .mapNotNull { model -> providerMap[model.providerId]?.let { Candidate(it, model) } }
    }

    /** 保存提供商并将新写入的手动模型合并进模型组，保留现有开关和顺序。 */
    fun saveProviders(context: Context, providers: List<AiProviderConfig>) {
        val oldModels = models(context)
        val validIds = providers.map { it.id }.toSet()
        val nextModels = oldModels.filter { it.providerId in validIds }.toMutableList()
        val keys = nextModels.map { "${it.providerId}::${it.modelName}" }.toMutableSet()
        var nextPriority = (nextModels.maxOfOrNull { it.priority } ?: -1) + 1
        providers.forEach { provider ->
            provider.modelNames().forEach { name ->
                if (keys.add("${provider.id}::$name")) {
                    nextModels += AiModelEntry(
                        providerId = provider.id,
                        modelName = name,
                        priority = nextPriority++
                    )
                }
            }
        }
        prefs(context).edit()
            .putString(KEY_PROVIDERS_JSON, AiModelConfigCodec.providersToJson(providers))
            .putString(KEY_MODELS_JSON, AiModelConfigCodec.modelsToJson(nextModels))
            .commit()
    }

    fun saveModels(context: Context, models: List<AiModelEntry>) {
        prefs(context).edit()
            .putString(KEY_MODELS_JSON, AiModelConfigCodec.modelsToJson(models))
            .commit()
    }

    /** 合并服务商从 /models 拉取的名称；已存在模型的启用状态和顺序不变。 */
    fun mergeProviderModels(context: Context, providerId: String, names: List<String>): Int {
        val cleanNames = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanNames.isEmpty()) return 0
        val currentProviders = providers(context)
        val provider = currentProviders.firstOrNull { it.id == providerId } ?: return 0
        val currentModels = models(context).toMutableList()
        val keys = currentModels.map { "${it.providerId}::${it.modelName}" }.toMutableSet()
        var priority = (currentModels.maxOfOrNull { it.priority } ?: -1) + 1
        var added = 0
        cleanNames.forEach { name ->
            if (keys.add("$providerId::$name")) {
                currentModels += AiModelEntry(providerId = providerId, modelName = name, priority = priority++)
                added++
            }
        }
        val mergedNames = (provider.modelNames() + cleanNames).distinct()
        val updatedProviders = currentProviders.map {
            if (it.id == providerId) it.copy(modelNamesText = mergedNames.joinToString("\n")) else it
        }
        prefs(context).edit()
            .putString(KEY_PROVIDERS_JSON, AiModelConfigCodec.providersToJson(updatedProviders))
            .putString(KEY_MODELS_JSON, AiModelConfigCodec.modelsToJson(currentModels))
            .commit()
        return added
    }

    fun setDailyTime(context: Context, value: String) {
        prefs(context).edit().putString(KEY_DAILY_TIME, value.trim()).commit()
    }

    fun maxOutputChars(context: Context): Int = prefs(context)
        .getInt(KEY_MAX_OUTPUT_CHARS, DEFAULT_MAX_OUTPUT_CHARS)
        .coerceIn(MIN_OUTPUT_CHARS, MAX_OUTPUT_CHARS)

    fun setMaxOutputChars(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_MAX_OUTPUT_CHARS, value.coerceIn(MIN_OUTPUT_CHARS, MAX_OUTPUT_CHARS))
            .commit()
    }

    /** 保留旧配置写入方法，便于兼容尚未迁移的安装版本。 */
    fun saveConfiguration(context: Context, apiKey: String, baseUrl: String, model: String, dailyTime: String) {
        prefs(context).edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL, model.trim())
            .putString(KEY_DAILY_TIME, dailyTime.trim())
            .commit()
    }

    fun result(context: Context): String = prefs(context).getString(KEY_RESULT, "").orEmpty()
    fun lastSuccessAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SUCCESS_AT, 0L)
    fun lastError(context: Context): String = prefs(context).getString(KEY_LAST_ERROR, "").orEmpty()
    fun isAnalyzing(context: Context): Boolean = prefs(context).getBoolean(KEY_ANALYZING, false)
    fun lastTriggerAt(context: Context): Long = prefs(context).getLong(KEY_LAST_TRIGGER_AT, 0L)
    fun lastTriggerSource(context: Context): String =
        prefs(context).getString(KEY_LAST_TRIGGER_SOURCE, "").orEmpty()

    /** 仅在 Worker 真正开始执行时记录，不代表只是加入了队列。 */
    fun markTriggered(context: Context, source: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_TRIGGER_AT, System.currentTimeMillis())
            .putString(KEY_LAST_TRIGGER_SOURCE, source)
            .putBoolean(KEY_ANALYZING, true)
            .commit()
        notifyStateChanged(context)
    }

    fun setAnalyzing(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANALYZING, value).commit()
        notifyStateChanged(context)
    }

    fun saveResult(context: Context, text: String, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_RESULT, text.take(maxOutputChars(context)))
            .putLong(KEY_LAST_SUCCESS_AT, timestamp)
            .putBoolean(KEY_ANALYZING, false)
            .remove(KEY_LAST_ERROR)
            .commit()
        notifyStateChanged(context)
    }

    fun saveError(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message.take(240)).commit()
        notifyStateChanged(context)
    }

    private fun notifyStateChanged(context: Context) {
        val preferences = prefs(context)
        val intent = Intent(ACTION_STATE_CHANGED)
            .setPackage(context.packageName)
            .putExtra(EXTRA_ENABLED, preferences.getBoolean(KEY_ENABLED, false))
            .putExtra(EXTRA_RESULT, preferences.getString(KEY_RESULT, "").orEmpty())
            .putExtra(EXTRA_LAST_SUCCESS_AT, preferences.getLong(KEY_LAST_SUCCESS_AT, 0L))
            .putExtra(EXTRA_LAST_ERROR, preferences.getString(KEY_LAST_ERROR, "").orEmpty())
            .putExtra(EXTRA_ANALYZING, preferences.getBoolean(KEY_ANALYZING, false))
            .putExtra(EXTRA_LAST_TRIGGER_AT, preferences.getLong(KEY_LAST_TRIGGER_AT, 0L))
            .putExtra(EXTRA_LAST_TRIGGER_SOURCE, preferences.getString(KEY_LAST_TRIGGER_SOURCE, "").orEmpty())
        context.applicationContext.sendBroadcast(intent)
    }
}
