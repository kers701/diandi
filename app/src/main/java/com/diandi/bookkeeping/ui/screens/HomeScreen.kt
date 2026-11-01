package com.diandi.bookkeeping.ui.screens

import android.widget.Toast
import kotlin.math.roundToInt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.ui.components.BalanceCard
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.viewmodel.MainViewModel
import com.diandi.bookkeeping.util.Categories
import com.diandi.bookkeeping.util.AppUpdateChecker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

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
    onEditClick: (Long) -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val income by viewModel.totalIncome.collectAsState()
    val expense by viewModel.totalExpense.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showCategoryEditConfirm by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableIntStateOf(0) }
    var lastTitleTapAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
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
        containerColor = balanceBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "点滴",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
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
                    IconButton(onClick = onChartsClick) {
                        Icon(Icons.Default.BarChart, contentDescription = "图表")
                    }
                    IconButton(onClick = onGoalsClick) {
                        Icon(Icons.Default.Savings, contentDescription = "攒钱目标")
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
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加账单")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            BalanceCard(
                balance = balance,
                income = income,
                expense = expense,
                accentColor = balanceAccent
            )

            val goals by viewModel.goals.collectAsState()
            val targetSum = goals.sumOf { it.targetAmount }
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
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 标签 / 类目搜索
            OutlinedTextField(
                value = tagSearch,
                onValueChange = {
                    tagSearch = it
                    viewModel.updateFilter(tagQuery = it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("按标签或类目搜索…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                trailingIcon = {
                    if (tagSearch.isNotEmpty()) {
                        IconButton(onClick = {
                            tagSearch = ""
                            viewModel.updateFilter(tagQuery = "")
                        }) {
                            Icon(Icons.Default.Clear, null)
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


    if (showCategoryEditConfirm) {
        AlertDialog(
            onDismissRequest = { showCategoryEditConfirm = false },
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
