package ro.punguta.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ro.punguta.app.AppContainer
import ro.punguta.app.domain.stats.DashboardCalculator
import ro.punguta.app.domain.stats.DashboardData
import ro.punguta.app.domain.stats.DatedAmount
import ro.punguta.app.ui.appViewModelFactory
import java.time.LocalDate
import java.time.YearMonth

sealed interface DashboardState {
    data object Loading : DashboardState
    data class Ready(val data: DashboardData) : DashboardState
    data object Error : DashboardState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(c: AppContainer, month: StateFlow<YearMonth>) : ViewModel() {
    val state: StateFlow<DashboardState> = month.flatMapLatest { m ->
        val from = m.minusMonths(DashboardCalculator.TREND_MONTHS - 1L)
        combine(
            c.incomes.observeRange(from, m),
            c.expenses.observeItems(from, m),
            c.categories.categories,
            c.budgets.budgets,
        ) { incomes, items, categories, budgets ->
            DashboardCalculator.build(
                month = m,
                incomes = incomes.map { DatedAmount(LocalDate.ofEpochDay(it.date), it.amountBani) },
                items = items.map { DatedAmount(LocalDate.ofEpochDay(it.date), it.item.amountBani, it.item.categoryId) },
                categoryNames = categories.associate { it.id to it.name },
                budgets = budgets,
            )
        }
    }
        .map<DashboardData, DashboardState> { DashboardState.Ready(it) }
        .catch { emit(DashboardState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState.Loading)

    companion object {
        fun factory(month: StateFlow<YearMonth>) = appViewModelFactory { c, _ -> DashboardViewModel(c, month) }
    }
}
