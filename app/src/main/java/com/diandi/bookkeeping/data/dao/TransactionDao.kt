package com.diandi.bookkeeping.data.dao

import androidx.room.*
import com.diandi.bookkeeping.data.entity.Budget
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
        SELECT
            (SELECT COALESCE(SUM(amount), 0) FROM fund_corrections) +
            (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME') -
            (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE')
    """)
    suspend fun getCurrentLedgerBalance(): Long

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
              OR printf('%.2f', amount / 100.0) LIKE '%' || :tagQuery || '%'
              OR printf('%.2f', amount / 100.0) LIKE '%' || replace(replace(:tagQuery, '¥', ''), '￥', '') || '%'
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

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE category = :category
           OR category LIKE :category || ' + %'
           OR category LIKE '% + ' || :category || ' + %'
           OR category LIKE '% + ' || :category
    """)
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
    fun getTotalIncome(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Long>

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense
        FROM transactions
        WHERE (:startDate IS NULL OR printf('%04d-%02d-%02d', year, month, day) >= :startDate)
          AND (:endDate IS NULL OR printf('%04d-%02d-%02d', year, month, day) <= :endDate)
    """)
    fun getTransactionTotals(startDate: String?, endDate: String?): Flow<TransactionTotals>

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

    @Query("""
        SELECT year, month, day,
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as expense,
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as income
        FROM transactions
        WHERE printf('%04d-%02d-%02d', year, month, day) >= :startDate
          AND printf('%04d-%02d-%02d', year, month, day) <= :endDate
        GROUP BY year, month, day
        ORDER BY year, month, day
    """)
    fun getDailyStatsInRange(startDate: String, endDate: String): Flow<List<DailyStat>>

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


    // ========== 预算 ==========
    @Query("SELECT * FROM budgets ORDER BY scope DESC, createdAt ASC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getBudgetsForBackup(): List<Budget>

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(items: List<Budget>)

    @Insert
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    /** 指定月份的总支出（分） */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = 'EXPENSE' AND year = :year AND month = :month
    """)
    fun getMonthExpense(year: Int, month: Int): Flow<Long>

    /** 指定月份按大类汇总的支出（分）；组合类目（「A + B」）在仓库层拆分 */
    @Query("""
        SELECT category, SUM(amount) as total FROM transactions
        WHERE type = 'EXPENSE' AND year = :year AND month = :month
        GROUP BY category
    """)
    fun getMonthExpenseByCategory(year: Int, month: Int): Flow<List<CategoryStat>>

    /** 指定日期区间内的支出合计（分）——资金周期可能是跨月的 */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE type = 'EXPENSE'
          AND printf('%04d-%02d-%02d', year, month, day) >= :startDate
          AND printf('%04d-%02d-%02d', year, month, day) <= :endDate
    """)
    fun getExpenseInRange(startDate: String, endDate: String): Flow<Long>

    /** 指定日期区间内按大类汇总的支出（分） */
    @Query("""
        SELECT category, SUM(amount) as total FROM transactions
        WHERE type = 'EXPENSE'
          AND printf('%04d-%02d-%02d', year, month, day) >= :startDate
          AND printf('%04d-%02d-%02d', year, month, day) <= :endDate
        GROUP BY category
    """)
    fun getExpenseByCategoryInRange(startDate: String, endDate: String): Flow<List<CategoryStat>>

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
    fun getTotalCorrection(): Flow<Long>

    @Insert
    suspend fun insertCorrection(correction: FundCorrection): Long

    @Update
    suspend fun updateCorrection(correction: FundCorrection)

    @Delete
    suspend fun deleteCorrection(correction: FundCorrection)
}

data class MonthlyStat(
    val year: Int,
    val month: Int,
    /** 分 */
    val expense: Long,
    /** 分 */
    val income: Long
)

data class DailyStat(
    val year: Int,
    val month: Int,
    val day: Int,
    /** 分 */
    val expense: Long,
    /** 分 */
    val income: Long
)

data class CategoryStat(
    val category: String,
    /** 分 */
    val total: Long
)

data class TransactionTotals(
    /** 分 */
    val income: Long,
    /** 分 */
    val expense: Long
)
