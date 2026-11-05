package com.diandi.bookkeeping.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.diandi.bookkeeping.data.entity.Budget
import com.diandi.bookkeeping.data.entity.FundCorrection
import com.diandi.bookkeeping.data.entity.SavingsGoal
import com.diandi.bookkeeping.data.entity.Transaction
import com.diandi.bookkeeping.data.repository.BookkeepingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FilterState(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val type: String? = null,       // null / EXPENSE / INCOME
    val category: String? = null,
    val tagQuery: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val categories: Set<String> = emptySet()
)

class MainViewModel(private val repo: BookkeepingRepository) : ViewModel() {

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = _filter
        .flatMapLatest { f ->
            val startDate = f.startDate ?: when {
                f.year == null -> null
                f.month == null -> LocalDate.of(f.year, 1, 1)
                f.day == null -> LocalDate.of(f.year, f.month, 1)
                else -> LocalDate.of(f.year, f.month, f.day)
            }
            val endDate = f.endDate ?: when {
                f.year == null -> null
                f.month == null -> LocalDate.of(f.year, 12, 31)
                f.day == null -> LocalDate.of(f.year, f.month, 1).plusMonths(1).minusDays(1)
                else -> LocalDate.of(f.year, f.month, f.day)
            }
            repo.getFiltered(
                startDate = startDate?.toString(),
                endDate = endDate?.toString(),
                type = f.type,
                tagQuery = f.tagQuery,
                categories = f.categories.toList().ifEmpty { listOf("") },
                categoriesEmpty = if (f.categories.isEmpty()) 1 else 0
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 余额（分） */
    val balance: StateFlow<Long> = repo.getBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 收入（分） */
    val totalIncome: StateFlow<Long> = repo.getTotalIncome()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 支出（分） */
    val totalExpense: StateFlow<Long> = repo.getTotalExpense()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 预算列表 */
    val budgets: StateFlow<List<Budget>> = repo.getAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<SavingsGoal>> = repo.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val corrections: StateFlow<List<FundCorrection>> = repo.getAllCorrections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyStats = repo.getMonthlyStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFilter(
        year: Int? = _filter.value.year,
        month: Int? = _filter.value.month,
        day: Int? = _filter.value.day,
        type: String? = _filter.value.type,
        category: String? = _filter.value.category,
        tagQuery: String = _filter.value.tagQuery,
        startDate: LocalDate? = _filter.value.startDate,
        endDate: LocalDate? = _filter.value.endDate,
        categories: Set<String> = _filter.value.categories
    ) {
        _filter.value = FilterState(
            year, month, day, type, category, tagQuery, startDate, endDate, categories
        )
    }

    fun clearFilter() {
        _filter.value = FilterState()
    }

    fun addTransaction(
        amountCents: Long,
        type: String,
        category: String,
        tags: String,
        note: String,
        date: LocalDate = LocalDate.now()
    ) {
        viewModelScope.launch {
            repo.addTransaction(
                Transaction(
                    amountCents = amountCents,
                    type = type,
                    category = category,
                    tags = tags.trim(),
                    note = note,
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth
                )
            )
        }
    }

    fun updateTransaction(
        id: Long,
        amountCents: Long,
        type: String,
        category: String,
        tags: String,
        note: String,
        date: LocalDate,
        createdAt: Long
    ) {
        viewModelScope.launch {
            repo.updateTransaction(
                Transaction(
                    id = id,
                    amountCents = amountCents,
                    type = type,
                    category = category,
                    tags = tags.trim(),
                    note = note,
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth,
                    createdAt = createdAt
                )
            )
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch { repo.deleteTransaction(tx) }
    }

    suspend fun isCategoryInUse(category: String): Boolean =
        repo.countByCategory(category) > 0

    suspend fun loadTransaction(id: Long) = repo.getTransaction(id)

    fun addGoal(
        title: String,
        targetCents: Long,
        allocationWeight: Double = 1.0,
        deadline: String = ""
    ) {
        if (title.isBlank() || targetCents <= 0L) return
        viewModelScope.launch {
            repo.addGoal(
                SavingsGoal(
                    title = title.trim(),
                    targetAmountCents = targetCents,
                    allocationWeight = allocationWeight,
                    deadline = deadline
                )
            )
        }
    }

    fun updateGoalDetails(
        goal: SavingsGoal,
        title: String,
        targetAmountCents: Long,
        allocationWeight: Double
    ) {
        if (title.isBlank() || targetAmountCents <= 0L ||
            !allocationWeight.isFinite() || allocationWeight !in 0.01..1000.0
        ) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    title = title.trim(),
                    targetAmountCents = targetAmountCents,
                    allocationWeight = allocationWeight
                )
            )
        }
    }

    fun updateGoalProgress(goal: SavingsGoal, addCents: Long) {
        if (addCents <= 0L) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    currentAmountCents = goal.currentAmountCents + addCents,
                    lastManualDepositCents = addCents
                )
            )
        }
    }

    fun undoLastGoalDeposit(goal: SavingsGoal) {
        val undo = minOf(
            goal.lastManualDepositCents.coerceAtLeast(0L),
            goal.currentAmountCents.coerceAtLeast(0L)
        )
        if (undo <= 0L) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    currentAmountCents = (goal.currentAmountCents - undo).coerceAtLeast(0L),
                    lastManualDepositCents = 0L
                )
            )
        }
    }

    fun reduceGoalProgress(goal: SavingsGoal, amountCents: Long) {
        if (amountCents <= 0L) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    currentAmountCents = (goal.currentAmountCents - amountCents).coerceAtLeast(0L),
                    lastManualDepositCents = 0L
                )
            )
        }
    }

    fun resetGoalProgress(goal: SavingsGoal) {
        viewModelScope.launch {
            repo.updateGoal(goal.copy(currentAmountCents = 0L, lastManualDepositCents = 0L))
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch { repo.deleteGoal(goal) }
    }

    fun addCorrection(
        amountCents: Long,
        note: String = "",
        date: LocalDate = LocalDate.now(),
        source: String = "手动"
    ) {
        viewModelScope.launch {
            repo.addCorrection(
                FundCorrection(
                    amountCents = amountCents,
                    source = source.trim().ifBlank { "手动" },
                    note = note,
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth
                )
            )
        }
    }

    fun updateCorrection(correction: FundCorrection, amountCents: Long, source: String, note: String) {
        viewModelScope.launch {
            repo.updateCorrection(
                correction.copy(
                    amountCents = amountCents,
                    source = source.trim().ifBlank { "手动" },
                    note = note
                )
            )
        }
    }

    fun deleteCorrection(c: FundCorrection) {
        viewModelScope.launch { repo.deleteCorrection(c) }
    }

    // ========== 预算 ==========

    fun addBudget(kind: String, scope: String, category: String, monthlyAmountCents: Long) {
        if (monthlyAmountCents <= 0L) return
        if (kind == Budget.KIND_FIXED && category.isBlank()) return
        viewModelScope.launch {
            repo.addBudget(
                Budget(
                    kind = kind,
                    scope = if (category.isBlank()) Budget.SCOPE_TOTAL else scope,
                    category = if (category.isBlank()) "" else category.trim(),
                    monthlyAmountCents = monthlyAmountCents
                )
            )
        }
    }

    fun updateBudgetFull(
        budget: Budget,
        monthlyAmountCents: Long,
        category: String,
        kind: String,
        scope: String
    ) {
        if (monthlyAmountCents <= 0L) return
        if (kind == Budget.KIND_FIXED && category.isBlank()) return
        viewModelScope.launch {
            repo.updateBudget(
                budget.copy(
                    kind = kind,
                    scope = if (category.isBlank()) Budget.SCOPE_TOTAL else scope,
                    category = if (category.isBlank()) "" else category.trim(),
                    monthlyAmountCents = monthlyAmountCents
                )
            )
        }
    }

    fun updateBudget(budget: Budget, monthlyAmountCents: Long, enabled: Boolean) {
        if (monthlyAmountCents <= 0L) return
        viewModelScope.launch {
            repo.updateBudget(budget.copy(monthlyAmountCents = monthlyAmountCents, enabled = enabled))
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { repo.deleteBudget(budget) }
    }

    fun monthExpense(year: Int, month: Int) = repo.getMonthExpense(year, month)

    fun monthExpenseByCategory(year: Int, month: Int) = repo.getMonthExpenseByCategory(year, month)

    /** 资金周期内的支出合计（分） */
    fun expenseInRange(startDate: String, endDate: String) =
        repo.getExpenseInRange(startDate, endDate)

    /** 资金周期内按大类汇总的支出（分） */
    fun expenseByCategoryInRange(startDate: String, endDate: String) =
        repo.getExpenseByCategoryInRange(startDate, endDate)

    /** 批量创建智能预算 */
    fun addBudgets(items: List<Budget>) {
        if (items.isEmpty()) return
        viewModelScope.launch { repo.addBudgets(items) }
    }

    fun getCategoryDistribution(type: String, year: Int?, month: Int?, day: Int?) =
        repo.getCategoryDistribution(type, year, month, day)

    fun getDailyStats(year: Int, month: Int) = repo.getDailyStats(year, month)
    fun getDailyStatsInRange(startDate: String, endDate: String) =
        repo.getDailyStatsInRange(startDate, endDate)
    fun getTransactionTotals(startDate: String?, endDate: String?) =
        repo.getTransactionTotals(startDate, endDate)
}

class MainViewModelFactory(private val repo: BookkeepingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repo) as T
    }
}
