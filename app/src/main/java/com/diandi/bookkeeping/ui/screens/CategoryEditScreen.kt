package com.diandi.bookkeeping.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.util.Categories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) } // 0 expense 1 income
    var expenseList by remember { mutableStateOf(Categories.expense(context)) }
    var incomeList by remember { mutableStateOf(Categories.income(context)) }
    var newName by remember { mutableStateOf("") }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val currentList = if (tab == 0) expenseList else incomeList

    fun persist() {
        if (tab == 0) {
            AppPrefs.setExpenseCategories(context, expenseList)
        } else {
            AppPrefs.setIncomeCategories(context, incomeList)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑类目") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showRestoreConfirm = true }) {
                        Text("一键还原")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "点首页标题「点滴」7 次可进入本页。支出与收入类目分开维护。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("支出") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("收入") })
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("新类目名称") }
                )
                Button(
                    onClick = {
                        val name = newName.trim()
                        if (name.isBlank()) return@Button
                        if (tab == 0) {
                            if (name in expenseList) {
                                Toast.makeText(context, "已存在", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            expenseList = expenseList + name
                            AppPrefs.setExpenseCategories(context, expenseList)
                        } else {
                            if (name in incomeList) {
                                Toast.makeText(context, "已存在", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            incomeList = incomeList + name
                            AppPrefs.setIncomeCategories(context, incomeList)
                        }
                        newName = ""
                        Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("添加") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "共 ${currentList.size} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(currentList, key = { it }) { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(
                            onClick = {
                                if (currentList.size <= 1) {
                                    Toast.makeText(context, "至少保留一个类目", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                if (tab == 0) {
                                    expenseList = expenseList - name
                                    AppPrefs.setExpenseCategories(context, expenseList)
                                } else {
                                    incomeList = incomeList - name
                                    AppPrefs.setIncomeCategories(context, incomeList)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("还原预设类目？") },
            text = {
                Text(
                    if (tab == 0) "将支出类目恢复为应用内置列表，当前自定义支出类目会被覆盖。"
                    else "将收入类目恢复为应用内置列表，当前自定义收入类目会被覆盖。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tab == 0) {
                        AppPrefs.restoreExpenseCategories(context)
                        expenseList = Categories.expense(context)
                    } else {
                        AppPrefs.restoreIncomeCategories(context)
                        incomeList = Categories.income(context)
                    }
                    showRestoreConfirm = false
                    Toast.makeText(context, "已还原", Toast.LENGTH_SHORT).show()
                }) { Text("还原") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }
}
