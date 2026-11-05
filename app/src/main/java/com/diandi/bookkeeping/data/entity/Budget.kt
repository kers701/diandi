package com.diandi.bookkeeping.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 预算项。
 *
 * - [kind] 区分**固定开销**（房租、水电这类每月必花且金额相对固定的）与
 *   **浮动预算**（餐饮、购物这类日常浮动开销）。
 * - [scope] 区分该预算覆盖「全部支出」（TOTAL）还是指定大类（CATEGORY）。
 * - 金额统一以「分」存储。
 *
 * 核心语义（见 util/BudgetMath）：
 *   可自由分配余额 = 当前余额 − 固定开销未花预留 − 浮动预算未花预留
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** FIXED / FLOATING */
    val kind: String = KIND_FLOATING,
    val scope: String = SCOPE_CATEGORY,
    val category: String = "",
    @ColumnInfo(name = "monthlyAmount")
    val monthlyAmountCents: Long,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_FIXED = "FIXED"
        const val KIND_FLOATING = "FLOATING"

        const val SCOPE_TOTAL = "TOTAL"
        const val SCOPE_CATEGORY = "CATEGORY"
    }
}
