package com.diandi.bookkeeping.util

/** 兼容旧版单大类字符串，并在多大类账单中保存可读的连接格式。 */
object TransactionCategories {
    private const val SEPARATOR = " + "

    fun decode(value: String): List<String> = value
        .split(Regex("\\s*\\+\\s*"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun encode(categories: Collection<String>): String = categories
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(SEPARATOR)
}
