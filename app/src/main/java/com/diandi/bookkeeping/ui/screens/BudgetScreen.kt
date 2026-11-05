package com.diandi.bookkeeping.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.entity.Budget
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.util.BillingCycle
import com.diandi.bookkeeping.util.BillingCycleCalculator
import com.diandi.bookkeeping.util.BudgetMath
import com.diandi.bookkeeping.util.Money
import com.diandi.bookkeeping.util.TransactionCategories
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 预算页（2.3 起的新模型）。
 *
 * - 固定开销：房租、水电这类每月必花的项目，可用「智能生成」按上个周期实际支出建项。
 * - 浮动预算：日常浮动开销，可设一个覆盖全部支出的大数（如 2000）。
 * - 可自由分配余额 = 当前余额 − 固定未花预留 − 浮动未花预留。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var cycleStartDay by remember {
        mutableIntStateOf(AppPrefs.getBudgetCycleStartDay(context))
    }
    val cycle = remember(today, cycleStartDay) { BillingCycleCalculator.of(today, cycleStartDay) }
    val previousCycle = remember(today, cycleStartDay) {
        BillingCycleCalculator.previous(today, cycleStartDay)
    }

    val budgets by viewModel.budgets.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val cycleExpense by remember(cycle) {
        viewModel.expenseInRange(cycle.startDate, cycle.endDate)
    }.collectAsState(initial = 0L)
    val cycleByCategory by remember(cycle) {
        viewModel.expenseByCategoryInRange(cycle.startDate, cycle.endDate)
    }.collectAsState(initial = emptyMap())
    val previousByCategory by remember(previousCycle) {
        viewModel.expenseByCategoryInRange(previousCycle.startDate, previousCycle.endDate)
    }.collectAsState(initial = emptyMap())

    val overview = remember(budgets, balance, cycleByCategory, cycleExpense) {
        BudgetMath.overview(balance, budgets, cycleByCategory, cycleExpense)
    }
    val fixedCategories = remember(budgets) { BudgetMath.fixedCategories(budgets) }
    val fixedBudgets = budgets.filter { it.kind == Budget.KIND_FIXED }
    val floatingBudgets = budgets.filter { it.kind == Budget.KIND_FLOATING }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var budgetToDelete by remember { mutableStateOf<Budget?>(null) }
    var showCycleDialog by remember { mutableStateOf(false) }
    var showSmartDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCycleDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "周期设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingBudget = null
                showEditDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加预算")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            item { FreeBalanceCard(overview) }

            item {
                CycleCard(
                    cycle = cycle,
                    startDay = cycleStartDay,
                    onEditCycle = { showCycleDialog = true }
                )
            }

            item {
                SectionHeader(
                    title = "固定开销",
                    subtitle = "房租、水电这类每月必花",
                    action = "智能生成",
                    onAction = { showSmartDialog = true }
                )
            }
            if (fixedBudgets.isEmpty()) {
                item {
                    Text(
                        "还没有固定开销。点「智能生成」按上个周期实际支出自动建项，或右下角 + 手动添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(fixedBudgets, key = { it.id }) { budget ->
                    BudgetItemCard(
                        budget = budget,
                        spentCents = BudgetMath.spentForItem(
                            budget = budget,
                            expenseByCategory = cycleByCategory,
                            totalExpenseCents = cycleExpense,
                            fixedCategories = fixedCategories
                        ),
                        onClick = {
                            editingBudget = budget
                            showEditDialog = true
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                SectionHeader(
                    title = "浮动预算",
                    subtitle = "餐饮、购物等日常浮动开销",
                    action = null,
                    onAction = {}
                )
            }
            if (floatingBudgets.isEmpty()) {
                item {
                    Text(
                        "还没有浮动预算。可以设一个覆盖全部支出的日常额度（例如 2000），" +
                            "超支会标红；不设也不影响记账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(floatingBudgets, key = { it.id }) { budget ->
                    BudgetItemCard(
                        budget = budget,
                        spentCents = BudgetMath.spentForItem(
                            budget = budget,
                            expenseByCategory = cycleByCategory,
                            totalExpenseCents = cycleExpense,
                            fixedCategories = fixedCategories
                        ),
                        onClick = {
                            editingBudget = budget
                            showEditDialog = true
                        }
                    )
                }
            }

            item {
                Text(
                    "说明：可自由分配余额 = 当前余额 − 固定开销未花预留 − 浮动预算未花预留。" +
                        "已经花掉的钱不会再从余额里扣第二次；预算只做提示，不会阻止记账。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showEditDialog) {
        BudgetEditDialog(
            budget = editingBudget,
            categories = remember { AppPrefs.getExpenseCategories(context) },
            lastCycleSpend = previousByCategory,
            lastCycleTotalCents = previousByCategory.values.sum(),
            lastCycleLabel = previousCycle.label,
            onDismiss = { showEditDialog = false },
            onConfirm = { budget, amountCents, category, kind ->
                val scope = if (category.isBlank()) Budget.SCOPE_TOTAL else Budget.SCOPE_CATEGORY
                if (budget == null) {
                    viewModel.addBudget(kind, scope, category, amountCents)
                } else {
                    viewModel.updateBudgetFull(budget, amountCents, category, kind, scope)
                }
                showEditDialog = false
            }
        )
    }

    if (showSmartDialog) {
        SmartBudgetDialog(
            lastCycleLabel = previousCycle.label,
            lastCycleByCategory = previousByCategory,
            existingFixedCategories = fixedCategories,
            onDismiss = { showSmartDialog = false },
            onConfirm = { selected ->
                if (selected.isEmpty()) {
                    showSmartDialog = false
                    return@SmartBudgetDialog
                }
                viewModel.addBudgets(
                    selected.map { (category, cents) ->
                        Budget(
                            kind = Budget.KIND_FIXED,
                            scope = Budget.SCOPE_CATEGORY,
                            category = category,
                            monthlyAmountCents = cents
                        )
                    }
                )
                Toast.makeText(
                    context,
                    "已按上个周期生成 ${selected.size} 条固定开销",
                    Toast.LENGTH_SHORT
                ).show()
                showSmartDialog = false
            }
        )
    }

    if (showCycleDialog) {
        CycleSettingsDialog(
            currentDay = cycleStartDay,
            cycle = cycle,
            onDismiss = { showCycleDialog = false },
            onConfirm = { day ->
                val clamped = BillingCycleCalculator.clampStartDay(day)
                cycleStartDay = clamped
                AppPrefs.setBudgetCycleStartDay(context, clamped)
                showCycleDialog = false
            }
        )
    }

    budgetToDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("删除预算？") },
            text = {
                Text(
                    if (budget.category.isBlank()) "将删除「全部支出」的预算。"
                    else "将删除「${budget.category}」的预算。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBudget(budget)
                    budgetToDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FreeBalanceCard(overview: com.diandi.bookkeeping.util.BudgetOverview) {
    val free = overview.freeCents
    val accent = when {
        free < 0L -> MaterialTheme.colorScheme.error
        overview.reservedCents > overview.balanceCents -> MaterialTheme.colorScheme.error
        free == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("可自由分配余额", style = MaterialTheme.typography.titleSmall)
            Text(
                formatMoney(free),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                "当前余额 ${formatMoney(overview.balanceCents)} " +
                    "− 固定预留 ${formatMoney(overview.fixedReservedCents)} " +
                    "− 浮动预留 ${formatMoney(overview.floatingReservedCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (free < 0L) {
                Text(
                    "已超出可支配范围 ${formatMoney(-free)}，这个周期需要控制一下。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!overview.hasFixed && !overview.hasFloating) {
                Text(
                    "还没有任何预算，先设固定开销或浮动预算，这里才会显示可自由分配余额。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    action: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun CycleCard(
    cycle: BillingCycle,
    startDay: Int,
    onEditCycle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("本资金周期", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${cycle.start.year}年 ${cycle.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onEditCycle) { Text("每月${startDay}号起") }
        }
    }
}

@Composable
private fun BudgetItemCard(
    budget: Budget,
    spentCents: Long,
    onClick: () -> Unit
) {
    val progress = Money.ratio(spentCents, budget.monthlyAmountCents).toFloat().coerceIn(0f, 1f)
    val remaining = budget.monthlyAmountCents - spentCents
    val over = remaining < 0L
    val warn = !over && progress >= 0.8f
    val barColor = when {
        over -> MaterialTheme.colorScheme.error
        warn -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    budget.category.ifBlank { "全部支出" },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${formatMoney(spentCents)} / ${formatMoney(budget.monthlyAmountCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = barColor
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = barColor
            )
            Text(
                when {
                    over -> "已超支 ${formatMoney(-remaining)}"
                    warn -> "快用完了，还剩 ${formatMoney(remaining)}"
                    else -> "剩余 ${formatMoney(remaining)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetEditDialog(
    budget: Budget?,
    categories: List<String>,
    lastCycleSpend: Map<String, Long>,
    lastCycleTotalCents: Long,
    lastCycleLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Budget?, Long, String, String) -> Unit
) {
    var amountText by remember(budget?.id) {
        mutableStateOf(budget?.let { Money.toInputText(it.monthlyAmountCents) } ?: "")
    }
    var isFixed by remember(budget?.id) {
        mutableStateOf(budget?.kind != Budget.KIND_FLOATING)
    }
    var selected by remember(budget?.id) {
        mutableStateOf(
            budget?.let { TransactionCategories.decode(it.category).toSet() } ?: emptySet()
        )
    }
    var invalid by remember(budget?.id) { mutableStateOf(false) }

    // 参考上个周期：固定开销按选中类目求和；浮动（全部支出）用上个周期总支出
    val referenceCents = if (isFixed || selected.isNotEmpty()) {
        val sum = selected.sumOf { lastCycleSpend[it] ?: 0L }
        if (sum > 0L) sum else null
    } else {
        lastCycleTotalCents.takeIf { it > 0L }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget == null) "新建预算" else "修改预算") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isFixed,
                        onClick = { isFixed = true },
                        label = { Text("固定开销") }
                    )
                    FilterChip(
                        selected = !isFixed,
                        onClick = { isFixed = false },
                        label = { Text("浮动预算") }
                    )
                }
                Text(
                    if (isFixed) "房租、水电等每月必花的项目，金额相对固定。"
                    else "日常浮动的开销额度；不选大类表示覆盖全部非固定支出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text("每周期金额") },
                    singleLine = true,
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (referenceCents != null) {
                    TextButton(onClick = { amountText = Money.toInputText(referenceCents) }) {
                        Text("填入上个周期实际支出 ${formatMoney(referenceCents)}")
                    }
                } else {
                    Text(
                        "正在收集数据：上个周期（$lastCycleLabel）没有可参考的支出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (isFixed) "选择大类（必选，可多选）" else "选择大类（可留空 = 全部支出）",
                    style = MaterialTheme.typography.bodySmall
                )
                if (categories.isEmpty()) {
                    Text(
                        "还没有支出类目，先去记一笔或编辑类目。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                categories.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { category ->
                            AssistChip(
                                onClick = {
                                    selected = if (category in selected) selected - category
                                    else selected + category
                                },
                                label = { Text(category) },
                                colors = if (category in selected) {
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                } else {
                                    AssistChipDefaults.assistChipColors()
                                }
                            )
                        }
                    }
                }
                if (invalid) {
                    Text(
                        "金额须大于 0；固定开销至少要选一个大类。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parseToCents(amountText)
                val category = TransactionCategories.encode(selected)
                val validCategories = !isFixed || selected.isNotEmpty()
                if (cents != null && cents > 0L && validCategories) {
                    onConfirm(
                        budget,
                        cents,
                        category,
                        if (isFixed) Budget.KIND_FIXED else Budget.KIND_FLOATING
                    )
                } else {
                    invalid = true
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 智能生成固定开销：列出上个周期有支出的分类，用户勾选哪些属于固定开销，
 * 确定后按这些分类的实际金额建预算项。
 */
@Composable
private fun SmartBudgetDialog(
    lastCycleLabel: String,
    lastCycleByCategory: Map<String, Long>,
    existingFixedCategories: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<String, Long>>) -> Unit
) {
    val candidates = remember(lastCycleByCategory, existingFixedCategories) {
        lastCycleByCategory
            .filterKeys { it.isNotBlank() }
            .filterKeys { it !in existingFixedCategories }
            .filterValues { it > 0L }
            .entries
            .sortedByDescending { it.value }
    }
    val selected = remember(candidates) { mutableStateMapOf<String, Boolean>().apply {
        candidates.forEach { put(it.key, true) }
    } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能生成固定开销") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        "正在收集数据：上个周期（$lastCycleLabel）没有可参考的分类支出，" +
                            "或者这些分类已经有固定开销了。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "上个周期（$lastCycleLabel）的分类支出如下。勾选属于固定开销的项目" +
                            "（房租、水电、话费…），确定后按实际金额建预算；餐饮购物这类不要勾。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    candidates.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.key, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "上个周期 ${formatMoney(entry.value)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = selected[entry.key] == true,
                                onCheckedChange = { selected[entry.key] = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        candidates.filter { selected[it.key] == true }
                            .map { it.key to it.value }
                    )
                },
                enabled = candidates.isNotEmpty()
            ) { Text("生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CycleSettingsDialog(
    currentDay: Int,
    cycle: BillingCycle,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var day by remember { mutableStateOf(currentDay.toFloat()) }
    val preview = remember(day) {
        BillingCycleCalculator.of(LocalDate.now(), day.roundToInt())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("资金周期开始日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "发薪日通常不是 1 号，把周期开始日设成发薪日，预算和开销才对得上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "每月 ${day.roundToInt()} 号开始 · 当前周期 ${preview.label}",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = day,
                    onValueChange = { day = it },
                    valueRange = BillingCycleCalculator.MIN_START_DAY.toFloat()..
                        BillingCycleCalculator.MAX_START_DAY.toFloat(),
                    steps = BillingCycleCalculator.MAX_START_DAY - BillingCycleCalculator.MIN_START_DAY - 1
                )
                Text(
                    "范围 1–28：29/30/31 在部分月份不存在，容易产生歧义。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day.roundToInt()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
