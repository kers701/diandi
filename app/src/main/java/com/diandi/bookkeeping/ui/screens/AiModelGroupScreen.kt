package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.ai.AiModelEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelGroupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val providerMap = remember { AiAnalysisPrefs.providers(context).associateBy { it.id } }
    var models by remember {
        mutableStateOf(AiAnalysisPrefs.models(context).sortedBy { it.priority })
    }

    fun persist(ordered: List<AiModelEntry>) {
        val normalized = ordered.mapIndexed { index, model -> model.copy(priority = index) }
        models = normalized
        AiAnalysisPrefs.saveModels(context, normalized)
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in models.indices) return
        val reordered = models.toMutableList()
        val item = reordered.removeAt(index)
        reordered.add(target, item)
        persist(reordered)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型组 · 查询顺序") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "仅会尝试已启用且所属提供商也已启用的模型。按下方顺序逐个查询；遇到请求失败或空回复会切换下一项，首个有效回复即停止。开关和顺序会即时保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "已启用 ${models.count { it.enabled }} / ${models.size} 个模型",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (models.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(
                        "模型组为空。返回提供商配置，填写 API Key 后拉取 /models；也可以手动输入模型名。",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            models.forEachIndexed { index, model ->
                val provider = providerMap[model.providerId]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (model.enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            model.modelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "提供商：${provider?.name ?: "已删除"}" +
                                if (provider != null && !provider.enabled) "（提供商已禁用）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("查询顺序 ${index + 1}", style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("启用")
                                Switch(
                                    checked = model.enabled,
                                    onCheckedChange = { enabled ->
                                        persist(models.mapIndexed { itemIndex, item ->
                                            if (itemIndex == index) item.copy(enabled = enabled) else item
                                        })
                                    }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = index > 0, onClick = { move(index, -1) }) { Text("上移") }
                            TextButton(enabled = index < models.lastIndex, onClick = { move(index, 1) }) { Text("下移") }
                        }
                    }
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text("返回提供商配置") }
        }
    }
}
