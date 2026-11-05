package com.diandi.bookkeeping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diandi.bookkeeping.util.Money

/** 金额统一以「分」传入，展示为 ¥12.34（不带 CNY 前缀，保持与旧版观感一致） */
fun formatMoney(cents: Long): String = "¥" + Money.format(cents)

@Composable
fun BalanceCard(
    balanceCents: Long,
    incomeCents: Long,
    expenseCents: Long,
    summaryPeriodLabel: String = "",
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accentColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("当前余额", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoney(balanceCents),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (summaryPeriodLabel.isBlank()) "收入" else "${summaryPeriodLabel}收入",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        formatMoney(incomeCents),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (summaryPeriodLabel.isBlank()) "支出" else "${summaryPeriodLabel}支出",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Text(
                        formatMoney(expenseCents),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TypeChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        )
    )
}
