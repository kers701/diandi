package com.diandi.bookkeeping.data.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一组 OpenAI 兼容服务商连接配置；modelNamesText 可为空，模型可以从 /models 拉取。 */
data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val modelNamesText: String = "",
    val enabled: Boolean = true
) {
    fun modelNames(): List<String> = modelNamesText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct()
        .toList()
}

/** 模型组中的可选模型；priority 越小越先尝试。 */
data class AiModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String = "",
    val modelName: String = "",
    val priority: Int = 0,
    val enabled: Boolean = true
)

object AiModelConfigCodec {
    fun providersToJson(items: List<AiProviderConfig>): String = JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("apiKey", item.apiKey)
                    .put("baseUrl", item.baseUrl)
                    .put("modelNamesText", item.modelNamesText)
                    .put("enabled", item.enabled)
            )
        }
    }.toString()

    fun providersFromJson(json: String?): List<AiProviderConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        AiProviderConfig(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = item.optString("name"),
                            apiKey = item.optString("apiKey"),
                            baseUrl = item.optString("baseUrl").ifBlank { "https://api.openai.com/v1" },
                            modelNamesText = item.optString("modelNamesText"),
                            enabled = item.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun modelsToJson(items: List<AiModelEntry>): String = JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject()
                    .put("id", item.id)
                    .put("providerId", item.providerId)
                    .put("modelName", item.modelName)
                    .put("priority", item.priority.coerceAtLeast(0))
                    .put("enabled", item.enabled)
            )
        }
    }.toString()

    fun modelsFromJson(json: String?): List<AiModelEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        AiModelEntry(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            providerId = item.optString("providerId"),
                            modelName = item.optString("modelName"),
                            priority = item.optInt("priority", index).coerceAtLeast(0),
                            enabled = item.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
