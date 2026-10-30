package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.ui.components.BalanceCard
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.viewmodel.MainViewModel
import com.diandi.bookkeeping.util.AppUpdateChecker
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddClick: () -> Unit,
    onChartsClick: () -> Unit,
    onGoalsClick: () -> Unit,
    onCorrectionClick: () -> Unit,
    onAboutClick: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val income by viewModel.totalIncome.collectAsState()
    val expense by viewModel.totalExpense.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var tagSearch by remember { mutableStateOf(filter.tagQuery) }
    var checkUpdate by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<AppUpdateChecker.ReleaseInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点滴", fontWeight = FontWeight.Bold) },
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
                    IconButton(onClick = { checkUpdate = true }) {
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
            BalanceCard(balance = balance, income = income, expense = expense)

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

            // 标签搜索
            OutlinedTextField(
                value = tagSearch,
                onValueChange = {
                    tagSearch = it
                    viewModel.updateFilter(tagQuery = it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("按标签模糊搜索…") },
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
            val isAllTime = filter.year == null && filter.month == null && filter.day == null
            val isThisYear = filter.year == today.year && filter.month == null && filter.day == null
            val isThisMonth = filter.year == today.year && filter.month == today.monthValue && filter.day == null
            val isToday = filter.year == today.year && filter.month == today.monthValue && filter.day == today.dayOfMonth
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("全部", isAllTime, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(year = null, month = null, day = null)
                }
                TypeChip("本年", isThisYear, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(year = today.year, month = null, day = null)
                }
                TypeChip("本月", isThisMonth, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(year = today.year, month = today.monthValue, day = null)
                }
                TypeChip("本日", isToday, MaterialTheme.colorScheme.tertiary) {
                    viewModel.updateFilter(
                        year = today.year,
                        month = today.monthValue,
                        day = today.dayOfMonth
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filter.year != null || filter.month != null || filter.day != null || filter.category != null) {
                Text(
                    "当前筛选: ${filter.year ?: "年"}年${filter.month?.let { "${it}月" } ?: ""}${filter.day?.let { "${it}日" } ?: ""} ${filter.category ?: ""}",
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
                            onDelete = { viewModel.deleteTransaction(tx) }
                        )
                    }
                }
            }
        }
    }


    if (checkUpdate) {
        LaunchedEffect(Unit) {
            updateMsg = "正在检查…"
            updateInfo = null
            val result = withContext(Dispatchers.IO) { AppUpdateChecker.checkLatest() }
            when (result) {
                is AppUpdateChecker.CheckResult.UpToDate ->
                    updateMsg = "已是最新版 ${result.current}"
                is AppUpdateChecker.CheckResult.UpdateAvailable -> {
                    updateInfo = result.info
                    updateMsg = "发现新版本 ${result.info.versionName}"
                }
                is AppUpdateChecker.CheckResult.Failed ->
                    updateMsg = "检查失败：${result.message}"
            }
        }
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
            onApply = { y, m, d, cat ->
                viewModel.updateFilter(year = y, month = m, day = d, category = cat)
                showFilterSheet = false
            }
        )
    }
}

@Composable
fun TransactionItem(tx: Transaction, onDelete: () -> Unit) {
    val isExpense = tx.type == "EXPENSE"
    val amountColor = if (isExpense) Color(0xFFD32F2F) else Color(0xFF388E3C)
    val sign = if (isExpense) "-" else "+"

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    fontSize = 16.sp
                )
                if (tx.tags.isNotBlank()) {
                    Text(
                        "标签: ${tx.tags}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (tx.note.isNotBlank()) {
                    Text(tx.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${tx.year}-${tx.month.toString().padStart(2, '0')}-${tx.day.toString().padStart(2, '0')}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$sign${formatMoney(tx.amount)}",
                color = amountColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    current: com.diandi.bookkeeping.viewmodel.FilterState,
    onDismiss: () -> Unit,
    onApply: (year: Int?, month: Int?, day: Int?, category: String?) -> Unit
) {
    var year by remember { mutableStateOf(current.year?.toString() ?: "") }
    var month by remember { mutableStateOf(current.month?.toString() ?: "") }
    var day by remember { mutableStateOf(current.day?.toString() ?: "") }
    var category by remember { mutableStateOf(current.category ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("按时间 / 类别筛选", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("年") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("月") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("日") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("大类（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("如：餐饮、烟酒…") }
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onApply(
                        year.toIntOrNull(),
                        month.toIntOrNull(),
                        day.toIntOrNull(),
                        category.ifBlank { null }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("应用筛选")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
