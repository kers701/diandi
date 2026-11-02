package com.diandi.bookkeeping.data.repository

import android.content.Context
import com.diandi.bookkeeping.data.dao.TransactionDao
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.service.AiAnalysisScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    ) = dao.getFiltered(startDate, endDate, type, tagQuery, categories, categoriesEmpty)

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

    /** 当前余额 = 资金修正总和 + 收入 - 支出 */
    fun getBalance(): Flow<Double> = combine(
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
        dao.getCategoryDistribution(type, year, month, day)

    fun getAllGoals() = dao.getAllGoals()
    suspend fun addGoal(goal: SavingsGoal) = dao.insertGoal(goal)
    suspend fun updateGoal(goal: SavingsGoal) = dao.updateGoal(goal)
    suspend fun deleteGoal(goal: SavingsGoal) = dao.deleteGoal(goal)

    fun getAllCorrections() = dao.getAllCorrections()
    suspend fun addCorrection(c: FundCorrection) = dao.insertCorrection(c)
    suspend fun updateCorrection(c: FundCorrection) = dao.updateCorrection(c)
    suspend fun deleteCorrection(c: FundCorrection) = dao.deleteCorrection(c)
}
