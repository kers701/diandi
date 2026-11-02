package com.diandi.bookkeeping.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    val balance: StateFlow<Double> = repo.getBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIncome: StateFlow<Double> = repo.getTotalIncome()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = repo.getTotalExpense()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
        amount: Double,
        type: String,
        category: String,
        tags: String,
        note: String,
        date: LocalDate = LocalDate.now()
    ) {
        viewModelScope.launch {
            repo.addTransaction(
                Transaction(
                    amount = amount,
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
        amount: Double,
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
                    amount = amount,
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
        target: Double,
        allocationWeight: Double = 1.0,
        deadline: String = ""
    ) {
        viewModelScope.launch {
            repo.addGoal(
                SavingsGoal(
                    title = title,
                    targetAmount = target,
                    allocationWeight = allocationWeight,
                    deadline = deadline
                )
            )
        }
    }

    fun updateGoalDetails(
        goal: SavingsGoal,
        title: String,
        targetAmount: Double,
        allocationWeight: Double
    ) {
        if (title.isBlank() || !targetAmount.isFinite() || targetAmount <= 0.0 ||
            !allocationWeight.isFinite() || allocationWeight !in 0.01..1000.0
        ) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    title = title.trim(),
                    targetAmount = targetAmount,
                    allocationWeight = allocationWeight
                )
            )
        }
    }

    fun updateGoalProgress(goal: SavingsGoal, addAmount: Double) {
        if (!addAmount.isFinite() || addAmount <= 0.0) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    currentAmount = goal.currentAmount + addAmount,
                    lastManualDeposit = addAmount
                )
            )
        }
    }

    fun undoLastGoalDeposit(goal: SavingsGoal) {
        val undoAmount = minOf(goal.lastManualDeposit.coerceAtLeast(0.0), goal.currentAmount.coerceAtLeast(0.0))
        if (undoAmount <= 0.0) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    currentAmount = (goal.currentAmount - undoAmount).coerceAtLeast(0.0),
                    lastManualDeposit = 0.0
                )
            )
        }
    }

    fun reduceGoalProgress(goal: SavingsGoal, amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return
        viewModelScope.launch {
            repo.updateGoal(
                goal.copy(
                    currentAmount = (goal.currentAmount - amount).coerceAtLeast(0.0),
                    lastManualDeposit = 0.0
                )
            )
        }
    }

    fun resetGoalProgress(goal: SavingsGoal) {
        viewModelScope.launch {
            repo.updateGoal(goal.copy(currentAmount = 0.0, lastManualDeposit = 0.0))
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch { repo.deleteGoal(goal) }
    }

    fun addCorrection(
        amount: Double,
        note: String = "",
        date: LocalDate = LocalDate.now(),
        source: String = "手动"
    ) {
        viewModelScope.launch {
            repo.addCorrection(
                FundCorrection(
                    amount = amount,
                    source = source.trim().ifBlank { "手动" },
                    note = note,
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth
                )
            )
        }
    }

    fun updateCorrection(correction: FundCorrection, amount: Double, source: String, note: String) {
        if (!amount.isFinite()) return
        viewModelScope.launch {
            repo.updateCorrection(
                correction.copy(
                    amount = amount,
                    source = source.trim().ifBlank { "手动" },
                    note = note
                )
            )
        }
    }

    fun deleteCorrection(c: FundCorrection) {
        viewModelScope.launch { repo.deleteCorrection(c) }
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
