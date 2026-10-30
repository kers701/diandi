package com.diandi.bookkeeping.data.repository

import com.diandi.bookkeeping.data.dao.TransactionDao
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class BookkeepingRepository(private val dao: TransactionDao) {

    fun getAllTransactions() = dao.getAllTransactions()

    fun getFiltered(
        year: Int? = null,
        month: Int? = null,
        day: Int? = null,
        type: String? = null,
        category: String? = null,
        tagQuery: String = ""
    ) = dao.getFiltered(year, month, day, type, category, tagQuery)

    suspend fun addTransaction(tx: Transaction) = dao.insert(tx)
    suspend fun updateTransaction(tx: Transaction) = dao.update(tx)
    suspend fun deleteTransaction(tx: Transaction) = dao.delete(tx)

    fun getTotalIncome() = dao.getTotalIncome()
    fun getTotalExpense() = dao.getTotalExpense()
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
    fun getCategoryDistribution(type: String, year: Int?, month: Int?, day: Int?) =
        dao.getCategoryDistribution(type, year, month, day)

    fun getAllGoals() = dao.getAllGoals()
    suspend fun addGoal(goal: SavingsGoal) = dao.insertGoal(goal)
    suspend fun updateGoal(goal: SavingsGoal) = dao.updateGoal(goal)
    suspend fun deleteGoal(goal: SavingsGoal) = dao.deleteGoal(goal)

    fun getAllCorrections() = dao.getAllCorrections()
    suspend fun addCorrection(c: FundCorrection) = dao.insertCorrection(c)
    suspend fun deleteCorrection(c: FundCorrection) = dao.deleteCorrection(c)
}
