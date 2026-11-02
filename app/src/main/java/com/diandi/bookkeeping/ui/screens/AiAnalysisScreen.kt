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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.AiAnalysisThresholdCounter
import com.diandi.bookkeeping.service.AiAnalysisScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(onBack: () -> Unit, onOpenModelConfig: () -> Unit) {
    val context = LocalContext.current
    val initialCounterState = remember(context) { AiAnalysisThresholdCounter.readState(context) }

    var enabled by remember { mutableStateOf(AiAnalysisPrefs.isEnabled(context)) }
    var thresholdText by remember { mutableStateOf(initialCounterState.threshold.toString()) }
    var billCount by remember { mutableIntStateOf(initialCounterState.count) }
    var outputChars by remember { mutableStateOf(AiAnalysisPrefs.maxOutputChars(context).toString()) }
    var analysisText by remember { mutableStateOf(AiAnalysisPrefs.result(context)) }
    var lastSuccessAt by remember { mutableLongStateOf(AiAnalysisPrefs.lastSuccessAt(context)) }
    var lastTriggerAt by remember { mutableLongStateOf(AiAnalysisPrefs.lastTriggerAt(context)) }
    var lastTriggerSource by remember { mutableStateOf(AiAnalysisPrefs.lastTriggerSource(context)) }
    var lastError by remember { mutableStateOf(AiAnalysisPrefs.lastError(context)) }
    var enabledModelCount by remember { mutableIntStateOf(AiAnalysisPrefs.enabledCandidates(context).size) }
    var isAnalyzing by remember { mutableStateOf(AiAnalysisPrefs.isAnalyzing(context)) }
    var analysisQueueStatus by remember { mutableStateOf("正在读取分析队列状态…") }
    var autoAnalysisAttempted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showModelConfigAction by remember { mutableStateOf(false) }

    fun refreshState() {
        analysisText = AiAnalysisPrefs.result(context)
        lastSuccessAt = AiAnalysisPrefs.lastSuccessAt(context)
        lastTriggerAt = AiAnalysisPrefs.lastTriggerAt(context)
        lastTriggerSource = AiAnalysisPrefs.lastTriggerSource(context)
        lastError = AiAnalysisPrefs.lastError(context)
        enabledModelCount = AiAnalysisPrefs.enabledCandidates(context).size
        isAnalyzing = AiAnalysisPrefs.isAnalyzing(context)
        val counterState = AiAnalysisThresholdCounter.readState(context)
        billCount = counterState.count
    }

    fun saveSettings(): String? {
        val parsedThreshold = thresholdText.toIntOrNull()
        val parsedChars = outputChars.toIntOrNull()
        return when {
            parsedThreshold == null || parsedThreshold !in
                AiAnalysisThresholdCounter.MIN_THRESHOLD..AiAnalysisThresholdCounter.MAX_THRESHOLD ->
                "账单触发阈值须为 ${AiAnalysisThresholdCounter.MIN_THRESHOLD}–${AiAnalysisThresholdCounter.MAX_THRESHOLD} 笔。"
            parsedChars == null || parsedChars !in AiAnalysisPrefs.MIN_OUTPUT_CHARS..AiAnalysisPrefs.MAX_OUTPUT_CHARS ->
                "最大输出字数须为 ${AiAnalysisPrefs.MIN_OUTPUT_CHARS}–${AiAnalysisPrefs.MAX_OUTPUT_CHARS}。"
            else -> {
                try {
                    val counterState = AiAnalysisThresholdCounter.setThreshold(context, parsedThreshold)
                    thresholdText = counterState.threshold.toString()
                    billCount = counterState.count
                    outputChars = parsedChars.toString()
                    AiAnalysisPrefs.setMaxOutputChars(context, parsedChars)
                    null
                } catch (error: Exception) {
                    "保存账单触发阈值失败：${error.message ?: "未知错误"}"
                }
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

    LaunchedEffect(enabled, context) {
        if (!enabled) {
            analysisQueueStatus = "未启用"
            return@LaunchedEffect
        }
        while (true) {
            analysisQueueStatus = withContext(Dispatchers.IO) {
                runCatching {
                    WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(AiAnalysisScheduler.IMMEDIATE_WORK_NAME)
                        .get(10, TimeUnit.SECONDS)
                }.fold(
                    onSuccess = { workInfos ->
                        when {
                            workInfos.any { it.state == WorkInfo.State.RUNNING } -> "正在执行分析"
                            workInfos.any { it.state == WorkInfo.State.ENQUEUED } -> "已排队，等待联网或系统执行"
                            workInfos.any { it.state == WorkInfo.State.BLOCKED } -> "等待前序分析完成"
                            workInfos.any { it.state == WorkInfo.State.FAILED } -> "最近任务执行失败"
                            workInfos.any { it.state == WorkInfo.State.SUCCEEDED } -> "最近任务已完成"
                            else -> "当前没有待执行的分析任务"
                        }
                    },
                    onFailure = { error -> "状态读取失败：${error.message ?: "未知错误"}" }
                )
            }
            delay(15_000)
        }
    }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(
            "smart_consumption_analysis", android.content.Context.MODE_MULTI_PROCESS
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshState()
            enabled = AiAnalysisPrefs.isEnabled(context)
            outputChars = AiAnalysisPrefs.maxOutputChars(context).toString()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: android.content.Context?, intent: Intent?) {
                val update = intent ?: return
                if (update.action == AiAnalysisThresholdCounter.ACTION_STATE_CHANGED) {
                    val state = AiAnalysisThresholdCounter.readState(context)
                    billCount = state.count
                    return
                }
                enabled = update.getBooleanExtra(AiAnalysisPrefs.EXTRA_ENABLED, enabled)
                analysisText = update.getStringExtra(AiAnalysisPrefs.EXTRA_RESULT) ?: analysisText
                lastSuccessAt = update.getLongExtra(AiAnalysisPrefs.EXTRA_LAST_SUCCESS_AT, lastSuccessAt)
                lastTriggerAt = update.getLongExtra(AiAnalysisPrefs.EXTRA_LAST_TRIGGER_AT, lastTriggerAt)
                lastTriggerSource = update.getStringExtra(AiAnalysisPrefs.EXTRA_LAST_TRIGGER_SOURCE)
                    ?: lastTriggerSource
                lastError = update.getStringExtra(AiAnalysisPrefs.EXTRA_LAST_ERROR) ?: lastError
                isAnalyzing = update.getBooleanExtra(AiAnalysisPrefs.EXTRA_ANALYZING, isAnalyzing)
                enabledModelCount = AiAnalysisPrefs.enabledCandidates(context).size
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AiAnalysisPrefs.ACTION_STATE_CHANGED).apply {
                addAction(AiAnalysisThresholdCounter.ACTION_STATE_CHANGED)
            },
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
                                "开启后按新增账单数触发；没有历史结果时会立即分析一次。",
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
                                        if (AiAnalysisPrefs.result(context).isBlank()) {
                                            runAnalysisNow()
                                        } else {
                                            message = "已有分析结果；新增账单累计达到阈值后会自动更新。"
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
                    value = thresholdText,
                    onValueChange = { thresholdText = it.filter { char -> char.isDigit() }.take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账单触发阈值（笔）") },
                    supportingText = {
                        Text("智能分析开启期间，每新增一笔收入或支出计数 +1；达到阈值后触发分析并归零。")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    "当前计数：$billCount / $thresholdText 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            if (enabled && AiAnalysisPrefs.result(context).isBlank()) runAnalysisNow()
                            message = "账单触发阈值和输出字数已保存。"
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存设置") }

                Text(
                    "计数以应用私有文件为准，并用文件锁处理主进程与通知进程的并发写入。每次分析都是独立的新对话；只发送最近 30 天最多 120 笔账单与攒钱目标，按模型组顺序尝试已启用模型。账单会发送到你配置的 AI 服务商；触发后需联网，若暂时离线将等待系统运行。",
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
                Text("分析任务队列：$analysisQueueStatus", style = MaterialTheme.typography.bodySmall)
                if (lastTriggerAt > 0L) {
                    val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(lastTriggerAt))
                    val sourceLabel = when (lastTriggerSource) {
                        AiAnalysisScheduler.SOURCE_MANUAL -> "手动"
                        AiAnalysisScheduler.SOURCE_THRESHOLD -> "账单阈值"
                        else -> "未知来源"
                    }
                    Text("最近实际触发：$formatted（$sourceLabel）", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("最近实际触发：暂无记录", style = MaterialTheme.typography.bodySmall)
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
