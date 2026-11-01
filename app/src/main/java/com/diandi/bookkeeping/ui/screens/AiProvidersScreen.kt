package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.AiModelService
import com.diandi.bookkeeping.data.ai.AiProviderConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProvidersScreen(
    onBack: () -> Unit,
    onOpenModelGroup: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(AiAnalysisPrefs.providers(context)) }
    var editing by remember { mutableStateOf<AiProviderConfig?>(null) }
    var deleting by remember { mutableStateOf<AiProviderConfig?>(null) }
    var fetchingId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun saveProviders() {
        AiAnalysisPrefs.saveProviders(context, providers)
        providers = AiAnalysisPrefs.providers(context)
    }

    fun fetchModels(provider: AiProviderConfig) {
        if (fetchingId != null) return
        if (provider.apiKey.isBlank()) {
            notice = "请先编辑该提供商并填写 API Key。"
            return
        }
        saveProviders()
        scope.launch {
            fetchingId = provider.id
            try {
                val names = AiModelService.fetchModels(provider)
                if (names.isEmpty()) {
                    val manual = provider.modelNames()
                    if (manual.isEmpty()) {
                        notice = "服务商未返回模型列表。可在提供商编辑页手动填写模型名。"
                    } else {
                        val added = AiAnalysisPrefs.mergeProviderModels(context, provider.id, manual)
                        providers = AiAnalysisPrefs.providers(context)
                        notice = "在线列表为空，已将手动模型同步到模型组（新增 $added 个）。"
                    }
                } else {
                    val added = AiAnalysisPrefs.mergeProviderModels(context, provider.id, names)
                    providers = AiAnalysisPrefs.providers(context)
                    notice = "拉取到 ${names.size} 个模型，模型组新增 $added 个；可在模型组设置启用和顺序。"
                }
            } catch (error: Exception) {
                val manual = provider.modelNames()
                if (manual.isNotEmpty()) {
                    val added = AiAnalysisPrefs.mergeProviderModels(context, provider.id, manual)
                    providers = AiAnalysisPrefs.providers(context)
                    notice = "在线拉取失败（${error.message.orEmpty().take(100)}），已使用手动模型（新增 $added 个）。"
                } else {
                    notice = "模型拉取失败：${error.message.orEmpty().take(160)}"
                }
            } finally {
                fetchingId = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置 · 提供商") },
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
            Text(
                "可配置多个 OpenAI 兼容服务商。模型名称允许留空；保存 API Key 与 Base URL 后，可拉取 /models，或手动逐行填写模型名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    editing = AiProviderConfig(
                        name = "提供商 ${providers.size + 1}",
                        baseUrl = AiAnalysisPrefs.DEFAULT_BASE_URL
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("添加提供商") }

            providers.forEach { provider ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            provider.name + if (provider.enabled) "" else "（已禁用）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${provider.baseUrl}\nAPI Key：${if (provider.apiKey.isBlank()) "未填写" else "已设置"} · 模型名：${provider.modelNames().size} 个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = provider }) { Text("编辑") }
                            TextButton(
                                enabled = fetchingId == null,
                                onClick = { fetchModels(provider) }
                            ) { Text(if (fetchingId == provider.id) "拉取中…" else "拉取模型") }
                            TextButton(onClick = { deleting = provider }) { Text("删除") }
                        }
                    }
                }
            }

            if (providers.isEmpty()) {
                Text("尚未配置提供商。至少添加一个服务商，并填写 API Key。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = { saveProviders(); notice = "提供商配置已保存。" }, modifier = Modifier.fillMaxWidth()) {
                Text("保存全部提供商")
            }
            OutlinedButton(
                onClick = { saveProviders(); onOpenModelGroup() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("模型组与查询顺序…") }
        }
    }

    editing?.let { initial ->
        var name by remember(initial.id) { mutableStateOf(initial.name) }
        var apiKey by remember(initial.id) { mutableStateOf(initial.apiKey) }
        var baseUrl by remember(initial.id) { mutableStateOf(initial.baseUrl) }
        var modelNames by remember(initial.id) { mutableStateOf(initial.modelNamesText) }
        var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (providers.any { it.id == initial.id }) "编辑提供商" else "新建提供商") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("提供商名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL（HTTPS）") },
                        placeholder = { Text(AiAnalysisPrefs.DEFAULT_BASE_URL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelNames,
                        onValueChange = { modelNames = it },
                        label = { Text("模型名称（可留空；手动填写时每行一个）") },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用此提供商")
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val saved = initial.copy(
                        name = name.trim().ifBlank { "提供商 ${providers.size + 1}" },
                        apiKey = apiKey.trim(),
                        baseUrl = baseUrl.trim().ifBlank { AiAnalysisPrefs.DEFAULT_BASE_URL }.trimEnd('/'),
                        modelNamesText = modelNames,
                        enabled = enabled
                    )
                    providers = if (providers.any { it.id == initial.id }) {
                        providers.map { if (it.id == initial.id) saved else it }
                    } else {
                        providers + saved
                    }
                    editing = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
        )
    }

    deleting?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除提供商？") },
            text = { Text("删除后会从模型组移除该提供商下的模型。") },
            confirmButton = {
                TextButton(onClick = {
                    providers = providers.filterNot { it.id == provider.id }
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("模型配置") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("确定") } }
        )
    }
}
