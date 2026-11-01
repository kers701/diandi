package com.diandi.bookkeeping.ui.screens

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.service.AiAnalysisScheduler
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(onBack: () -> Unit, onOpenModelConfig: () -> Unit) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(AiAnalysisPrefs.isEnabled(context)) }
    var dailyTime by remember { mutableStateOf(AiAnalysisPrefs.dailyTime(context)) }
    var outputChars by remember { mutableStateOf(AiAnalysisPrefs.maxOutputChars(context).toString()) }
    var analysisText by remember { mutableStateOf(AiAnalysisPrefs.result(context)) }
    var lastSuccessAt by remember { mutableLongStateOf(AiAnalysisPrefs.lastSuccessAt(context)) }
    var lastError by remember { mutableStateOf(AiAnalysisPrefs.lastError(context)) }
    var enabledModelCount by remember { mutableIntStateOf(AiAnalysisPrefs.enabledCandidates(context).size) }
    var isAnalyzing by remember { mutableStateOf(AiAnalysisPrefs.isAnalyzing(context)) }
    var autoAnalysisAttempted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showModelConfigAction by remember { mutableStateOf(false) }

    fun refreshState() {
        analysisText = AiAnalysisPrefs.result(context)
        lastSuccessAt = AiAnalysisPrefs.lastSuccessAt(context)
        lastError = AiAnalysisPrefs.lastError(context)
        enabledModelCount = AiAnalysisPrefs.enabledCandidates(context).size
        isAnalyzing = AiAnalysisPrefs.isAnalyzing(context)
    }

    fun saveSettings(): String? {
        val parsedTime = runCatching { LocalTime.parse(dailyTime.trim()) }.getOrNull()
        val parsedChars = outputChars.toIntOrNull()
        return when {
            parsedTime == null -> "时间格式无效，请使用 20:00 这样的 HH:mm 格式。"
            parsedChars == null || parsedChars !in AiAnalysisPrefs.MIN_OUTPUT_CHARS..AiAnalysisPrefs.MAX_OUTPUT_CHARS ->
                "最大输出字数须为 ${AiAnalysisPrefs.MIN_OUTPUT_CHARS}–${AiAnalysisPrefs.MAX_OUTPUT_CHARS}。"
            else -> {
                dailyTime = parsedTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                outputChars = parsedChars.toString()
                AiAnalysisPrefs.setDailyTime(context, dailyTime)
                AiAnalysisPrefs.setMaxOutputChars(context, parsedChars)
                null
            }
        }
    }

    fun runAnalysisNow() {
        if (isAnalyzing) return
        isAnalyzing = true
        message = null
        if (!AiAnalysisScheduler.analyzeNow(context)) {
            refreshState()
            message = lastError.ifBlank { "无法将分析加入后台队列，请检查智能分析是否开启。" }
        } else {
            message = "分析已加入独立后台进程队列，可离开页面等待完成。"
        }
    }

    LaunchedEffect(enabled, enabledModelCount, analysisText) {
        if (enabled && enabledModelCount > 0 && analysisText.isBlank() && !autoAnalysisAttempted) {
            autoAnalysisAttempted = true
            runAnalysisNow()
        }
    }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(
            "smart_consumption_analysis", android.content.Context.MODE_MULTI_PROCESS
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshState()
            enabled = AiAnalysisPrefs.isEnabled(context)
            dailyTime = AiAnalysisPrefs.dailyTime(context)
            outputChars = AiAnalysisPrefs.maxOutputChars(context).toString()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: android.content.Context?, intent: Intent?) {
                val update = intent ?: return
                enabled = update.getBooleanExtra(AiAnalysisPrefs.EXTRA_ENABLED, enabled)
                analysisText = update.getStringExtra(AiAnalysisPrefs.EXTRA_RESULT) ?: analysisText
                lastSuccessAt = update.getLongExtra(AiAnalysisPrefs.EXTRA_LAST_SUCCESS_AT, lastSuccessAt)
                lastError = update.getStringExtra(AiAnalysisPrefs.EXTRA_LAST_ERROR) ?: lastError
                isAnalyzing = update.getBooleanExtra(AiAnalysisPrefs.EXTRA_ANALYZING, isAnalyzing)
                enabledModelCount = AiAnalysisPrefs.enabledCandidates(context).size
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AiAnalysisPrefs.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            context.unregisterReceiver(receiver)
        }
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
                        if (enabled) {
                            Text(
                                "开启后按设定时间分析；没有历史结果时会立即分析一次。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { turnOn ->
                            if (turnOn) {
                                val configError = saveSettings()
                                when {
                                    configError != null -> message = configError
                                    AiAnalysisPrefs.enabledCandidates(context).isEmpty() -> {
                                        showModelConfigAction = true
                                        message = "请先进入模型配置，添加并启用至少一个含 API Key 的提供商和模型。"
                                    }
                                    else -> {
                                        showModelConfigAction = false
                                        enabled = true
                                        AiAnalysisPrefs.setEnabled(context, true)
                                        AiAnalysisScheduler.reschedule(context)
                                        if (AiAnalysisPrefs.result(context).isBlank()) {
                                            runAnalysisNow()
                                        } else {
                                            message = "已有分析结果，将按每日时间更新。"
                                        }
                                    }
                                }
                            } else {
                                enabled = false
                                AiAnalysisPrefs.setEnabled(context, false)
                                AiAnalysisScheduler.cancel(context)
                                message = null
                                showModelConfigAction = false
                            }
                        }
                    )
                }
            }

            if (enabled) {
                OutlinedButton(onClick = onOpenModelConfig, modifier = Modifier.fillMaxWidth()) {
                    Text("模型配置…（提供商、模型组与查询顺序）")
                }
                Text(
                    "当前可用于查询的已启用模型：$enabledModelCount 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                OutlinedTextField(
                    value = outputChars,
                    onValueChange = { text -> outputChars = text.filter { it.isDigit() }.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最大输出字数（${AiAnalysisPrefs.MIN_OUTPUT_CHARS}–${AiAnalysisPrefs.MAX_OUTPUT_CHARS}）") },
                    supportingText = { Text("模型会收到该字数要求，保存结果时也会按上限截断。") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        val error = saveSettings()
                        if (error != null) {
                            message = error
                        } else {
                            if (enabled) {
                                AiAnalysisScheduler.reschedule(context)
                                if (AiAnalysisPrefs.result(context).isBlank()) runAnalysisNow()
                            }
                            message = "定时时间和输出字数已保存。"
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存设置") }

                Text(
                    "每次分析都是独立的新对话；只发送最近 30 天最多 120 笔账单与攒钱目标，包含日期、金额、类目、标签和备注。系统按模型组顺序尝试已启用模型，首个有效回复即停止。账单会发送到你配置的 AI 服务商，请确认其隐私政策与费用。后台执行需要联网，系统省电策略可能使时间稍有延迟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        val error = saveSettings()
                        if (error != null) message = error else runAnalysisNow()
                    },
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
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = {
                message = null
                showModelConfigAction = false
            },
            title = { Text("AI 消费分析") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    message = null
                    if (showModelConfigAction) onOpenModelConfig()
                    showModelConfigAction = false
                }) {
                    Text(if (showModelConfigAction) "去配置模型" else "确定")
                }
            },
            dismissButton = {
                if (showModelConfigAction) {
                    TextButton(onClick = {
                        message = null
                        showModelConfigAction = false
                    }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
