package com.diandi.bookkeeping.util

object Categories {
    val expenseCategories = listOf(
        "餐饮", "交通", "购物", "烟酒", "娱乐",
        "居住", "通讯", "医疗", "教育", "服饰",
        "美容", "运动", "旅行", "社交", "其他支出"
    )

    val incomeCategories = listOf(
        "工资", "奖金", "兼职", "投资理财", "红包",
        "退款", "其他收入"
    )

    fun getByType(type: String): List<String> =
        if (type == "INCOME") incomeCategories else expenseCategories
}
