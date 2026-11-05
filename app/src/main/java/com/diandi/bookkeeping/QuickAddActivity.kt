package com.diandi.bookkeeping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.util.Money
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.ui.theme.DiandiTheme
import com.diandi.bookkeeping.util.Categories
import com.diandi.bookkeeping.util.TransactionCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.quick_add_fade_in, R.anim.quick_add_fade_out)
        val app = application as DiandiApp
        setContent {
            DiandiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    QuickAddScreen(
                        onSave = { amountCents, type, category, tags, note ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    val d = LocalDate.now()
                                    app.repository.addTransaction(
                                        Transaction(
                                            amountCents = amountCents,
                                            type = type,
                                            category = category,
                                            tags = tags,
                                            note = note,
                                            year = d.year,
                                            month = d.monthValue,
                                            day = d.dayOfMonth
                                        )
                                    )
                                }
                                closeSelf()
                            }
                        },
                        onClose = { closeSelf() }
                    )
                }
            }
        }
    }

    private fun closeSelf() {
        if (android.os.Build.VERSION.SDK_INT >= 21) finishAndRemoveTask() else finish()
        overridePendingTransition(R.anim.quick_add_fade_in, R.anim.quick_add_fade_out)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.quick_add_fade_in, R.anim.quick_add_fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeSelf()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddScreen(
    onSave: (amountCents: Long, type: String, category: String, tags: String, note: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("EXPENSE") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategories by remember { mutableStateOf(setOf(Categories.expense(context).first())) }
    var tags by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val categories =
        if (type == "EXPENSE") Categories.expense(context) else Categories.income(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速记账") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "关闭")
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("支出", type == "EXPENSE", Color(0xFFD32F2F)) {
                    type = "EXPENSE"
                    selectedCategories = setOf(Categories.expense(context).first())
                }
                TypeChip("收入", type == "INCOME", Color(0xFF388E3C)) {
                    type = "INCOME"
                    selectedCategories = setOf(Categories.income(context).first())
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("金额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Text("大类（可多选）", style = MaterialTheme.typography.titleSmall)
            categories.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { cat ->
                        FilterChip(
                            selected = cat in selectedCategories,
                            onClick = {
                                selectedCategories = if (cat in selectedCategories) {
                                    if (selectedCategories.size == 1) selectedCategories else selectedCategories - cat
                                } else {
                                    selectedCategories + cat
                                }
                            },
                            label = { Text(cat) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("标签（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val amountCents = Money.parseToCents(amountText)
                    if (amountCents != null && amountCents > 0L) {
                        onSave(amountCents, type, TransactionCategories.encode(selectedCategories), tags.trim(), note.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = Money.parseToCents(amountText)?.let { it > 0L } == true
            ) {
                Text("保存并关闭")
            }
        }
    }
}
