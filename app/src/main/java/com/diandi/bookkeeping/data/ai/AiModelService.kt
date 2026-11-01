package com.diandi.bookkeeping.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AiModelService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 读取 OpenAI 兼容 GET {baseUrl}/models 的 data[].id 列表。 */
    suspend fun fetchModels(provider: AiProviderConfig): List<String> = withContext(Dispatchers.IO) {
        val key = provider.apiKey.trim()
        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        require(key.isNotBlank()) { "请先填写该提供商的 API Key。" }
        require(baseUrl.startsWith("https://", ignoreCase = true)) { "为保护 API Key，请使用 HTTPS Base URL。" }

        val request = Request.Builder()
            .url("$baseUrl/models")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("拉取模型失败：HTTP ${response.code}")
            val data = JSONObject(body).optJSONArray("data")
                ?: throw IOException("模型服务响应中没有 data 模型列表。")
            buildList {
                for (index in 0 until data.length()) {
                    val id = data.optJSONObject(index)?.optString("id").orEmpty().trim()
                    if (id.isNotEmpty()) add(id)
                }
            }.distinct().sorted()
        }
    }
}
