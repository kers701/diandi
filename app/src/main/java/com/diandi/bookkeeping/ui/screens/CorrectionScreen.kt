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
            FloatingActionButton(onClick = { showAdd = true }) {
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
                "说明：应用初始余额为 0。添加修正（如 +800）后，当前余额会在此基础上计算收入与支出。",
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
                    items(corrections, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        formatMoney(c.amount),
                                        fontWeight = FontWeight.Bold,
                                        color = if (c.amount >= 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                    if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${c.year}-${c.month.toString().padStart(2, '0')}-${c.day.toString().padStart(2, '0')}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { correctionToDelete = c }) {
                                    Icon(Icons.Default.Delete, "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加资金修正") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it.filter { c -> c.isDigit() || c == '.' || c == '-' }
                        },
                        label = { Text("修正金额（可负）") },
                        singleLine = true,
                        prefix = { Text("¥ ") }
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    amount.toDoubleOrNull()?.let {
                        viewModel.addCorrection(it, note)
                        showAdd = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }

    correctionToDelete?.let { correction ->
        AlertDialog(
            onDismissRequest = { correctionToDelete = null },
            title = { Text("删除资金修正？") },
            text = {
                Text("确定要删除 ${formatMoney(correction.amount)} 这条资金修正吗？删除后无法恢复。")
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
