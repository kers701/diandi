package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.diandi.bookkeeping.util.Money
import com.diandi.bookkeeping.util.TransactionCategories
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate

/**
 * 预算：每月总预算 + 按大类预算，显示本月已用与超支情况。
 *
 * 已用金额来自本月支出；分类预算的类目可以是多选组合（如「餐饮 + 交通」），
 * 组合内的类目支出相加即为该预算的已用金额。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val budgets by viewModel.budgets.collectAsState()
    val monthExpense by remember(today.year, today.monthValue) {
        viewModel.monthExpense(today.year, today.monthValue)
    }.collectAsState(initial = 0L)
    val expenseByCategory by remember(today.year, today.monthValue) {
        viewModel.monthExpenseByCategory(today.year, today.monthValue)
    }.collectAsState(initial = emptyMap())

    var showEditDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var budgetToDelete by remember { mutableStateOf<Budget?>(null) }

    val totalBudget = budgets.firstOrNull { it.scope == Budget.SCOPE_TOTAL }
    val categoryBudgets = budgets.filter { it.scope == Budget.SCOPE_CATEGORY }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            item { Spacer(Modifier.height(4.dp)) }

            item {
                TotalBudgetCard(
                    budget = totalBudget,
                    spentCents = monthExpense,
                    monthLabel = "${today.year}年${today.monthValue}月",
                    onEdit = {
                        editingBudget = totalBudget
                        showEditDialog = true
                    },
                    onDelete = { totalBudget?.let { budgetToDelete = it } }
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
                        "还没有分类预算。点右下角 + 可以给某个大类单独设上限，比如「餐饮 1500」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(categoryBudgets, key = { it.id }) { budget ->
                    CategoryBudgetCard(
                        budget = budget,
                        spentCents = spentForBudget(budget, expenseByCategory),
                        onClick = {
                            editingBudget = budget
                            showEditDialog = true
                        }
                    )
                }
            }

            item {
                Text(
                    "提示：预算只做提示，不会阻止记账。已用金额按本月支出实时统计。",
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

    budgetToDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("删除预算？") },
            text = {
                Text(
                    if (budget.scope == Budget.SCOPE_TOTAL) "将删除本月总预算。"
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

/** 某条预算本月已用金额（分） */
private fun spentForBudget(budget: Budget, expenseByCategory: Map<String, Long>): Long {
    if (budget.scope == Budget.SCOPE_TOTAL) return expenseByCategory.values.sum()
    val categories = TransactionCategories.decode(budget.category)
    return categories.sumOf { expenseByCategory[it] ?: 0L }
}

@Composable
private fun TotalBudgetCard(
    budget: Budget?,
    spentCents: Long,
    monthLabel: String,
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
                Text("$monthLabel 总预算", style = MaterialTheme.typography.titleSmall)
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
                    "还没有设置本月总预算。设置后首页会显示本月还能花多少。",
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
                        label = { Text("本月总共") }
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
                    label = { Text("每月预算金额") },
                    singleLine = true,
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
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
                                    } else AssistantChipColorsDefault()
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
                val category = if (isCategoryScope) {
                    TransactionCategories.encode(selected)
                } else ""
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
private fun AssistantChipColorsDefault() = AssistChipDefaults.assistChipColors()
