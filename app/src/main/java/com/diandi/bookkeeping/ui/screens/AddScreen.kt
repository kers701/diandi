package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.util.Categories
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    viewModel: MainViewModel,
    initialType: String? = null,
    editId: Long? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(editId == null) }
    var type by remember { mutableStateOf(initialType ?: "EXPENSE") }
    var amountText by remember { mutableStateOf("") }
    var category by remember {
        mutableStateOf(
            if (initialType == "INCOME") Categories.income(context).first()
            else Categories.expense(context).first()
        )
    }
    var tags by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(editId) {
        if (editId != null) {
            val tx = withContext(Dispatchers.IO) {
                // load via viewModel suspend
                viewModel.loadTransaction(editId)
            }
            if (tx != null) {
                type = tx.type
                amountText = if (tx.amount == tx.amount.toLong().toDouble()) {
                    tx.amount.toLong().toString()
                } else {
                    "%.2f".format(tx.amount)
                }
                category = tx.category
                tags = tx.tags
                note = tx.note
                date = tx.toLocalDate()
                createdAt = tx.createdAt
            }
            ready = true
        }
    }

    val categories = Categories.getByType(context, type)
    // 若编辑时类目已不在列表，仍显示当前值
    val displayCategories = remember(categories, category) {
        if (category.isNotBlank() && category !in categories) categories + category else categories
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editId != null && type == "EXPENSE" -> "编辑支出"
                            editId != null -> "编辑收入"
                            type == "EXPENSE" -> "添加支出"
                            else -> "添加收入"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeChip("支出", type == "EXPENSE", Color(0xFFD32F2F)) {
                    type = "EXPENSE"
                    val list = Categories.expense(context)
                    if (category !in list) category = list.first()
                }
                TypeChip("收入", type == "INCOME", Color(0xFF388E3C)) {
                    type = "INCOME"
                    val list = Categories.income(context)
                    if (category !in list) category = list.first()
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("金额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("¥ ") }
            )

            Text("选择大类", style = MaterialTheme.typography.titleSmall)
            displayCategories.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("自定义标签（逗号分隔）") },
                placeholder = { Text("如：午餐,同事聚餐") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "日期: ${date.year}-${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        if (editId != null) {
                            viewModel.updateTransaction(
                                id = editId,
                                amount = amount,
                                type = type,
                                category = category,
                                tags = tags,
                                note = note,
                                date = date,
                                createdAt = createdAt
                            )
                        } else {
                            viewModel.addTransaction(
                                amount = amount,
                                type = type,
                                category = category,
                                tags = tags,
                                note = note,
                                date = date
                            )
                        }
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text(if (editId != null) "保存修改" else "保存")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
