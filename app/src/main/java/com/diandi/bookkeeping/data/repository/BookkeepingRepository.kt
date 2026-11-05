package com.diandi.bookkeeping.data.repository

import android.content.Context
import com.diandi.bookkeeping.data.dao.CategoryStat
import com.diandi.bookkeeping.data.dao.TransactionDao
import com.diandi.bookkeeping.data.entity.Budget
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.service.AiAnalysisScheduler
import com.diandi.bookkeeping.util.TransactionCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookkeepingRepository(private val dao: TransactionDao, private val context: Context) {

    fun getAllTransactions() = dao.getAllTransactions()

    fun getFiltered(
        startDate: String? = null,
        endDate: String? = null,
        type: String? = null,
        tagQuery: String = "",
        categories: List<String> = listOf(""),
        categoriesEmpty: Int = 1
    ) = dao.getFiltered(startDate, endDate, type, tagQuery, listOf(""), 1).map { transactions ->
        if (categoriesEmpty == 1 || categories.isEmpty()) {
            transactions
        } else {
            val selected = categories.toSet()
            transactions.filter { transaction ->
                TransactionCategories.decode(transaction.category).any { it in selected }
            }
        }
    }

    suspend fun countByCategory(category: String) = dao.countByCategory(category)
    suspend fun getTransaction(id: Long) = dao.getById(id)
    suspend fun addTransaction(tx: Transaction): Long {
        val id = dao.insert(tx)
        if (id > 0L) {
            withContext(Dispatchers.IO) {
                AiAnalysisScheduler.onBillInserted(context.applicationContext)
            }
        }
        return id
    }
    suspend fun updateTransaction(tx: Transaction) = dao.update(tx)
    suspend fun deleteTransaction(tx: Transaction) = dao.delete(tx)

    fun getTotalIncome() = dao.getTotalIncome()
    fun getTotalExpense() = dao.getTotalExpense()
    fun getTransactionTotals(startDate: String?, endDate: String?) =
        dao.getTransactionTotals(startDate, endDate)
    fun getTotalCorrection() = dao.getTotalCorrection()

    /** 当前余额（分）= 资金修正总和 + 收入 - 支出 */
    fun getBalance(): Flow<Long> = combine(
        getTotalCorrection(),
        getTotalIncome(),
        getTotalExpense()
    ) { correction, income, expense ->
        correction + income - expense
    }

    fun getMonthlyStats() = dao.getMonthlyStats()
    fun getDailyStats(year: Int, month: Int) = dao.getDailyStats(year, month)
    fun getDailyStatsInRange(startDate: String, endDate: String) =
        dao.getDailyStatsInRange(startDate, endDate)

    fun getCategoryDistribution(type: String, year: Int?, month: Int?, day: Int?) =
        dao.getCategoryDistribution(type, year, month, day).map { rows ->
            splitCombinedCategories(rows)
                .map { (category, total) -> CategoryStat(category, total) }
                .sortedByDescending { it.total }
        }

    fun getAllGoals() = dao.getAllGoals()
    suspend fun addGoal(goal: SavingsGoal) = dao.insertGoal(goal)
    suspend fun updateGoal(goal: SavingsGoal) = dao.updateGoal(goal)
    suspend fun deleteGoal(goal: SavingsGoal) = dao.deleteGoal(goal)

    fun getAllCorrections() = dao.getAllCorrections()
    suspend fun addCorrection(c: FundCorrection) = dao.insertCorrection(c)
    suspend fun updateCorrection(c: FundCorrection) = dao.updateCorrection(c)
    suspend fun deleteCorrection(c: FundCorrection) = dao.deleteCorrection(c)

    // ========== 预算 ==========

    fun getAllBudgets() = dao.getAllBudgets()
    suspend fun addBudget(budget: Budget) = dao.insertBudget(budget)
    suspend fun updateBudget(budget: Budget) = dao.updateBudget(budget)
    suspend fun deleteBudget(budget: Budget) = dao.deleteBudget(budget)

    /** 指定月份的支出合计（分） */
    fun getMonthExpense(year: Int, month: Int) = dao.getMonthExpense(year, month)

    /** 指定月份各大类的支出（分），组合类目已拆分 */
    fun getMonthExpenseByCategory(year: Int, month: Int): Flow<Map<String, Long>> =
        dao.getMonthExpenseByCategory(year, month).map { rows -> splitCombinedCategories(rows) }

    /** 资金周期（可能跨月）内的支出合计（分） */
    fun getExpenseInRange(startDate: String, endDate: String): Flow<Long> =
        dao.getExpenseInRange(startDate, endDate)

    /** 资金周期内按大类汇总的支出（分），组合类目已拆分 */
    fun getExpenseByCategoryInRange(startDate: String, endDate: String): Flow<Map<String, Long>> =
        dao.getExpenseByCategoryInRange(startDate, endDate).map { rows -> splitCombinedCategories(rows) }

    suspend fun addBudgets(items: List<Budget>) {
        if (items.isEmpty()) return
        dao.insertBudgets(items)
    }

    /**
     * 把「餐饮 + 交通」这类组合类目的金额拆到单个类目上。
     * 除不尽的分按顺序补给前几个类目，保证拆完之后总额与原金额完全一致。
     */
    private fun splitCombinedCategories(rows: List<CategoryStat>): Map<String, Long> {
        val merged = LinkedHashMap<String, Long>()
        rows.forEach { row ->
            val categories = TransactionCategories.decode(row.category).ifEmpty { listOf(row.category) }
            if (categories.isEmpty()) return@forEach
            val base = row.total / categories.size
            var remainder = row.total % categories.size
            categories.forEach { category ->
                val extra = if (remainder > 0L) {
                    remainder -= 1L
                    1L
                } else {
                    0L
                }
                merged[category] = (merged[category] ?: 0L) + base + extra
            }
        }
        return merged.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }
}
