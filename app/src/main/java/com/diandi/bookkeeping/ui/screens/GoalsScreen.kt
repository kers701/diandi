package com.diandi.bookkeeping.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    var goalToEdit by remember { mutableStateOf<SavingsGoal?>(null) }
    var goalToDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    var goalToReset by remember { mutableStateOf<SavingsGoal?>(null) }

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
                    "添加后可在编辑中设置权重；当前正余额会按权重比例分配。",
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
                        "向左滑动目标可退出操作栏、编辑或删除。当前正余额按权重分配，手动记录可存入、降低、撤销最近一次存入或重置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        allocatedAmount = allocatedByGoal[goal.id] ?: 0.0,
                        onAddProgress = { amount -> viewModel.updateGoalProgress(goal, amount) },
                        onReduceProgress = { amount -> viewModel.reduceGoalProgress(goal, amount) },
                        onUndoProgress = { viewModel.undoLastGoalDeposit(goal) },
                        onResetProgress = { goalToReset = goal },
                        onEdit = { goalToEdit = goal },
                        onDelete = { goalToDelete = goal }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, target ->
                viewModel.addGoal(title, target)
                showAddDialog = false
            }
        )
    }

    goalToEdit?.let { goal ->
        EditGoalDialog(
            goal = goal,
            onDismiss = { goalToEdit = null },
            onConfirm = { title, target, weight ->
                viewModel.updateGoalDetails(goal, title, target, weight)
                goalToEdit = null
            }
        )
    }

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("删除攒钱目标？") },
            text = { Text("确定要删除“${goal.title}”吗？目标及其手动记录将被删除，且无法恢复。") },
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

    goalToReset?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToReset = null },
            title = { Text("重置手动进度？") },
            text = { Text("将“${goal.title}”的手动记录进度清零；按余额分配的金额不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetGoalProgress(goal)
                        goalToReset = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { goalToReset = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: SavingsGoal,
    allocatedAmount: Double,
    onAddProgress: (Double) -> Unit,
    onReduceProgress: (Double) -> Unit,
    onUndoProgress: () -> Unit,
    onResetProgress: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var amountText by remember(goal.id) { mutableStateOf("") }
    var revealed by remember(goal.id) { mutableStateOf(false) }
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val lockedHeight = if (cardHeightPx > 0) {
        with(density) { cardHeightPx.toDp() }.coerceAtLeast(200.dp)
    } else 200.dp
    val manualAmount = amountText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val displayedAmount = goal.currentAmount + allocatedAmount
    val progress = if (goal.targetAmount > 0.0) {
        (displayedAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    } else 0f

    Crossfade(
        targetState = revealed,
        animationSpec = tween(durationMillis = 220),
        label = "goal-actions"
    ) { showActions ->
        if (showActions) {
            Card(
                modifier = Modifier.fillMaxWidth().height(lockedHeight),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { revealed = false }) {
                        Icon(Icons.Default.Close, "退出", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        revealed = false
                        onEdit()
                    }) {
                        Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        revealed = false
                        onDelete()
                    }) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { cardHeightPx = it.height }
                    .pointerInput(goal.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = { },
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount < -12f) revealed = true
                            }
                        )
                    },
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(goal.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                        label = { Text("手动进度金额") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = manualAmount != null,
                            onClick = { manualAmount?.let { onAddProgress(it); amountText = "" } }
                        ) { Text("存入") }
                        TextButton(
                            enabled = manualAmount != null && goal.currentAmount > 0.0,
                            onClick = { manualAmount?.let { onReduceProgress(it); amountText = "" } }
                        ) { Text("降低") }
                        TextButton(
                            enabled = goal.lastManualDeposit > 0.0,
                            onClick = onUndoProgress
                        ) { Text("撤销") }
                        TextButton(
                            enabled = goal.currentAmount > 0.0,
                            onClick = onResetProgress
                        ) { Text("重置") }
                    }
                    if (goal.lastManualDeposit > 0.0) {
                        Text(
                            "最近存入 ${formatMoney(goal.lastManualDeposit)} 可撤销",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建攒钱目标") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
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
                if (invalid) {
                    Text("目标名称不能为空，目标金额须大于 0。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val targetValue = target.toDoubleOrNull()
                if (title.isNotBlank() && targetValue != null && targetValue.isFinite() && targetValue > 0.0) {
                    onConfirm(title.trim(), targetValue)
                } else {
                    invalid = true
                }
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun EditGoalDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double) -> Unit
) {
    var title by remember(goal.id) { mutableStateOf(goal.title) }
    var target by remember(goal.id) { mutableStateOf(formatMoneyInput(goal.targetAmount)) }
    var weight by remember(goal.id) { mutableStateOf(formatWeight(goal.allocationWeight)) }
    var invalid by remember(goal.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑攒钱目标") },
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
                    onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text("目标金额") },
                    singleLine = true,
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                    label = { Text("分配权重") },
                    supportingText = { Text("权重越大，从当前正余额中分得的比例越高。") },
                    isError = invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (invalid) {
                    Text("名称不能为空；目标金额须大于 0；权重范围为 0.01–1000。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val targetValue = target.toDoubleOrNull()
                val weightValue = weight.toDoubleOrNull()
                if (title.isNotBlank() && targetValue != null && targetValue.isFinite() && targetValue > 0.0 &&
                    weightValue != null && weightValue.isFinite() && weightValue in 0.01..1000.0
                ) {
                    onConfirm(title.trim(), targetValue, weightValue)
                } else {
                    invalid = true
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()

private fun formatMoneyInput(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
