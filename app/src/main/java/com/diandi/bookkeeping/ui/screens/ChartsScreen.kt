package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val PIE_COLORS = listOf(
    Color(0xFF5C6BC0),
    Color(0xFF26A69A),
    Color(0xFFEF5350),
    Color(0xFFFFA726),
    Color(0xFFAB47BC),
    Color(0xFF42A5F5),
    Color(0xFF66BB6A),
    Color(0xFFEC407A),
    Color(0xFF8D6E63),
    Color(0xFF78909C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var period by remember { mutableStateOf("month") }
    var chartType by remember { mutableStateOf("line") }
    var selectedType by remember { mutableStateOf("EXPENSE") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val monthlyStats by viewModel.monthlyStats.collectAsState()
    val dailyStats by viewModel.getDailyStats(selectedDate.year, selectedDate.monthValue)
        .collectAsState(initial = emptyList())
    val categoryFlow = viewModel.getCategoryDistribution(
        selectedType,
        if (period != "all") selectedDate.year else null,
        if (period == "month" || period == "day") selectedDate.monthValue else null,
        if (period == "day") selectedDate.dayOfMonth else null
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("基准日期：$selectedDate")
                }
                TextButton(
                    onClick = { selectedDate = LocalDate.now() },
                    enabled = selectedDate != LocalDate.now()
                ) {
                    Text("还原本日")
                }
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
                    val linePoints = when (period) {
                        "year" -> monthlyStats
                            .groupBy { it.year }
                            .toSortedMap()
                            .map { (year, stats) -> year.toString() to stats.sumOf { it.expense }.toFloat() }
                        "day" -> dailyStats.map {
                            "${it.month}-${it.day}" to it.expense.toFloat()
                        }
                        else -> monthlyStats.map {
                            "${it.year}-${it.month}" to it.expense.toFloat()
                        }
                    }
                    Text(
                        "支出趋势（${when (period) { "year" -> "按年"; "day" -> "按日"; else -> "按月" }}汇总）",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (linePoints.isEmpty()) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val labels = linePoints.map { it.first }
                        val expenses = linePoints.map { it.second }
                        val maxE = (expenses.maxOrNull() ?: 1f).coerceAtLeast(1f)
                        val lineColor = MaterialTheme.colorScheme.primary
                        val labelColor = MaterialTheme.colorScheme.onSurface
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val topPad = 28f
                            val bottomPad = 36f
                            val chartH = h - topPad - bottomPad
                            val n = expenses.size
                            val points = expenses.mapIndexed { i, v ->
                                val x = if (n == 1) w / 2f else i * w / (n - 1)
                                val y = topPad + chartH - (v / maxE) * chartH * 0.9f
                                Offset(x, y) to v
                            }
                            if (n >= 2) {
                                val path = Path()
                                points.forEachIndexed { i, (pt, _) ->
                                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                                }
                                drawPath(path, lineColor, style = Stroke(width = 4f))
                            }
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(
                                    (labelColor.alpha * 255).toInt(),
                                    (labelColor.red * 255).toInt(),
                                    (labelColor.green * 255).toInt(),
                                    (labelColor.blue * 255).toInt()
                                )
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            points.forEachIndexed { i, (pt, v) ->
                                drawCircle(lineColor, radius = 7f, center = pt)
                                drawCircle(Color.White, radius = 3.5f, center = pt)
                                // 数值标注
                                textPaint.textSize = 28f
                                drawContext.canvas.nativeCanvas.drawText(
                                    formatShort(v),
                                    pt.x,
                                    (pt.y - 14f).coerceAtLeast(22f),
                                    textPaint
                                )
                                // 月份标签
                                textPaint.textSize = 24f
                                drawContext.canvas.nativeCanvas.drawText(
                                    labels.getOrElse(i) { "" }.takeLast(5),
                                    pt.x,
                                    h - 8f,
                                    textPaint
                                )
                            }
                        }
                        Text(
                            "最近: " + linePoints.takeLast(6).joinToString(" | ") {
                                "${it.first}: ¥${"%.0f".format(it.second)}"
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
                        val total = categoryStats.sumOf { it.total }.coerceAtLeast(0.0001)
                        // 扇形图
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(8.dp)
                        ) {
                            val diameter = min(size.width, size.height) * 0.9f
                            val left = (size.width - diameter) / 2f
                            val top = (size.height - diameter) / 2f
                            var startAngle = -90f
                            categoryStats.forEachIndexed { i, stat ->
                                val sweep = (stat.total / total * 360.0).toFloat()
                                drawArc(
                                    color = PIE_COLORS[i % PIE_COLORS.size],
                                    startAngle = startAngle,
                                    sweepAngle = sweep.coerceAtLeast(0.5f),
                                    useCenter = true,
                                    topLeft = Offset(left, top),
                                    size = Size(diameter, diameter)
                                )
                                // 扇区中部写百分比（足够大才写）
                                if (sweep >= 18f) {
                                    val mid = Math.toRadians((startAngle + sweep / 2).toDouble())
                                    val cx = left + diameter / 2 + cos(mid).toFloat() * diameter * 0.32f
                                    val cy = top + diameter / 2 + sin(mid).toFloat() * diameter * 0.32f
                                    val pct = stat.total / total * 100
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        textSize = 30f
                                        isAntiAlias = true
                                        isFakeBoldText = true
                                    }
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "${"%.0f".format(pct)}%",
                                        cx,
                                        cy + 10f,
                                        paint
                                    )
                                }
                                startAngle += sweep
                            }
                        }
                        // 图例 + 进度条
                        categoryStats.forEachIndexed { i, stat ->
                            val percent = if (total > 0) (stat.total / total * 100) else 0.0
                            val color = PIE_COLORS[i % PIE_COLORS.size]
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Canvas(Modifier.size(12.dp)) {
                                            drawCircle(color)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(stat.category)
                                    }
                                    Text("¥${"%.2f".format(stat.total)}  (${"%.1f".format(percent)}%)")
                                }
                                LinearProgressIndicator(
                                    progress = { (percent / 100).toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = color
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
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
            DatePicker(state = pickerState)
        }
    }
}

private fun formatShort(v: Float): String {
    return when {
        v >= 10000 -> "${"%.1f".format(v / 10000)}万"
        v >= 1000 -> "${"%.1f".format(v / 1000)}k"
        else -> "%.0f".format(v)
    }
}
