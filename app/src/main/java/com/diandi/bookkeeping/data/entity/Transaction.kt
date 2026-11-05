package com.diandi.bookkeeping.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 账单实体
 * type: EXPENSE / INCOME
 * tags: 逗号分隔的自定义标签，支持模糊搜索
 *
 * 金额统一以「分」为单位的 Long 存储（列名沿用旧库的 amount，类型已是 INTEGER），
 * 展示与输入换算见 [com.diandi.bookkeeping.util.Money]。
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 金额（分，正数） */
    @ColumnInfo(name = "amount")
    val amountCents: Long,
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
 *
 * 金额字段同样以「分」存储；allocationWeight 是分配权重，不是金额，保持 Double。
 */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "targetAmount")
    val targetAmountCents: Long,
    @ColumnInfo(name = "currentAmount")
    val currentAmountCents: Long = 0L,
    val deadline: String = "",          // yyyy-MM-dd 或空
    @ColumnInfo(name = "allocationWeight")
    val allocationWeight: Double = 1.0,
    @ColumnInfo(name = "lastManualDeposit")
    val lastManualDepositCents: Long = 0L,
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
    /** 金额（分），可为正负，+80000 表示初始设为 800.00 元 */
    @ColumnInfo(name = "amount")
    val amountCents: Long,
    @ColumnInfo(name = "source")
    val source: String = "手动",
    val note: String = "",
    val year: Int,
    val month: Int,
    val day: Int,
    val createdAt: Long = System.currentTimeMillis()
)
