package ro.punguta.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import java.time.LocalDate
import java.time.YearMonth

/** Activity-scoped selected month, shared by the dashboard and the income/expense lists. */
class MonthViewModel(private val state: SavedStateHandle) : ViewModel() {
    val month: StateFlow<YearMonth> = state.getStateFlow(KEY, YearMonth.now().toString())
        .map { YearMonth.parse(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, YearMonth.parse(state[KEY] ?: YearMonth.now().toString()))

    fun previous() = set(month.value.minusMonths(1))
    fun next() = set(month.value.plusMonths(1))
    fun current() = set(YearMonth.from(LocalDate.now()))
    fun set(month: YearMonth) {
        state[KEY] = month.toString()
    }

    private companion object {
        const val KEY = "selected_month"
    }
}
