package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val goals by viewModel.goals.collectAsState()
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
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有攒钱目标，点击右下角添加")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        onAddProgress = { amount -> viewModel.updateGoalProgress(goal, amount) },
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

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("删除攒钱目标？") },
            text = {
                Text("确定要删除“${goal.title}”吗？目标及其进度记录将被删除，且无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGoal(goal)
                        goalToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
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
    onAddProgress: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var addAmount by remember { mutableStateOf("") }
    val progress = if (goal.targetAmount > 0) {
        (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    } else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
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
                "${formatMoney(goal.currentAmount)} / ${formatMoney(goal.targetAmount)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addAmount,
                    onValueChange = { addAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("存入金额") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        addAmount.toDoubleOrNull()?.let {
                            if (it > 0) {
                                onAddProgress(it)
                                addAmount = ""
                            }
                        }
                    }
                ) {
                    Text("存入")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建攒钱目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    prefix = { Text("¥ ") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = target.toDoubleOrNull()
                    if (title.isNotBlank() && t != null && t > 0) {
                        onConfirm(title, t)
                    }
                }
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
