package com.diandi.bookkeeping.data.ai

import android.content.Context

/** 智能消费分析的私有本机设置与最近一次结果。API Key 不进入账单备份文件。 */
object AiAnalysisPrefs {
    private const val PREFS_NAME = "smart_consumption_analysis"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_DAILY_TIME = "daily_time"
    private const val KEY_RESULT = "result"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    private const val KEY_LAST_ERROR = "last_error"

    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DEFAULT_DAILY_TIME = "20:00"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun apiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "").orEmpty()
    fun baseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty().ifBlank { DEFAULT_BASE_URL }
    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
    fun dailyTime(context: Context): String =
        prefs(context).getString(KEY_DAILY_TIME, DEFAULT_DAILY_TIME).orEmpty().ifBlank { DEFAULT_DAILY_TIME }

    fun saveConfiguration(
        context: Context,
        apiKey: String,
        baseUrl: String,
        model: String,
        dailyTime: String
    ) {
        prefs(context).edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_MODEL, model.trim())
            .putString(KEY_DAILY_TIME, dailyTime.trim())
            .apply()
    }

    fun result(context: Context): String = prefs(context).getString(KEY_RESULT, "").orEmpty()
    fun lastSuccessAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SUCCESS_AT, 0L)
    fun lastError(context: Context): String = prefs(context).getString(KEY_LAST_ERROR, "").orEmpty()

    fun saveResult(context: Context, text: String, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_RESULT, text.take(500))
            .putLong(KEY_LAST_SUCCESS_AT, timestamp)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun saveError(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message.take(240)).apply()
    }
}
