package ro.punguta.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ro.punguta.app.R
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.db.ExpenseWithTotal
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.MonthViewModel
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.AppCard
import ro.punguta.app.ui.components.BadgeTone
import ro.punguta.app.ui.components.CardRow
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.IconBadge
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.MonthPill
import ro.punguta.app.ui.components.PillButton
import ro.punguta.app.ui.components.RoundIconButton
import ro.punguta.app.ui.components.ScreenHeader
import ro.punguta.app.ui.components.SegmentOption
import ro.punguta.app.ui.components.SegmentedTabs
import ro.punguta.app.ui.components.formatEpochDay
import ro.punguta.app.ui.theme.tabular
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(repo: ExpenseRepository, month: StateFlow<YearMonth>) : ViewModel() {
    val expenses: StateFlow<List<ExpenseWithTotal>?> = month.flatMapLatest { repo.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(month: StateFlow<YearMonth>) = appViewModelFactory { c, _ -> ExpensesViewModel(c.expenses, month) }
    }
}

@Composable
fun ExpensesScreen(
    monthVm: MonthViewModel,
    onAddManual: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecurring: () -> Unit,
    vm: ExpensesViewModel = viewModel(factory = ExpensesViewModel.factory(monthVm.month)),
) {
    val month by monthVm.month.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.tab_expenses),
                    subtitle = null,
                    sidePadding = 2.dp,
                    below = { MonthPill(month, monthVm::previous, monthVm::next, monthVm::current) },
                ) {
                    RoundIconButton(Icons.Outlined.EventRepeat, stringResource(R.string.recurring_expenses), onRecurring)
                    RoundIconButton(Icons.Default.Add, stringResource(R.string.expense_add), onAddManual, filled = true)
                }
            }
            item {
                SegmentedTabs(
                    options = listOf(
                        SegmentOption(stringResource(R.string.filter_all), Icons.Outlined.ViewAgenda),
                        SegmentOption(stringResource(R.string.filter_receipts), Icons.Outlined.Receipt),
                        SegmentOption(stringResource(R.string.filter_recurring), Icons.Outlined.Repeat),
                    ),
                    selected = filter,
                    onSelect = { filter = it },
                )
            }
            val list = expenses
            when {
                list == null -> item { LoadingState(Modifier) }
                list.isEmpty() -> item {
                    AppCard {
                        EmptyState(
                            Icons.Outlined.ShoppingBag,
                            stringResource(R.string.expenses_empty_title),
                            stringResource(R.string.expenses_empty_message),
                        )
                    }
                }
                else -> {
                    val shown = when (filter) {
                        1 -> list.filter { it.expense.source == ExpenseSource.RECEIPT }
                        2 -> list.filter { it.expense.source == ExpenseSource.RECURRING }
                        else -> list
                    }
                    item {
                        AppCard {
                            CardRow(
                                title = stringResource(R.string.total),
                                trailing = {
                                    Text(Money.format(shown.sumOf { it.itemsTotal }), style = MaterialTheme.typography.titleLarge.tabular)
                                },
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item { AppCard { EmptyState(Icons.Outlined.ViewAgenda, stringResource(R.string.filter_empty), "") } }
                    } else {
                        item {
                            AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                shown.forEach { row ->
                                    val e = row.expense
                                    CardRow(
                                        title = e.title,
                                        subtitle = formatEpochDay(e.date) + " · " +
                                            pluralStringResource(R.plurals.item_count, row.itemCount, row.itemCount),
                                        leading = {
                                            when (e.source) {
                                                ExpenseSource.RECEIPT -> IconBadge(Icons.Outlined.Receipt, BadgeTone.ACCENT)
                                                ExpenseSource.RECURRING -> IconBadge(Icons.Outlined.Repeat, BadgeTone.NEUTRAL)
                                                else -> IconBadge(Icons.Outlined.EditNote, BadgeTone.NEUTRAL)
                                            }
                                        },
                                        trailing = {
                                            Text(Money.format(row.itemsTotal), style = MaterialTheme.typography.titleSmall.tabular)
                                        },
                                        onClick = { onEdit(e.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
