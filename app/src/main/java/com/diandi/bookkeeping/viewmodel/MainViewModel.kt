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
    val tagQuery: String = ""
)

class MainViewModel(private val repo: BookkeepingRepository) : ViewModel() {

    private val _filter = MutableStateFlow(FilterState())
    val filter: StateFlow<FilterState> = _filter.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = _filter
        .flatMapLatest { f ->
            repo.getFiltered(f.year, f.month, f.day, f.type, f.category, f.tagQuery)
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
        tagQuery: String = _filter.value.tagQuery
    ) {
        _filter.value = FilterState(year, month, day, type, category, tagQuery)
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

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch { repo.deleteTransaction(tx) }
    }

    fun addGoal(title: String, target: Double, deadline: String = "") {
        viewModelScope.launch {
            repo.addGoal(SavingsGoal(title = title, targetAmount = target, deadline = deadline))
        }
    }

    fun updateGoalProgress(goal: SavingsGoal, addAmount: Double) {
        viewModelScope.launch {
            repo.updateGoal(goal.copy(currentAmount = goal.currentAmount + addAmount))
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch { repo.deleteGoal(goal) }
    }

    fun addCorrection(amount: Double, note: String = "", date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            repo.addCorrection(
                FundCorrection(
                    amount = amount,
                    note = note,
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth
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
}

class MainViewModelFactory(private val repo: BookkeepingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repo) as T
    }
}
