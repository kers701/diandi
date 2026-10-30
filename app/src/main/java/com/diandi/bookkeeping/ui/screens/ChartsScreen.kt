package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var period by remember { mutableStateOf("month") }
    var chartType by remember { mutableStateOf("line") }
    var selectedType by remember { mutableStateOf("EXPENSE") }
    val now = LocalDate.now()

    val monthlyStats by viewModel.monthlyStats.collectAsState()
    val categoryFlow = viewModel.getCategoryDistribution(
        selectedType,
        if (period != "all") now.year else null,
        if (period == "month" || period == "day") now.monthValue else null,
        if (period == "day") now.dayOfMonth else null
    )
    val categoryStats by categoryFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计图表") },
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
            Text("统计维度", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("按年", period == "year", MaterialTheme.colorScheme.primary) { period = "year" }
                TypeChip("按月", period == "month", MaterialTheme.colorScheme.primary) { period = "month" }
                TypeChip("按日", period == "day", MaterialTheme.colorScheme.primary) { period = "day" }
            }

            Text("图表类型", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("消费折线", chartType == "line", MaterialTheme.colorScheme.secondary) {
                    chartType = "line"
                }
                TypeChip("大类分布", chartType == "pie", MaterialTheme.colorScheme.secondary) {
                    chartType = "pie"
                }
            }

            if (chartType == "pie") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip("支出", selectedType == "EXPENSE", Color(0xFFD32F2F)) {
                        selectedType = "EXPENSE"
                    }
                    TypeChip("收入", selectedType == "INCOME", Color(0xFF388E3C)) {
                        selectedType = "INCOME"
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when (chartType) {
                "line" -> {
                    Text("支出趋势（按月汇总）", style = MaterialTheme.typography.titleMedium)
                    if (monthlyStats.isEmpty()) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val expenses = monthlyStats.map { it.expense.toFloat() }
                        val maxE = (expenses.maxOrNull() ?: 1f).coerceAtLeast(1f)
                        val lineColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val n = expenses.size
                            if (n == 1) {
                                val y = h - (expenses[0] / maxE) * h * 0.85f
                                drawCircle(lineColor, radius = 6f, center = Offset(w / 2, y))
                            } else if (n > 1) {
                                val path = Path()
                                expenses.forEachIndexed { i, v ->
                                    val x = i * w / (n - 1)
                                    val y = h - (v / maxE) * h * 0.85f
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, lineColor, style = Stroke(width = 4f))
                                expenses.forEachIndexed { i, v ->
                                    val x = i * w / (n - 1)
                                    val y = h - (v / maxE) * h * 0.85f
                                    drawCircle(lineColor, radius = 5f, center = Offset(x, y))
                                }
                            }
                        }
                        Text(
                            "最近月份: " + monthlyStats.takeLast(6).joinToString(" | ") {
                                "${it.year}-${it.month}: ¥${"%.0f".format(it.expense)}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                "pie" -> {
                    Text(
                        "${if (selectedType == "EXPENSE") "支出" else "收入"}大类分布",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (categoryStats.isEmpty()) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val total = categoryStats.sumOf { it.total }
                        categoryStats.forEach { stat ->
                            val percent = if (total > 0) (stat.total / total * 100) else 0.0
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stat.category)
                                    Text("¥${"%.2f".format(stat.total)}  (${"%.1f".format(percent)}%)")
                                }
                                LinearProgressIndicator(
                                    progress = { (percent / 100).toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
