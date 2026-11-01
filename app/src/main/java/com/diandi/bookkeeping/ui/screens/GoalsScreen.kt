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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.util.allocateBalanceByWeight
import com.diandi.bookkeeping.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val goals by viewModel.goals.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val allocatedByGoal = remember(goals, balance) { allocateBalanceByWeight(balance, goals) }
    var showAddDialog by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<SavingsGoal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("攒钱目标") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "添加目标")
            }
        }
    ) { padding ->
        if (goals.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有攒钱目标，点击右下角添加")
                Spacer(Modifier.height(8.dp))
                Text(
                    "添加后可为每个目标设置分配权重；当前正余额会按权重比例分配。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "当前正余额按目标权重比例智能分配；手动记录进度会与分配金额合并显示。权重越大，分到的余额比例越高。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        allocatedAmount = allocatedByGoal[goal.id] ?: 0.0,
                        onAddProgress = { amount -> viewModel.updateGoalProgress(goal, amount) },
                        onWeightChange = { weight -> viewModel.updateGoalWeight(goal, weight) },
                        onDelete = { goalToDelete = goal }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, target, weight ->
                viewModel.addGoal(title, target, weight)
                showAddDialog = false
            }
        )
    }

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("删除攒钱目标？") },
            text = {
                Text("确定要删除“${goal.title}”吗？目标及其手动记录将被删除，且无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGoal(goal)
                        goalToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { goalToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: SavingsGoal,
    allocatedAmount: Double,
    onAddProgress: (Double) -> Unit,
    onWeightChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var addAmount by remember(goal.id) { mutableStateOf("") }
    var weightText by remember(goal.id, goal.allocationWeight) {
        mutableStateOf(formatWeight(goal.allocationWeight))
    }
    var weightError by remember(goal.id) { mutableStateOf(false) }
    val displayedAmount = goal.currentAmount + allocatedAmount
    val progress = if (goal.targetAmount > 0) {
        (displayedAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    } else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(goal.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "${formatMoney(displayedAmount)} / ${formatMoney(goal.targetAmount)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "手动记录 ${formatMoney(goal.currentAmount)}  +  余额分配 ${formatMoney(allocatedAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it.filter { c -> c.isDigit() || c == '.' }.take(8)
                        weightError = false
                    },
                    label = { Text("分配权重") },
                    supportingText = {
                        Text(if (weightError) "请输入 0.01–1000 之间的权重" else "当前权重 ${formatWeight(goal.allocationWeight)}")
                    },
                    isError = weightError,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val weight = weightText.toDoubleOrNull()
                    if (weight != null && weight.isFinite() && weight in 0.01..1000.0) {
                        onWeightChange(weight)
                        weightError = false
                    } else {
                        weightError = true
                    }
                }) {
                    Text("保存")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addAmount,
                    onValueChange = { addAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("手动记录") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    addAmount.toDoubleOrNull()?.let {
                        if (it.isFinite() && it > 0) {
                            onAddProgress(it)
                            addAmount = ""
                        }
                    }
                }) {
                    Text("存入")
                }
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("1") }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建攒钱目标") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("目标名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("目标金额") },
                    singleLine = true,
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                    label = { Text("余额分配权重") },
                    supportingText = { Text("权重越大，分配到的余额比例越高；默认 1。") },
                    isError = invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (invalid) {
                    Text("目标金额须大于 0，权重范围为 0.01–1000。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val targetValue = target.toDoubleOrNull()
                    val weightValue = weight.toDoubleOrNull()
                    if (title.isNotBlank() && targetValue != null && targetValue.isFinite() && targetValue > 0 &&
                        weightValue != null && weightValue.isFinite() && weightValue in 0.01..1000.0
                    ) {
                        onConfirm(title.trim(), targetValue, weightValue)
                    } else {
                        invalid = true
                    }
                }
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
