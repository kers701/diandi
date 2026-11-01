package com.diandi.bookkeeping.ui.screens

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.diandi.bookkeeping.data.ai.AiAnalysisPrefs
import com.diandi.bookkeeping.data.dao.CategoryStat
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.ui.components.BalanceCard
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.viewmodel.MainViewModel
import com.diandi.bookkeeping.util.Categories
import com.diandi.bookkeeping.util.AppUpdateChecker
import com.diandi.bookkeeping.util.allocateBalanceByWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddClick: () -> Unit,
    onChartsClick: () -> Unit,
    onGoalsClick: () -> Unit,
    onCorrectionClick: () -> Unit,
    onAboutClick: () -> Unit = {},
    onCategoryEditClick: () -> Unit = {},
    onEditClick: (Long) -> Unit = {},
    onAiAnalysisClick: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val income by viewModel.totalIncome.collectAsState()
    val expense by viewModel.totalExpense.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showBillsPage by rememberSaveable { mutableStateOf(false) }
    var showCategoryEditConfirm by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableIntStateOf(0) }
    var lastTitleTapAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var smartAnalysisEnabled by remember { mutableStateOf(AiAnalysisPrefs.isEnabled(context)) }
    var smartAnalysisResult by remember { mutableStateOf(AiAnalysisPrefs.result(context)) }
    var smartAnalysisError by remember { mutableStateOf(AiAnalysisPrefs.lastError(context)) }
    var smartAnalysisUpdatedAt by remember { mutableLongStateOf(AiAnalysisPrefs.lastSuccessAt(context)) }
    var smartAnalysisRunning by remember { mutableStateOf(AiAnalysisPrefs.isAnalyzing(context)) }
    val currentMonth = YearMonth.now()
    val homeCategoryStatsFlow = remember(viewModel, currentMonth) {
        viewModel.getCategoryDistribution("EXPENSE", currentMonth.year, currentMonth.monthValue, null)
    }
    val homeCategoryStats by homeCategoryStatsFlow.collectAsState(initial = emptyList())
    val focusManager = LocalFocusManager.current
    var searchHasFocus by remember { mutableStateOf(false) }
    var scaffoldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var searchFieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var tagSearch by remember { mutableStateOf(filter.tagQuery) }
    var checkUpdate by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<AppUpdateChecker.ReleaseInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var updateCheckRequest by remember { mutableIntStateOf(0) }
    var automaticUpdateCheck by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val balanceAccent = balanceAccentColor(balance)
    val balanceBackground = balanceAccent.copy(alpha = 0.07f)

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smartAnalysisEnabled = AiAnalysisPrefs.isEnabled(context)
                smartAnalysisResult = AiAnalysisPrefs.result(context)
                smartAnalysisError = AiAnalysisPrefs.lastError(context)
                smartAnalysisUpdatedAt = AiAnalysisPrefs.lastSuccessAt(context)
                smartAnalysisRunning = AiAnalysisPrefs.isAnalyzing(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: android.content.Context?, intent: Intent?) {
                val update = intent ?: return
                smartAnalysisEnabled = update.getBooleanExtra(
                    AiAnalysisPrefs.EXTRA_ENABLED, AiAnalysisPrefs.isEnabled(context)
                )
                smartAnalysisResult = update.getStringExtra(AiAnalysisPrefs.EXTRA_RESULT)
                    ?: AiAnalysisPrefs.result(context)
                smartAnalysisError = update.getStringExtra(AiAnalysisPrefs.EXTRA_LAST_ERROR)
                    ?: AiAnalysisPrefs.lastError(context)
                smartAnalysisUpdatedAt = update.getLongExtra(
                    AiAnalysisPrefs.EXTRA_LAST_SUCCESS_AT, AiAnalysisPrefs.lastSuccessAt(context)
                )
                smartAnalysisRunning = update.getBooleanExtra(
                    AiAnalysisPrefs.EXTRA_ANALYZING, AiAnalysisPrefs.isAnalyzing(context)
                )
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AiAnalysisPrefs.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    BackHandler(enabled = searchHasFocus && !showCategoryEditConfirm && !checkUpdate) {
        focusManager.clearFocus(force = true)
    }

    LaunchedEffect(Unit) {
        updateCheckRequest++
    }
    LaunchedEffect(updateCheckRequest) {
        if (updateCheckRequest == 0) return@LaunchedEffect
        val isAutomaticCheck = automaticUpdateCheck
        if (!isAutomaticCheck) {
            checkUpdate = true
            updateMsg = "正在检查…"
            updateInfo = null
        }
        val result = withContext(Dispatchers.IO) { AppUpdateChecker.checkLatest() }
        when (result) {
            is AppUpdateChecker.CheckResult.UpdateAvailable -> {
                updateInfo = result.info
                updateMsg = "发现新版本 ${result.info.versionName}"
                checkUpdate = true
            }
            is AppUpdateChecker.CheckResult.UpToDate -> {
                if (!isAutomaticCheck) {
                    updateMsg = "已是最新版 ${result.current}"
                    checkUpdate = true
                }
            }
            is AppUpdateChecker.CheckResult.Failed -> {
                if (!isAutomaticCheck) {
                    updateMsg = "检查失败：${result.message}"
                    checkUpdate = true
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .onGloballyPositioned { scaffoldCoordinates = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                            ?: continue
                        if (!searchHasFocus) continue

                        val scaffold = scaffoldCoordinates ?: continue
                        val searchField = searchFieldCoordinates ?: continue
                        if (!scaffold.isAttached || !searchField.isAttached) continue

                        val topLeft = scaffold.localPositionOf(searchField, Offset.Zero)
                        val bottomRight = Offset(
                            topLeft.x + searchField.size.width.toFloat(),
                            topLeft.y + searchField.size.height.toFloat()
                        )
                        if (!Rect(topLeft, bottomRight).contains(down.position)) {
                            focusManager.clearFocus(force = true)
                        }
                    }
                }
            },
        containerColor = balanceBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showBillsPage) "账单" else "点滴",
                        fontWeight = FontWeight.Bold,
                        modifier = if (showBillsPage) Modifier else Modifier.clickable {
                            val now = System.currentTimeMillis()
                            titleTapCount = if (now - lastTitleTapAt > 2000L) 1 else titleTapCount + 1
                            lastTitleTapAt = now
                            if (titleTapCount >= 7) {
                                titleTapCount = 0
                                showCategoryEditConfirm = true
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = balanceBackground),
                actions = {
                    if (showBillsPage) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选账单")
                        }
                    } else {
                        IconButton(onClick = onChartsClick) {
                            Icon(Icons.Default.BarChart, contentDescription = "图表")
                        }
                        IconButton(onClick = onGoalsClick) {
                            Icon(Icons.Default.Savings, contentDescription = "攒钱目标")
                        }
                        IconButton(onClick = onAiAnalysisClick) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI 消费分析")
                        }
                        IconButton(onClick = onCorrectionClick) {
                            Icon(Icons.Default.AccountBalance, contentDescription = "资金修正")
                        }
                        IconButton(onClick = {
                            automaticUpdateCheck = false
                            updateCheckRequest++
                        }) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新")
                        }
                        IconButton(onClick = onAboutClick) {
                            Icon(Icons.Default.Info, contentDescription = "软件说明")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = balanceBackground) {
                NavigationBarItem(
                    selected = !showBillsPage,
                    onClick = { showBillsPage = false },
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = showBillsPage,
                    onClick = { showBillsPage = true },
                    icon = { Icon(Icons.Default.Receipt, contentDescription = "账单") },
                    label = { Text("账单") }
                )
            }
        },
        floatingActionButton = {
            if (showBillsPage) {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加账单")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (!showBillsPage) {
                Spacer(Modifier.height(8.dp))
                BalanceCard(
                    balance = balance,
                    income = income,
                    expense = expense,
                    accentColor = balanceAccent
                )

            val goals by viewModel.goals.collectAsState()
            val targetSum = goals.sumOf { it.targetAmount }
            val allocationByGoal = remember(goals, balance) {
                allocateBalanceByWeight(balance, goals)
            }
            if (targetSum > 0) {
                val pct = ((balance / targetSum) * 100).coerceIn(0.0, 999.0)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("攒钱进度", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${"%.0f".format(pct)}%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "余额 ${formatMoney(balance)} / 目标 ${formatMoney(targetSum)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (goals.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "目标余额分配（当前正余额按权重分配，另含手动记录）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            goals.forEach { goal ->
                                val allocated = allocationByGoal[goal.id] ?: 0.0
                                val displayed = goal.currentAmount + allocated
                                val goalProgress = if (goal.targetAmount > 0.0) {
                                    (displayed / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
                                } else 0f
                                val weightLabel = if (goal.allocationWeight % 1.0 == 0.0) {
                                    goal.allocationWeight.toInt().toString()
                                } else goal.allocationWeight.toString()
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${goal.title} · 权重 $weightLabel",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            "${formatMoney(displayed)} / ${formatMoney(goal.targetAmount)}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { goalProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (smartAnalysisEnabled) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f).then(
                        if (smartAnalysisResult.isBlank()) Modifier.clickable(onClick = onAiAnalysisClick)
                        else Modifier
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("AI 消费分析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (smartAnalysisRunning) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (smartAnalysisResult.isNotBlank()) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    smartAnalysisResult,
                                    modifier = Modifier.pointerInput(smartAnalysisResult) {
                                        detectTapGestures(onDoubleTap = {
                                            val clipboard = context.getSystemService(
                                                android.content.Context.CLIPBOARD_SERVICE
                                            ) as? ClipboardManager
                                            if (clipboard != null) {
                                                clipboard.setPrimaryClip(
                                                    ClipData.newPlainText("AI 消费分析结果", smartAnalysisResult)
                                                )
                                                Toast.makeText(context, "AI 分析结果已复制", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                if (smartAnalysisRunning) "双击分析文字复制 · 正在后台更新"
                                else "双击分析文字复制",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (smartAnalysisUpdatedAt > 0L) {
                                Text(
                                    "更新于 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(smartAnalysisUpdatedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    when {
                                        smartAnalysisRunning -> "正在独立后台进程中分析…"
                                        smartAnalysisError.isNotBlank() -> smartAnalysisError
                                        else -> "尚无分析结果；首次开启后会立即分析。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (smartAnalysisError.isNotBlank()) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                HomeCategoryChart(
                    stats = homeCategoryStats,
                    month = currentMonth.monthValue,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            } else {
                Spacer(Modifier.height(8.dp))

            // 支持搜索标签、类目、金额和备注
            OutlinedTextField(
                value = tagSearch,
                onValueChange = {
                    tagSearch = it
                    viewModel.updateFilter(tagQuery = it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { searchHasFocus = it.isFocused }
                    .onGloballyPositioned { searchFieldCoordinates = it },
                placeholder = { Text("搜索类目、标签、金额或备注…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus(force = true) }
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tagSearch.isNotEmpty()) {
                            IconButton(onClick = {
                                tagSearch = ""
                                viewModel.updateFilter(tagQuery = "")
                                focusManager.clearFocus(force = true)
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // 快捷类型筛选
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("全部", filter.type == null, MaterialTheme.colorScheme.primary) {
                    viewModel.updateFilter(type = null)
                }
                TypeChip("支出", filter.type == "EXPENSE", Color(0xFFD32F2F)) {
                    viewModel.updateFilter(type = "EXPENSE")
                }
                TypeChip("收入", filter.type == "INCOME", Color(0xFF388E3C)) {
                    viewModel.updateFilter(type = "INCOME")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 二级时间筛选：年 / 月 / 日
            val today = LocalDate.now()
            val hasDateRange = filter.startDate != null || filter.endDate != null
            val isAllTime = !hasDateRange && filter.year == null && filter.month == null && filter.day == null
            val isThisYear = !hasDateRange && filter.year == today.year && filter.month == null && filter.day == null
            val isThisMonth = !hasDateRange && filter.year == today.year && filter.month == today.monthValue && filter.day == null
            val isToday = !hasDateRange && filter.year == today.year && filter.month == today.monthValue && filter.day == today.dayOfMonth
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("全部", isAllTime, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(
                        year = null,
                        month = null,
                        day = null,
                        startDate = null,
                        endDate = null
                    )
                }
                TypeChip("本年", isThisYear, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(
                        year = today.year,
                        month = null,
                        day = null,
                        startDate = null,
                        endDate = null
                    )
                }
                TypeChip("本月", isThisMonth, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(
                        year = today.year,
                        month = today.monthValue,
                        day = null,
                        startDate = null,
                        endDate = null
                    )
                }
                TypeChip("本日", isToday, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(
                        year = today.year,
                        month = today.monthValue,
                        day = today.dayOfMonth,
                        startDate = null,
                        endDate = null
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (hasDateRange || filter.year != null || filter.month != null || filter.day != null || filter.categories.isNotEmpty()) {
                Text(
                    "当前筛选: ${filter.startDate?.toString() ?: filter.year?.let { "${it}年" } ?: "全部日期"}" +
                        "${filter.endDate?.let { " 至 $it" } ?: ""}" +
                        "${filter.categories.takeIf { it.isNotEmpty() }?.joinToString("、")?.let { " · $it" } ?: ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { viewModel.clearFilter(); tagSearch = "" }) {
                    Text("清除筛选")
                }
            }

            Spacer(Modifier.height(4.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无账单，点击右下角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        TransactionItem(
                            tx = tx,
                            onEdit = { onEditClick(tx.id) },
                            onDelete = { transactionToDelete = tx }
                        )
                    }
                }
            }
            }
        }
    }


    if (showCategoryEditConfirm) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("进入类目编辑？") },
            text = { Text("是否进入类目编辑页面？") },
            confirmButton = {
                TextButton(onClick = {
                    showCategoryEditConfirm = false
                    onCategoryEditClick()
                }) {
                    Text("进入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryEditConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (checkUpdate) {
        AlertDialog(
            onDismissRequest = { checkUpdate = false },
            title = { Text("检查更新") },
            text = {
                Column {
                    Text(updateMsg ?: "")
                    updateInfo?.body?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.take(400), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                val info = updateInfo
                if (info != null && info.apkUrl != null) {
                    TextButton(
                        enabled = !downloading,
                        onClick = {
                            scope.launch {
                                downloading = true
                                updateMsg = "下载中…"
                                val r = withContext(Dispatchers.IO) {
                                    AppUpdateChecker.downloadApk(context, info)
                                }
                                downloading = false
                                when (r) {
                                    is AppUpdateChecker.DownloadResult.Ok -> {
                                        if (!AppUpdateChecker.canInstallPackages(context)) {
                                            AppUpdateChecker.openInstallPermissionSettings(context)
                                            updateMsg = "请允许安装未知应用后重试"
                                        } else {
                                            AppUpdateChecker.installApk(context, r.file)
                                            checkUpdate = false
                                        }
                                    }
                                    is AppUpdateChecker.DownloadResult.Failed ->
                                        updateMsg = r.message
                                }
                            }
                        }
                    ) { Text(if (downloading) "下载中…" else "下载安装") }
                } else {
                    TextButton(onClick = { checkUpdate = false }) { Text("确定") }
                }
            },
            dismissButton = {
                val info = updateInfo
                Row {
                    if (info != null) {
                        TextButton(onClick = {
                            AppUpdateChecker.openReleasePage(context, info.htmlUrl)
                        }) { Text("打开页面") }
                    }
                    TextButton(onClick = { checkUpdate = false }) { Text("关闭") }
                }
            }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            current = filter,
            onDismiss = { showFilterSheet = false },
            onApply = { y, m, d, selectedType ->
                viewModel.updateFilter(
                    year = null,
                    month = null,
                    day = null,
                    startDate = y,
                    endDate = m,
                    categories = d,
                    type = selectedType
                )
                showFilterSheet = false
            }
        )
    }

    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("删除账单？") },
            text = {
                Text("确定要删除 ${tx.category} ${formatMoney(tx.amount)} 这笔账单吗？删除后无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("取消") }
            }
        )
    }
}

private val HOME_CATEGORY_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFFFFA726), Color(0xFFAB47BC), Color(0xFF42A5F5)
)

@Composable
private fun HomeCategoryChart(
    stats: List<CategoryStat>,
    month: Int,
    modifier: Modifier = Modifier
) {
    val entries = remember(stats) {
        val top = stats.take(5)
        val remainder = stats.drop(5).sumOf { it.total }
        if (remainder > 0.0) top + CategoryStat("其他", remainder) else top
    }
    val total = entries.sumOf { it.total }.coerceAtLeast(0.0001)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("大类分析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${month}月支出分布 · 合计 ${formatMoney(entries.sumOf { it.total })}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("本月暂无支出分类数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(
                        modifier = Modifier.weight(1f).fillMaxHeight().aspectRatio(1f).padding(4.dp)
                    ) {
                        val diameter = min(size.width, size.height) * 0.94f
                        val left = (size.width - diameter) / 2f
                        val top = (size.height - diameter) / 2f
                        var startAngle = -90f
                        entries.forEachIndexed { index, entry ->
                            val sweep = (entry.total / total * 360.0).toFloat()
                            drawArc(
                                color = HOME_CATEGORY_COLORS[index % HOME_CATEGORY_COLORS.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = true,
                                topLeft = Offset(left, top),
                                size = Size(diameter, diameter)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1.35f).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        entries.forEachIndexed { index, entry ->
                            val color = HOME_CATEGORY_COLORS[index % HOME_CATEGORY_COLORS.size]
                            val fraction = (entry.total / total).toFloat().coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    entry.category,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    "${"%.0f".format(fraction * 100)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = color
                            )
                            Text(
                                formatMoney(entry.total),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun balanceAccentColor(balance: Double): Color {
    val neutral = Color(0xFF607D8B)
    val target = when {
        balance > 0.0 -> Color(0xFF2E7D32)
        balance < 0.0 -> Color(0xFFC62828)
        else -> Color(0xFF8D6E63)
    }
    val strength = (abs(balance) / (abs(balance) + 1000.0)).toFloat().coerceIn(0.0f, 1.0f)
    return Color(
        red = neutral.red + (target.red - neutral.red) * strength,
        green = neutral.green + (target.green - neutral.green) * strength,
        blue = neutral.blue + (target.blue - neutral.blue) * strength,
        alpha = 1.0f
    )
}

@Composable
fun TransactionItem(
    tx: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = tx.type == "EXPENSE"
    val amountColor = if (isExpense) Color(0xFFD32F2F) else Color(0xFF388E3C)
    val sign = if (isExpense) "-" else "+"
    var revealed by remember { mutableStateOf(false) }
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // 至少与常见账单行接近，避免尚未测量时高度跳动
    val minHeight = 64.dp
    val lockedHeight = if (cardHeightPx > 0) {
        with(density) { cardHeightPx.toDp() }.coerceAtLeast(minHeight)
    } else {
        minHeight
    }

    Crossfade(
        targetState = revealed,
        animationSpec = tween(durationMillis = 220),
        label = "bill-actions"
    ) { showActions ->
        if (showActions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lockedHeight),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { revealed = false }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "退出",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            revealed = false
                            onEdit()
                        }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            revealed = false
                            onDelete()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { cardHeightPx = it.height }
                    .pointerInput(tx.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = { },
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount < -12f) {
                                    revealed = true
                                }
                            }
                        )
                    },
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tx.category,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = amountColor
                        )
                        if (tx.tags.isNotBlank()) {
                            Text(
                                "标签: ${tx.tags}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (tx.note.isNotBlank()) {
                            Text(
                                tx.note,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            "${tx.year}-${tx.month.toString().padStart(2, '0')}-${tx.day.toString().padStart(2, '0')}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "$sign${"%.2f".format(tx.amount)}",
                        color = amountColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    current: com.diandi.bookkeeping.viewmodel.FilterState,
    onDismiss: () -> Unit,
    onApply: (startDate: LocalDate?, endDate: LocalDate?, categories: Set<String>, type: String?) -> Unit
) {
    var startDate by remember { mutableStateOf(current.startDate) }
    var endDate by remember { mutableStateOf(current.endDate) }
    var categories by remember { mutableStateOf(current.categories) }
    var selectedType by remember { mutableStateOf(current.type) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectingStart by remember { mutableStateOf(true) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    // 大类列表跟随支出/收入类型；未选类型时显示全部
    val filterContext = LocalContext.current
    val selectableCategories = remember(selectedType, filterContext) {
        when (selectedType) {
            "EXPENSE" -> Categories.expense(filterContext)
            "INCOME" -> Categories.income(filterContext)
            else -> (Categories.expense(filterContext) + Categories.income(filterContext)).distinct()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("按时间 / 类别筛选", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text("收支类型", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("全部", selectedType == null, MaterialTheme.colorScheme.primary) {
                    selectedType = null
                }
                TypeChip("支出", selectedType == "EXPENSE", Color(0xFFD32F2F)) {
                    selectedType = "EXPENSE"
                    categories = categories.intersect(Categories.expense(filterContext).toSet())
                }
                TypeChip("收入", selectedType == "INCOME", Color(0xFF388E3C)) {
                    selectedType = "INCOME"
                    categories = categories.intersect(Categories.income(filterContext).toSet())
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        selectingStart = true
                        showDatePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(startDate?.toString() ?: "开始日期")
                }
                OutlinedButton(
                    onClick = {
                        selectingStart = false
                        showDatePicker = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(endDate?.toString() ?: "结束日期")
                }
            }
            TextButton(
                onClick = {
                    startDate = null
                    endDate = null
                }
            ) { Text("清除日期") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (categories.isEmpty()) "选择大类（可多选）" else "已选大类：${categories.joinToString("、")}")
            }
            TextButton(onClick = { categories = emptySet() }) { Text("清除大类") }
            val invalidRange = startDate != null && endDate != null && endDate!!.isBefore(startDate)
            if (invalidRange) {
                Text("结束日期不能早于开始日期", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onApply(startDate, endDate, categories, selectedType)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !invalidRange
            ) {
                Text("应用筛选")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val initialDate = if (selectingStart) startDate ?: LocalDate.now() else endDate ?: startDate ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        if (selectingStart) startDate = date else endDate = date
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("选择大类") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    selectableCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    categories = if (category in categories) {
                                        categories - category
                                    } else {
                                        categories + category
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = category in categories,
                                onCheckedChange = { checked ->
                                    categories = if (checked) categories + category else categories - category
                                }
                            )
                            Text(category)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text("完成") }
            }
        )
    }
}
