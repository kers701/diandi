package com.diandi.bookkeeping.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 预算：每月总预算（scope=TOTAL）或按大类预算（scope=CATEGORY）。
 * 金额以「分」存储；category 支持多选组合名（如「餐饮 + 交通」）。
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scope: String = SCOPE_TOTAL,
    val category: String = "",
    @ColumnInfo(name = "monthlyAmount")
    val monthlyAmountCents: Long,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_TOTAL = "TOTAL"
        const val SCOPE_CATEGORY = "CATEGORY"
    }
}
