package com.diandi.bookkeeping.data.dao

import androidx.room.*
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // ========== 账单 ==========
    @Query("SELECT * FROM transactions ORDER BY year DESC, month DESC, day DESC, createdAt DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getTransactionsForBackup(): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<Transaction>)

    @Query("""
        SELECT * FROM transactions
        WHERE printf('%04d-%02d-%02d', year, month, day) >= :startDate
          AND printf('%04d-%02d-%02d', year, month, day) <= :endDate
        ORDER BY year DESC, month DESC, day DESC, createdAt DESC
        LIMIT :limit
    """)
    suspend fun getTransactionsForAiAnalysis(
        startDate: String,
        endDate: String,
        limit: Int
    ): List<Transaction>

    @Query("""
        SELECT * FROM transactions
        WHERE (:startDate IS NULL OR printf('%04d-%02d-%02d', year, month, day) >= :startDate)
          AND (:endDate IS NULL OR printf('%04d-%02d-%02d', year, month, day) <= :endDate)
          AND (:type IS NULL OR type = :type)
          AND (:categoriesEmpty = 1 OR category IN (:categories))
          AND (
              :tagQuery = ''
              OR tags LIKE '%' || :tagQuery || '%'
              OR category LIKE '%' || :tagQuery || '%'
              OR note LIKE '%' || :tagQuery || '%'
              OR CAST(amount AS TEXT) LIKE '%' || :tagQuery || '%'
              OR printf('%.2f', amount) LIKE '%' || :tagQuery || '%'
              OR printf('%.2f', amount) LIKE '%' || replace(replace(:tagQuery, '¥', ''), '￥', '') || '%'
          )
        ORDER BY year DESC, month DESC, day DESC, createdAt DESC
    """)
    fun getFiltered(
        startDate: String?,
        endDate: String?,
        type: String?,
        tagQuery: String,
        categories: List<String>,
        categoriesEmpty: Int
    ): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Transaction?

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Double>

    // 按年月分组统计（折线图用）
    @Query("""
        SELECT year, month,
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as expense,
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as income
        FROM transactions
        GROUP BY year, month
        ORDER BY year, month
    """)
    fun getMonthlyStats(): Flow<List<MonthlyStat>>

    @Query("""
        SELECT year, month, day,
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as expense,
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as income
        FROM transactions
        WHERE year = :year AND month = :month
        GROUP BY year, month, day
        ORDER BY day
    """)
    fun getDailyStats(year: Int, month: Int): Flow<List<DailyStat>>

    // 大类分布
    @Query("""
        SELECT category, SUM(amount) as total
        FROM transactions
        WHERE type = :type
          AND (:year IS NULL OR year = :year)
          AND (:month IS NULL OR month = :month)
          AND (:day IS NULL OR day = :day)
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getCategoryDistribution(
        type: String,
        year: Int?,
        month: Int?,
        day: Int?
    ): Flow<List<CategoryStat>>

    // ========== 攒钱目标 ==========
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    suspend fun getGoalsForBackup(): List<SavingsGoal>

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(items: List<SavingsGoal>)

    @Insert
    suspend fun insertGoal(goal: SavingsGoal): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoal)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoal)

    // ========== 资金修正 ==========
    @Query("SELECT * FROM fund_corrections ORDER BY createdAt DESC")
    fun getAllCorrections(): Flow<List<FundCorrection>>

    @Query("SELECT * FROM fund_corrections ORDER BY id ASC")
    suspend fun getCorrectionsForBackup(): List<FundCorrection>

    @Query("DELETE FROM fund_corrections")
    suspend fun deleteAllCorrections()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrections(items: List<FundCorrection>)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM fund_corrections")
    fun getTotalCorrection(): Flow<Double>

    @Insert
    suspend fun insertCorrection(correction: FundCorrection): Long

    @Delete
    suspend fun deleteCorrection(correction: FundCorrection)
}

data class MonthlyStat(
    val year: Int,
    val month: Int,
    val expense: Double,
    val income: Double
)

data class DailyStat(
    val year: Int,
    val month: Int,
    val day: Int,
    val expense: Double,
    val income: Double
)

data class CategoryStat(
    val category: String,
    val total: Double
)
