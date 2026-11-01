package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.SmartConsumptionAnalyzer
import com.diandi.bookkeeping.service.AiAnalysisScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(AiAnalysisPrefs.isEnabled(context)) }
    var apiKey by remember { mutableStateOf(AiAnalysisPrefs.apiKey(context)) }
    var baseUrl by remember { mutableStateOf(AiAnalysisPrefs.baseUrl(context)) }
    var model by remember { mutableStateOf(AiAnalysisPrefs.model(context)) }
    var dailyTime by remember { mutableStateOf(AiAnalysisPrefs.dailyTime(context)) }
    var analysisText by remember { mutableStateOf(AiAnalysisPrefs.result(context)) }
    var lastSuccessAt by remember { mutableLongStateOf(AiAnalysisPrefs.lastSuccessAt(context)) }
    var lastError by remember { mutableStateOf(AiAnalysisPrefs.lastError(context)) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshResult() {
        analysisText = AiAnalysisPrefs.result(context)
        lastSuccessAt = AiAnalysisPrefs.lastSuccessAt(context)
        lastError = AiAnalysisPrefs.lastError(context)
    }

    fun saveConfiguration(requireApiKey: Boolean): String? {
        val parsedTime = runCatching { LocalTime.parse(dailyTime.trim()) }.getOrNull()
        return when {
            requireApiKey && apiKey.isBlank() -> "请先填写 API Key。"
            baseUrl.isBlank() || !baseUrl.trim().startsWith("https://", ignoreCase = true) ->
                "请填写有效的 HTTPS Base URL。"
            model.isBlank() -> "请填写模型名称。"
            parsedTime == null -> "时间格式无效，请使用 20:00 这样的 HH:mm 格式。"
            else -> {
                dailyTime = parsedTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                AiAnalysisPrefs.saveConfiguration(context, apiKey, baseUrl, model, dailyTime)
                null
            }
        }
    }

    fun runAnalysisNow() {
        if (isAnalyzing) return
        isAnalyzing = true
        message = null
        scope.launch {
            val success = SmartConsumptionAnalyzer.analyze(context)
            refreshResult()
            isAnalyzing = false
            if (!success) message = lastError.ifBlank { "分析未完成，请检查 AI 配置和网络后重试。" }
        }
    }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(
            "smart_consumption_analysis", android.content.Context.MODE_PRIVATE
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshResult()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Text("  AI 消费分析")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("智能消费分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "开启后按设定时间分析；没有历史结果时会立即分析一次。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = !isAnalyzing,
                        onCheckedChange = { turnOn ->
                            if (turnOn) {
                                val configError = saveConfiguration(requireApiKey = true)
                                if (configError != null) {
                                    message = configError
                                } else {
                                    enabled = true
                                    AiAnalysisPrefs.setEnabled(context, true)
                                    AiAnalysisScheduler.reschedule(context)
                                    if (AiAnalysisPrefs.result(context).isBlank()) {
                                        runAnalysisNow()
                                    } else {
                                        message = "已有分析结果，将按每日时间更新。"
                                    }
                                }
                            } else {
                                enabled = false
                                AiAnalysisPrefs.setEnabled(context, false)
                                AiAnalysisScheduler.cancel(context)
                                message = "智能消费分析已关闭，后续不再自动发送账单数据。"
                            }
                        }
                    )
                }
            }

            Text("AI 模型配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                placeholder = { Text("填写你自己的模型服务 API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                placeholder = { Text(AiAnalysisPrefs.DEFAULT_BASE_URL) },
                singleLine = true
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") },
                placeholder = { Text(AiAnalysisPrefs.DEFAULT_MODEL) },
                singleLine = true
            )
            OutlinedTextField(
                value = dailyTime,
                onValueChange = { dailyTime = it.take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("每日分析时间（24 小时制 HH:mm）") },
                placeholder = { Text(AiAnalysisPrefs.DEFAULT_DAILY_TIME) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Text(
                "采用 wallpaper_app 同类的 OpenAI 兼容接口：Base URL 通常填写到 /v1，例如 https://api.openai.com/v1。API Key 保存在本机应用私有设置中。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    val configError = saveConfiguration(requireApiKey = enabled)
                    if (configError != null) {
                        message = configError
                    } else {
                        if (enabled) {
                            AiAnalysisScheduler.reschedule(context)
                            if (AiAnalysisPrefs.result(context).isBlank()) runAnalysisNow()
                        }
                        message = "AI 配置与定时时间已保存。"
                    }
                },
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存模型配置和定时时间") }

            Text(
                "每次分析都是独立的新对话，只发送最近 30 天最多 120 笔账单与当前攒钱目标；包含日期、金额、类目、标签和备注。分析请求会发送到你配置的 AI 服务商，请确认其隐私政策与费用。回复最多保存 500 字；后台任务需要联网，系统省电策略可能使执行稍有延迟。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { runAnalysisNow() },
                enabled = enabled && !isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isAnalyzing) "正在分析…" else "立即分析") }
            if (isAnalyzing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (lastSuccessAt > 0L) {
                val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(lastSuccessAt))
                Text("最近成功分析：$formatted", style = MaterialTheme.typography.bodySmall)
            }
            if (lastError.isNotBlank()) {
                Text(lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (analysisText.isNotBlank()) {
                Text("最近分析结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card {
                    Text(
                        analysisText,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("AI 消费分析") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("确定") } }
        )
    }
}
