package com.diandi.bookkeeping.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.diandi.bookkeeping.BuildConfig
import com.diandi.bookkeeping.service.KeepAliveService
import com.diandi.bookkeeping.util.AppPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var notifyOn by remember {
        mutableStateOf(AppPrefs.isNotifyEnabled(context))
    }
    var autoBillOn by remember {
        mutableStateOf(AppPrefs.isAutoBillEnabled(context))
    }
    var allowedApps by remember {
        mutableStateOf(AppPrefs.getAutoBillAllowedApps(context))
    }
    var allowedKeywords by remember {
        mutableStateOf(AppPrefs.getAutoBillAllowedKeywords(context))
    }
    var blockedKeywords by remember {
        mutableStateOf(AppPrefs.getAutoBillBlockedKeywords(context))
    }
    var allowKeywordInput by remember { mutableStateOf("") }
    var blockKeywordInput by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var draftAllowedApps by remember { mutableStateOf(emptySet<String>()) }
    val installedApps = remember(context) {
        context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("软件说明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("点滴", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "本地离线记账应用：记录收支、标签与大类、时间筛选、攒钱目标、资金修正与统计图表。" +
                    "数据仅保存在本机，无需账号。",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("功能说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HelpLine("添加账单", "右下角 +，可选支出或收入；支持大类、标签、备注与日期。")
            HelpLine("首页筛选", "类型：全部/支出/收入；时间：全部/本年/本月/本日；另支持标签搜索与详细筛选。")
            HelpLine("攒钱目标", "设置目标金额并记录进度；首页与通知可显示「余额/目标」进度。")
            HelpLine("资金修正", "调整起始余额（例如修正 +800，初始余额即为 800）。")
            HelpLine("统计图表", "支出趋势与大类分布，可按年/月/日维度查看。")
            HelpLine("检查更新", "首页顶栏更新图标，从 GitHub Releases 检测并下载安装。")
            HelpLine("通知快捷", "点击常驻通知打开独立记账页，保存后关闭，不进入主界面；独立进程 + 划掉后自启。")

            HorizontalDivider()

            Text("常驻通知", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "开启后每 2 分钟刷新攒钱进度，点击通知可快速记账。关闭后立即停止通知服务，不再发起通知。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("启用常驻通知", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = notifyOn,
                    onCheckedChange = { enabled ->
                        notifyOn = enabled
                        AppPrefs.setNotifyEnabled(context, enabled)
                        if (enabled) {
                            val needPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            if (!needPerm) {
                                KeepAliveService.start(context)
                            }
                            // 无通知权限时由主界面下次启动再申请
                        } else {
                            KeepAliveService.stop(context)
                        }
                    }
                )
            }

            Text("自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "读取已授权的消费、支付、收款和收入通知，识别金额后自动生成账单。不同应用在 3 分钟内出现相同金额和收支类型时只记录一次，可避免支付通知与银行卡短信重复记账。数据仍只保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("启用自动记账", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = autoBillOn,
                    onCheckedChange = { enabled ->
                        autoBillOn = enabled
                        AppPrefs.setAutoBillEnabled(context, enabled)
                        if (enabled) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } catch (_: Exception) {
                            }
                        }
                    }
                )
            }
            if (autoBillOn) {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开通知读取权限设置")
                }
            }

            Text("应用白名单", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "必须至少选择一个应用，只有白名单应用的通知才会被识别。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    draftAllowedApps = allowedApps.toSet()
                    showAppPicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (allowedApps.isEmpty()) "选择白名单应用" else "已选择 ${allowedApps.size} 个应用")
            }
            if (allowedApps.isEmpty()) {
                Text(
                    "当前未选择应用，自动记账不会记录任何通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            KeywordEditor(
                title = "自定义白名单关键词",
                description = "除预设关键词外，包含这些词的通知也会进入金额识别。",
                keywords = allowedKeywords,
                input = allowKeywordInput,
                onInputChange = { allowKeywordInput = it },
                onAdd = {
                    val keyword = allowKeywordInput.trim()
                    if (keyword.isNotBlank() && keyword !in allowedKeywords) {
                        val next = (allowedKeywords + keyword).distinct()
                        AppPrefs.setAutoBillAllowedKeywords(context, next)
                        allowedKeywords = next
                        allowKeywordInput = ""
                    }
                },
                onRemove = { keyword ->
                    val next = allowedKeywords - keyword
                    AppPrefs.setAutoBillAllowedKeywords(context, next)
                    allowedKeywords = next
                }
            )
            KeywordEditor(
                title = "自定义黑名单关键词",
                description = "命中这些词的通知会直接忽略；与白名单冲突时黑名单优先。",
                keywords = blockedKeywords,
                input = blockKeywordInput,
                onInputChange = { blockKeywordInput = it },
                onAdd = {
                    val keyword = blockKeywordInput.trim()
                    if (keyword.isNotBlank() && keyword !in blockedKeywords) {
                        val next = (blockedKeywords + keyword).distinct()
                        AppPrefs.setAutoBillBlockedKeywords(context, next)
                        blockedKeywords = next
                        blockKeywordInput = ""
                    }
                },
                onRemove = { keyword ->
                    val next = blockedKeywords - keyword
                    AppPrefs.setAutoBillBlockedKeywords(context, next)
                    blockedKeywords = next
                }
            )

            HorizontalDivider()
            Text(
                "开源仓库：github.com/kers701/diandi\n数据仅存本机，卸载即清除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("选择白名单应用") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(installedApps, key = { it.packageName }) { app ->
                        val selected = app.packageName in draftAllowedApps
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draftAllowedApps = if (selected) {
                                        draftAllowedApps - app.packageName
                                    } else {
                                        draftAllowedApps + app.packageName
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    draftAllowedApps = if (checked) {
                                        draftAllowedApps + app.packageName
                                    } else {
                                        draftAllowedApps - app.packageName
                                    }
                                }
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(app.loadLabel(context.packageManager).toString())
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppPrefs.setAutoBillAllowedApps(context, draftAllowedApps)
                    allowedApps = draftAllowedApps
                    showAppPicker = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HelpLine(title: String, body: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun KeywordEditor(
    title: String,
    description: String,
    keywords: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("输入关键词") }
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAdd, enabled = input.isNotBlank()) { Text("添加") }
    }
    if (keywords.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(keywords, key = { it }) { keyword ->
                AssistChip(
                    onClick = { onRemove(keyword) },
                    label = { Text("$keyword ×") }
                )
            }
        }
    }
}
