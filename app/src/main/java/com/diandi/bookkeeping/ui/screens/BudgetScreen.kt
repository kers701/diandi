package com.diandi.bookkeeping.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.diandi.bookkeeping.data.entity.Budget
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.util.BillingCycle
import com.diandi.bookkeeping.util.BillingCycleCalculator
import com.diandi.bookkeeping.util.Money
import com.diandi.bookkeeping.util.SmartBudget
import com.diandi.bookkeeping.util.TransactionCategories
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 预算页。
 *
 * - 资金月周期可自定义开始日（默认 1 号），因为发薪日通常不是 1 号；
 *   所以这里所有"本月"统计都按 [BillingCycle] 的日期区间查，不按自然月。
 * - 智能预算：用上一个周期的实际分类支出，一键生成还没预算的大类预算；
 *   上个周期没有数据时提示"正在收集数据"。
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
    val cycle = remember(today, cycleStartDay) {
        BillingCycleCalculator.of(today, cycleStartDay)
    }
    val previousCycle = remember(today, cycleStartDay) {
        BillingCycleCalculator.previous(today, cycleStartDay)
    }

    val budgets by viewModel.budgets.collectAsState()
    val cycleExpense by remember(cycle) {
        viewModel.expenseInRange(cycle.startDate, cycle.endDate)
    }.collectAsState(initial = 0L)
    val cycleByCategory by remember(cycle) {
        viewModel.expenseByCategoryInRange(cycle.startDate, cycle.endDate)
    }.collectAsState(initial = emptyMap())
    val previousByCategory by remember(previousCycle) {
        viewModel.expenseByCategoryInRange(previousCycle.startDate, previousCycle.endDate)
    }.collectAsState(initial = emptyMap())

    var showEditDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var budgetToDelete by remember { mutableStateOf<Budget?>(null) }
    var showCycleDialog by remember { mutableStateOf(false) }

    val totalBudget = budgets.firstOrNull { it.scope == Budget.SCOPE_TOTAL }
    val categoryBudgets = budgets.filter { it.scope == Budget.SCOPE_CATEGORY }
    val previousTotal = previousByCategory.values.sum()

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

            item {
                CycleCard(
                    cycle = cycle,
                    startDay = cycleStartDay,
                    onEditCycle = { showCycleDialog = true }
                )
            }

            item {
                TotalBudgetCard(
                    budget = totalBudget,
                    spentCents = cycleExpense,
                    cycleLabel = cycle.label,
                    onEdit = {
                        editingBudget = totalBudget
                        showEditDialog = true
                    },
                    onDelete = { totalBudget?.let { budgetToDelete = it } }
                )
            }

            item {
                SmartBudgetCard(
                    previousLabel = previousCycle.label,
                    previousTotalCents = previousTotal,
                    hasPreviousData = previousByCategory.isNotEmpty(),
                    suggestionCount = SmartBudget.suggest(
                        previousByCategory,
                        categoryBudgets.map { it.category }.toSet()
                    ).size,
                    onGenerate = {
                        val suggestions = SmartBudget.suggest(
                            previousByCategory,
                            categoryBudgets.map { it.category }.toSet()
                        )
                        when {
                            previousByCategory.isEmpty() -> {
                                Toast.makeText(
                                    context,
                                    "正在收集数据：上个周期还没有支出记录，下个周期起即可自动生成",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            suggestions.isEmpty() -> {
                                Toast.makeText(
                                    context,
                                    "上个周期的支出分类都已经有预算了",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else -> {
                                viewModel.addBudgets(
                                    suggestions.map {
                                        Budget(
                                            scope = Budget.SCOPE_CATEGORY,
                                            category = it.category,
                                            monthlyAmountCents = it.amountCents
                                        )
                                    }
                                )
                                Toast.makeText(
                                    context,
                                    "已按上个周期生成 ${suggestions.size} 条预算",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("分类预算", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "按大类控制支出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (categoryBudgets.isEmpty()) {
                item {
                    Text(
                        "还没有分类预算。点右下角 + 单条添加，或用上面的智能预算按上个周期自动生成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(categoryBudgets, key = { it.id }) { budget ->
                    CategoryBudgetCard(
                        budget = budget,
                        spentCents = spentForBudget(budget, cycleByCategory),
                        onClick = {
                            editingBudget = budget
                            showEditDialog = true
                        }
                    )
                }
            }

            item {
                Text(
                    "提示：预算只做提示，不会阻止记账。已用金额按当前资金周期实时统计；" +
                        "周期开始日可在右上角修改，修改后本页统计立即按新周期计算。",
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
            lastCycleTotalCents = previousTotal,
            lastCycleLabel = previousCycle.label,
            onDismiss = { showEditDialog = false },
            onConfirm = { budget, amountCents, category, categoryScope ->
                if (budget == null) {
                    if (categoryScope) {
                        viewModel.addBudget(Budget.SCOPE_CATEGORY, category, amountCents)
                    } else {
                        viewModel.addBudget(Budget.SCOPE_TOTAL, "", amountCents)
                    }
                } else {
                    viewModel.updateBudget(
                        budget.copy(
                            scope = if (categoryScope) Budget.SCOPE_CATEGORY else Budget.SCOPE_TOTAL,
                            category = category
                        ),
                        amountCents,
                        budget.enabled
                    )
                }
                showEditDialog = false
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
                    if (budget.scope == Budget.SCOPE_TOTAL) "将删除总预算。"
                    else "将删除「${budget.category}」的分类预算。"
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

/** 某条预算在当前周期内的已用金额（分） */
private fun spentForBudget(budget: Budget, expenseByCategory: Map<String, Long>): Long {
    if (budget.scope == Budget.SCOPE_TOTAL) return expenseByCategory.values.sum()
    val categories = TransactionCategories.decode(budget.category)
    return categories.sumOf { expenseByCategory[it] ?: 0L }
}

@Composable
private fun CycleCard(
    cycle: BillingCycle,
    startDay: Int,
    onEditCycle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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
}

@Composable
private fun SmartBudgetCard(
    previousLabel: String,
    previousTotalCents: Long,
    hasPreviousData: Boolean,
    suggestionCount: Int,
    onGenerate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("智能预算", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!hasPreviousData) {
                Text(
                    "正在收集数据：上个周期（$previousLabel）还没有支出记录，" +
                        "有了数据后就能按实际支出自动生成预算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "上个周期（$previousLabel）共支出 ${formatMoney(previousTotalCents)}，" +
                        "其中还有 $suggestionCount 个分类没有预算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "房租、水电、话费这类固定支出，直接按上个周期实际金额生成，省得每月手填。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onGenerate,
                enabled = hasPreviousData && suggestionCount > 0
            ) {
                Text(if (!hasPreviousData) "正在收集数据…" else "按上个周期生成预算")
            }
        }
    }
}

@Composable
private fun TotalBudgetCard(
    budget: Budget?,
    spentCents: Long,
    cycleLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (budget != null) {
        Money.ratio(spentCents, budget.monthlyAmountCents).toFloat().coerceIn(0f, 1f)
    } else 0f
    val remaining = (budget?.monthlyAmountCents ?: 0L) - spentCents
    val over = budget != null && remaining < 0L
    val warn = budget != null && !over && progress >= 0.8f
    val accent = when {
        over -> MaterialTheme.colorScheme.error
        warn -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("总预算（$cycleLabel）", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEdit) { Text(if (budget == null) "设置" else "修改") }
                    if (budget != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除总预算",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (budget == null) {
                Text(
                    "还没有设置总预算。设置后首页会显示本周期还能花多少。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Card
            }
            Text(
                "${formatMoney(spentCents)} / ${formatMoney(budget.monthlyAmountCents)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = accent
            )
            Text(
                if (over) "已超支 ${formatMoney(-remaining)}"
                else "剩余 ${formatMoney(remaining)} · 已用 ${"%.0f".format(progress * 100)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryBudgetCard(
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(budget.category.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall)
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
    onConfirm: (Budget?, Long, String, Boolean) -> Unit
) {
    var amountText by remember(budget?.id) {
        mutableStateOf(budget?.let { Money.toInputText(it.monthlyAmountCents) } ?: "")
    }
    var isCategoryScope by remember(budget?.id) {
        mutableStateOf(budget?.scope == Budget.SCOPE_CATEGORY)
    }
    var selected by remember(budget?.id) {
        mutableStateOf(
            budget?.let { TransactionCategories.decode(it.category).toSet() } ?: emptySet()
        )
    }
    var invalid by remember(budget?.id) { mutableStateOf(false) }

    // 参考上个周期：分类模式按选中的类目求和，总预算模式用上个周期总支出
    val referenceCents = if (isCategoryScope) {
        SmartBudget.referenceAmount(lastCycleSpend, selected)
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
                        selected = !isCategoryScope,
                        onClick = { isCategoryScope = false },
                        label = { Text("总预算") }
                    )
                    FilterChip(
                        selected = isCategoryScope,
                        onClick = { isCategoryScope = true },
                        label = { Text("按大类") }
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text("每周期预算金额") },
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
                if (isCategoryScope) {
                    Text("选择大类（可多选，合并计算）", style = MaterialTheme.typography.bodySmall)
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
                }
                if (invalid) {
                    Text(
                        "金额须大于 0；按大类预算需至少选一个类目。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parseToCents(amountText)
                val category = if (isCategoryScope) TransactionCategories.encode(selected) else ""
                if (cents != null && cents > 0L && (!isCategoryScope || selected.isNotEmpty())) {
                    onConfirm(budget, cents, category, isCategoryScope)
                } else {
                    invalid = true
                }
            }) { Text("保存") }
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
                Text(
                    "改完后本页的「已用金额」会立即按新周期重新统计，历史账单不受影响。",
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
