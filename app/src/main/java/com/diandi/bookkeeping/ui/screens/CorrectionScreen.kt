package com.diandi.bookkeeping.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.ui.components.formatMoney
import com.diandi.bookkeeping.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val corrections by viewModel.corrections.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var correctionToEdit by remember { mutableStateOf<FundCorrection?>(null) }
    var correctionToDelete by remember { mutableStateOf<FundCorrection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资金修正") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                correctionToEdit = null
                showAdd = true
            }) {
                Icon(Icons.Default.Add, "添加修正")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "说明：应用初始余额为 0。添加修正（如 +800）后，当前余额会在此基础上计算收入与支出。向左滑动记录可退出操作栏、编辑或删除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (corrections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无修正记录")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(corrections, key = { it.id }) { correction ->
                        CorrectionCard(
                            correction = correction,
                            onEdit = { correctionToEdit = correction },
                            onDelete = { correctionToDelete = correction }
                        )
                    }
                }
            }
        }
    }

    val editingCorrection = correctionToEdit
    if (showAdd || editingCorrection != null) {
        CorrectionEditorDialog(
            correction = editingCorrection,
            onDismiss = {
                showAdd = false
                correctionToEdit = null
            },
            onSave = { amount, source, note ->
                if (editingCorrection == null) {
                    viewModel.addCorrection(amount = amount, note = note, source = source)
                } else {
                    viewModel.updateCorrection(editingCorrection, amount, source, note)
                }
                showAdd = false
                correctionToEdit = null
            }
        )
    }

    correctionToDelete?.let { correction ->
        AlertDialog(
            onDismissRequest = { correctionToDelete = null },
            title = { Text("删除资金修正？") },
            text = {
                Text("确定要删除“${correction.source}”来源的 ${formatMoney(correction.amount)} 修正吗？删除后无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCorrection(correction)
                        correctionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { correctionToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CorrectionEditorDialog(
    correction: FundCorrection?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, source: String, note: String) -> Unit
) {
    var amountText by remember(correction?.id) {
        mutableStateOf(correction?.amount?.toString() ?: "")
    }
    var source by remember(correction?.id) {
        mutableStateOf(correction?.source ?: "手动")
    }
    var note by remember(correction?.id) {
        mutableStateOf(correction?.note ?: "")
    }
    val amount = amountText.toDoubleOrNull()?.takeIf { it.isFinite() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (correction == null) "添加资金修正" else "编辑资金修正") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { c -> c.isDigit() || c == '.' || c == '-' }
                    },
                    label = { Text("修正金额（可负）") },
                    singleLine = true,
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("修正来源") },
                    placeholder = { Text("例如：银行余额、现金盘点") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && source.isNotBlank(),
                onClick = { amount?.let { onSave(it, source.trim(), note.trim()) } }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CorrectionCard(
    correction: FundCorrection,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var revealed by remember(correction.id) { mutableStateOf(false) }
    var cardHeightPx by remember(correction.id) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val actionHeight = if (cardHeightPx > 0) {
        with(density) { cardHeightPx.toDp() }.coerceAtLeast(88.dp)
    } else 88.dp

    Crossfade(
        targetState = revealed,
        animationSpec = tween(durationMillis = 220),
        label = "correction-actions"
    ) { showActions ->
        if (showActions) {
            Card(
                modifier = Modifier.fillMaxWidth().height(actionHeight),
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
                    .pointerInput(correction.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = { },
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount < -12f) revealed = true
                            }
                        )
                    },
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            formatMoney(correction.amount),
                            fontWeight = FontWeight.Bold,
                            color = if (correction.amount >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Text("来源：${correction.source}", style = MaterialTheme.typography.bodySmall)
                        if (correction.note.isNotBlank()) {
                            Text(correction.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${correction.year}-${correction.month.toString().padStart(2, '0')}-${correction.day.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
