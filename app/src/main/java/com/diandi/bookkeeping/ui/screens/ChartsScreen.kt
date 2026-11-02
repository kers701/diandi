package com.diandi.bookkeeping.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diandi.bookkeeping.ui.components.TypeChip
import com.diandi.bookkeeping.viewmodel.MainViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
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

    val lineStartDate = when (period) {
        "year" -> LocalDate.of(selectedDate.year, 1, 1)
        "month" -> selectedDate.withDayOfMonth(1)
        else -> selectedDate.minusDays(2)
    }
    val lineEndDate = selectedDate
    var selectedPointDate by remember(lineStartDate, lineEndDate) {
        mutableStateOf<LocalDate?>(null)
    }
    val coroutineScope = rememberCoroutineScope()
    val dailyRangeFlow = remember(viewModel, lineStartDate, lineEndDate) {
        viewModel.getDailyStatsInRange(lineStartDate.toString(), lineEndDate.toString())
    }
    val dailyRangeStats by dailyRangeFlow.collectAsState(initial = emptyList())
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
                    val statsByDate = dailyRangeStats.associateBy {
                        LocalDate.of(it.year, it.month, it.day)
                    }
                    val chartDates = generateSequence(lineStartDate) { it.plusDays(1) }
                        .takeWhile { !it.isAfter(lineEndDate) }
                        .toList()
                    val linePoints = chartDates.map { date ->
                        date to (statsByDate[date]?.expense ?: 0.0).toFloat()
                    }
                    val periodDescription = when (period) {
                        "year" -> "${selectedDate.year}年每日"
                        "month" -> "${selectedDate.year}年${selectedDate.monthValue}月每日"
                        else -> "$lineStartDate 至 $lineEndDate 每日"
                    }
                    Text(
                        "支出趋势（$periodDescription）",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (dailyRangeStats.none { it.expense > 0.0 }) {
                        Text("该周期暂无支出数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val expenses = linePoints.map { it.second }
                        val maxE = (expenses.maxOrNull() ?: 1f).coerceAtLeast(1f)
                        val lineColor = MaterialTheme.colorScheme.primary
                        val labelColor = MaterialTheme.colorScheme.onSurface
                        val selectedPointBubbleColor = MaterialTheme.colorScheme.surface
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val chartWidth = maxOf(
                                maxWidth,
                                24.dp * (linePoints.size - 1).toFloat() + 32.dp
                            )
                            val density = LocalDensity.current
                            val chartEndScrollPx = with(density) {
                                ((chartWidth - maxWidth).toPx().roundToInt()).coerceAtLeast(0)
                            }
                            val chartScrollState = rememberScrollState()
                            LaunchedEffect(period, selectedDate, chartEndScrollPx) {
                                withFrameNanos { }
                                chartScrollState.animateScrollTo(chartEndScrollPx)
                            }
                            val viewportWidthPx = with(density) { maxWidth.toPx() }
                            val plotWidthPx = with(density) { (chartWidth - 32.dp).toPx() }
                                .coerceAtLeast(1f)
                            val horizontalInsetPx = with(density) { 16.dp.toPx() }
                            val pointSpacingPx = if (linePoints.size > 1) {
                                plotWidthPx / linePoints.lastIndex
                            } else 1f
                            val firstVisibleIndex = if (linePoints.size > 1) {
                                ceil(((chartScrollState.value - horizontalInsetPx) / pointSpacingPx).toDouble())
                                    .toInt().coerceIn(0, linePoints.lastIndex)
                            } else 0
                            val lastVisibleIndex = if (linePoints.size > 1) {
                                floor(((chartScrollState.value + viewportWidthPx - horizontalInsetPx) / pointSpacingPx).toDouble())
                                    .toInt().coerceIn(firstVisibleIndex, linePoints.lastIndex)
                            } else 0
                            val viewportStart = linePoints[firstVisibleIndex].first
                            val viewportEnd = linePoints[lastVisibleIndex].first
                            val viewportRange = if (period == "day") {
                                "$viewportStart 至 $viewportEnd"
                            } else {
                                "${viewportStart.monthValue}-${viewportStart.dayOfMonth} 至 " +
                                    "${viewportEnd.monthValue}-${viewportEnd.dayOfMonth}"
                            }
                            val markerHitRadiusPx = with(density) { 18.dp.toPx() }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "当前视口：$viewportRange",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "基准日期：$selectedDate",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                withFrameNanos { }
                                                chartScrollState.animateScrollTo(chartEndScrollPx)
                                            }
                                        }
                                    ) { Text("回到基准日") }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(chartScrollState)) {
                                Canvas(
                                    modifier = Modifier
                                        .width(chartWidth)
                                        .height(260.dp)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .pointerInput(linePoints, selectedPointDate) {
                                            detectTapGestures { tap ->
                                                val pointCount = linePoints.size
                                                val plotWidth = size.width.toFloat()
                                                val chartHeight = size.height - 28f - 36f
                                                var closestIndex = -1
                                                var closestDistanceSquared = Float.MAX_VALUE
                                                linePoints.forEachIndexed { index, (_, amount) ->
                                                    if (amount > 0f) {
                                                        val x = if (pointCount == 1) plotWidth / 2f
                                                        else index * plotWidth / (pointCount - 1)
                                                        val y = 28f + chartHeight -
                                                            (amount / maxE) * chartHeight * 0.9f
                                                        val dx = tap.x - x
                                                        val dy = tap.y - y
                                                        val distanceSquared = dx * dx + dy * dy
                                                        if (distanceSquared < closestDistanceSquared) {
                                                            closestDistanceSquared = distanceSquared
                                                            closestIndex = index
                                                        }
                                                    }
                                                }
                                                val pointSpacing = if (pointCount > 1) {
                                                    plotWidth / (pointCount - 1)
                                                } else plotWidth
                                                val hitRadius = min(markerHitRadiusPx, pointSpacing * 0.48f)
                                                if (closestIndex >= 0 &&
                                                    closestDistanceSquared <= hitRadius * hitRadius
                                                ) {
                                                    val tappedDate = linePoints[closestIndex].first
                                                    selectedPointDate = if (selectedPointDate == tappedDate) {
                                                        null
                                                    } else tappedDate
                                                } else {
                                                    selectedPointDate = null
                                                }
                                            }
                                        }
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val topPad = 28f
                                    val bottomPad = 36f
                                    val chartH = h - topPad - bottomPad
                                    val n = expenses.size
                                    val labelInterval = if (n <= 3) 1 else 5
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
                                        val date = linePoints[i].first
                                        if (v > 0f) {
                                            val markerRadius = if (n > 100) 5f else 7f
                                            drawCircle(lineColor, radius = markerRadius, center = pt)
                                            drawCircle(Color.White, radius = markerRadius / 2f, center = pt)
                                        }
                                        if (date == selectedPointDate && v > 0f) {
                                            val amountLabel = "¥${"%.2f".format(v)}"
                                            textPaint.textSize = 30f
                                            textPaint.isFakeBoldText = true
                                            val bubbleWidth = textPaint.measureText(amountLabel) + 20f
                                            val bubbleHeight = 42f
                                            val centerX = if (w > bubbleWidth + 4f) {
                                                pt.x.coerceIn(bubbleWidth / 2f + 2f, w - bubbleWidth / 2f - 2f)
                                            } else w / 2f
                                            val bubbleTop = (pt.y - bubbleHeight - 12f).coerceAtLeast(2f)
                                            drawRoundRect(
                                                color = selectedPointBubbleColor,
                                                topLeft = Offset(centerX - bubbleWidth / 2f, bubbleTop),
                                                size = Size(bubbleWidth, bubbleHeight),
                                                cornerRadius = CornerRadius(10f, 10f)
                                            )
                                            drawRoundRect(
                                                color = lineColor,
                                                topLeft = Offset(centerX - bubbleWidth / 2f, bubbleTop),
                                                size = Size(bubbleWidth, bubbleHeight),
                                                cornerRadius = CornerRadius(10f, 10f),
                                                style = Stroke(width = 2f)
                                            )
                                            drawContext.canvas.nativeCanvas.drawText(
                                                amountLabel,
                                                centerX,
                                                bubbleTop + 29f,
                                                textPaint
                                            )
                                            textPaint.isFakeBoldText = false
                                        }
                                        if (i % labelInterval == 0 || i == n - 1 || date == selectedDate) {
                                            textPaint.textSize = 24f
                                            drawContext.canvas.nativeCanvas.drawText(
                                                "${date.monthValue}-${date.dayOfMonth}",
                                                pt.x,
                                                h - 8f,
                                                textPaint
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val recentPoints = linePoints
                            .filter { !it.first.isAfter(selectedDate) }
                            .takeLast(6)
                        Text(
                            "最近: " + recentPoints.joinToString(" | ") {
                                "${it.first.monthValue}-${it.first.dayOfMonth}: ¥${"%.0f".format(it.second)}"
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
