package com.diandi.bookkeeping.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diandi.bookkeeping.data.backup.BackupManager
import com.diandi.bookkeeping.BuildConfig
import com.diandi.bookkeeping.service.AutoBillNotificationService
import com.diandi.bookkeeping.service.KeepAliveService
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.util.InstalledAppsHelper
import com.diandi.bookkeeping.util.LauncherAppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationAccessGranted by remember(context) {
        mutableStateOf(isAutoBillNotificationAccessGranted(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted = isAutoBillNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var notifyOn by remember {
        mutableStateOf(
            AppPrefs.isNotifyEnabled(context) && KeepAliveService.canShowNotification(context)
        )
    }
    val notifyPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted && AppPrefs.isNotifyEnabled(context) && KeepAliveService.canShowNotification(context)) {
            KeepAliveService.start(context)
        } else {
            notifyOn = false
            AppPrefs.setNotifyEnabled(context, false)
            KeepAliveService.stop(context)
            Toast.makeText(
                context,
                "未获得通知权限或常驻通知渠道已关闭，常驻通知未启用。",
                Toast.LENGTH_LONG
            ).show()
        }
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
    var appSearchQuery by remember { mutableStateOf("") }
    // 与 wallpaper_app 相同：全部可启动应用（含系统预装），不按 FLAG_SYSTEM 剔除
    val installedApps: List<LauncherAppInfo> = remember(context) {
        InstalledAppsHelper.listLaunchableApps(context)
    }
    val scope = rememberCoroutineScope()
    var dataTransferBusy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<BackupManager.Snapshot?>(null) }
    var dataTransferMessage by remember { mutableStateOf<String?>(null) }
    val exportBackupLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            dataTransferBusy = true
            try {
                BackupManager.export(context, uri)
                dataTransferMessage = "数据备份已导出。"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dataTransferMessage = e.message ?: "导出失败，请重试。"
            } finally {
                dataTransferBusy = false
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            dataTransferBusy = true
            try {
                pendingRestore = withContext(Dispatchers.IO) {
                    BackupManager.read(context, uri)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dataTransferMessage = e.message ?: "无法读取这个备份文件。"
            } finally {
                dataTransferBusy = false
            }
        }
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
                "本地离线记账应用：记录收支、标签与大类、时间筛选、攒钱目标、资金修正与统计图表；支持通知自动记账（白名单应用）、常驻通知快捷记账。" +
                    "数据仅保存在本机，无需账号。",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("功能说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HelpLine("添加账单", "右下角 +，可选支出或收入；支持大类、标签、备注与日期。")
            HelpLine("首页筛选", "类型：全部/支出/收入；时间：全部/本年/本月/本日；搜索支持标签与类目模糊匹配；另可详细筛选。")
            HelpLine("攒钱目标", "设置目标金额并记录进度；首页与通知可显示「余额/目标」进度。")
            HelpLine("资金修正", "调整起始余额（例如修正 +800，初始余额即为 800）。")
            HelpLine("统计图表", "支出趋势与大类分布，可按年/月/日维度查看。")
            HelpLine("应用白名单", "在软件说明中选择要监听的支付/短信等应用；选择列表中已勾选项置顶。")
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
                                if (KeepAliveService.canShowNotification(context)) {
                                    KeepAliveService.start(context)
                                } else {
                                    notifyOn = false
                                    AppPrefs.setNotifyEnabled(context, false)
                                    Toast.makeText(
                                        context,
                                        "请在系统设置中允许点滴通知，并开启“点滴常驻”通知渠道。",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            KeepAliveService.stop(context)
                        }
                    }
                )
            }

            Text("自动记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "读取已授权的消费、支付、收款和收入通知，优先识别「金额+元」。银行卡充值微信/支付宝零钱等账户互转不记账；「余额」后的数字不会当成交易额。不同应用在 3 分钟内相同金额和类型只记一次。数据仅存本机。",
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
                        notificationAccessGranted = isAutoBillNotificationAccessGranted(context)
                        if (enabled && !notificationAccessGranted) {
                            openAutoBillPermissionSettings(context)
                        }
                    }
                )
            }
            if (autoBillOn) {
                OutlinedButton(
                    onClick = { openAutoBillPermissionSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开通知读取权限设置")
            }
            Text(
                if (notificationAccessGranted) "通知读取权限已开启" else "尚未授权：请在系统设置中允许点滴访问通知",
                style = MaterialTheme.typography.bodySmall,
                color = if (notificationAccessGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Text("应用白名单", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "必须至少选择一个应用，只有白名单应用的通知才会被识别。列表中已勾选应用会置顶显示，便于管理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    draftAllowedApps = allowedApps.toSet()
                    appSearchQuery = ""
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

            }

            HorizontalDivider()
            Text("数据备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "备份包含账单、攒钱目标、资金修正和当前类目配置。恢复会替换当前这些数据；通知权限等系统授权不会写入备份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    exportBackupLauncher.launch("diandi_backup_$date.json")
                },
                enabled = !dataTransferBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出数据备份")
            }
            OutlinedButton(
                onClick = {
                    importBackupLauncher.launch(
                        arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
                    )
                },
                enabled = !dataTransferBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("从文件恢复数据")
            }
            if (dataTransferBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

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
                val filteredApps = installedApps
                    .filter { app ->
                        appSearchQuery.isBlank() ||
                            app.label.contains(appSearchQuery, ignoreCase = true) ||
                            app.packageName.contains(appSearchQuery, ignoreCase = true)
                    }
                    // 已在白名单（勾选）的应用置顶
                    .sortedWith(
                        compareByDescending<LauncherAppInfo> { it.packageName in draftAllowedApps }
                            .thenBy { it.label.lowercase() }
                    )
                Column {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索应用") },
                        placeholder = { Text("应用名称或包名") }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        filteredApps.size.toString() + " / " + installedApps.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
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
                                Text(
                                    if (app.isSystem) (app.label + "（系统）") else app.label
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
    pendingRestore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("确认恢复备份？") },
            text = {
                Text(
                    "文件已通过校验，包含 ${snapshot.transactions.size} 笔账单、${snapshot.goals.size} 个攒钱目标、" +
                        "${snapshot.corrections.size} 条资金修正，以及 ${snapshot.expenseCategories.size + snapshot.incomeCategories.size} 个类目。\n\n" +
                        "恢复会替换本机现有数据，建议先导出当前备份。此操作无法自动撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        dataTransferBusy = true
                        try {
                            BackupManager.restore(context, snapshot)
                            dataTransferMessage = "数据恢复完成。账单、余额和图表已更新。"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            dataTransferMessage = e.message ?: "恢复失败，当前数据未能完成替换。"
                        } finally {
                            dataTransferBusy = false
                        }
                    }
                }) { Text("恢复并替换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            }
        )
    }
    dataTransferMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dataTransferMessage = null },
            title = { Text("数据备份") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { dataTransferMessage = null }) { Text("确定") }
            }
        )
    }
}


private fun isAutoBillNotificationAccessGranted(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val cn = ComponentName(context, AutoBillNotificationService::class.java)
    val targets = setOf(
        cn.flattenToString(),
        cn.packageName + "/" + cn.className,
        cn.packageName + "/" + cn.shortClassName
    )
    return flat.split(':').any { entry ->
        val e = entry.trim()
        targets.any { t -> e.equals(t, ignoreCase = true) } ||
            e.endsWith("/" + cn.className, ignoreCase = true) ||
            e.endsWith("/" + cn.shortClassName, ignoreCase = true)
    }
}

private fun openAutoBillPermissionSettings(context: android.content.Context) {
    val component = ComponentName(context, AutoBillNotificationService::class.java)
    val intents = buildList {
        // HyperOS 等厂商系统对单应用授权详情深链的处理不一致，先打开通用列表页。
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
                }
            )
        }
        add(Intent(Settings.ACTION_SETTINGS))
    }
    intents.forEachIndexed { index, intent ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (index == intents.lastIndex) {
                Toast.makeText(
                    context,
                    "请在设置中搜索“通知访问”或“通知使用权”，找到“点滴自动记账”并允许访问。",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(
        context,
        "无法自动打开授权页。请到系统设置中搜索“通知访问”或“通知使用权”，然后允许“点滴自动记账”。",
        Toast.LENGTH_LONG
    ).show()
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
