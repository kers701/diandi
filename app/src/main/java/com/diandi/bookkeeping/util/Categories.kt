package com.diandi.bookkeeping.util

import android.content.Context

object Categories {
    val defaultExpense = listOf(
        "餐饮", "交通", "购物", "烟酒", "娱乐",
        "居住", "通讯", "医疗", "教育", "服饰",
        "美容", "运动", "旅行", "社交", "其他支出"
    )

    val defaultIncome = listOf(
        "工资", "奖金", "兼职", "投资理财", "红包",
        "退款", "其他收入"
    )

    /** @deprecated 使用 expense(context)；无 Context 时回退预设 */
    val expenseCategories: List<String> get() = defaultExpense

    /** @deprecated 使用 income(context) */
    val incomeCategories: List<String> get() = defaultIncome

    fun expense(context: Context): List<String> =
        AppPrefs.getExpenseCategories(context)

    fun income(context: Context): List<String> =
        AppPrefs.getIncomeCategories(context)

    fun getByType(context: Context, type: String): List<String> =
        if (type == "INCOME") income(context) else expense(context)

    fun getByType(type: String): List<String> =
        if (type == "INCOME") defaultIncome else defaultExpense
}
