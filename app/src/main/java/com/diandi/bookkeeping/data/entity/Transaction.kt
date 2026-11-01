package com.diandi.bookkeeping.data.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 账单实体
 * type: EXPENSE / INCOME
 * tags: 逗号分隔的自定义标签，支持模糊搜索
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,                 // 金额（正数）
    val type: String,                   // "EXPENSE" 或 "INCOME"
    val category: String,               // 大类：烟酒、娱乐、餐饮、工资等
    val tags: String = "",              // 自定义标签，逗号分隔
    val note: String = "",
    val year: Int,
    val month: Int,
    val day: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toLocalDate(): LocalDate = LocalDate.of(year, month, day)
}

/**
 * 攒钱目标
 */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: String = "",          // yyyy-MM-dd 或空
    @ColumnInfo(defaultValue = "1.0")
    val allocationWeight: Double = 1.0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 资金修正记录（初始余额调整）
 * 应用初始余额为 0，通过修正来设置起始金额
 */
@Entity(tableName = "fund_corrections")
data class FundCorrection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,                 // 可为正负，+800 表示初始设为 800
    val note: String = "",
    val year: Int,
    val month: Int,
    val day: Int,
    val createdAt: Long = System.currentTimeMillis()
)
