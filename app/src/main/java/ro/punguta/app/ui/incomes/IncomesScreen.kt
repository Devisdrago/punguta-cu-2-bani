package ro.punguta.app.ui.incomes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.repo.IncomeRepository
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
import ro.punguta.app.ui.components.RoundIconButton
import ro.punguta.app.ui.components.ScreenHeader
import ro.punguta.app.ui.components.SegmentOption
import ro.punguta.app.ui.components.SegmentedTabs
import ro.punguta.app.ui.components.formatEpochDay
import ro.punguta.app.ui.theme.tabular
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class IncomesViewModel(repo: IncomeRepository, month: StateFlow<YearMonth>) : ViewModel() {
    val incomes: StateFlow<List<IncomeEntity>?> = month.flatMapLatest { repo.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(month: StateFlow<YearMonth>) = appViewModelFactory { c, _ -> IncomesViewModel(c.incomes, month) }
    }
}

@Composable
fun IncomesScreen(
    monthVm: MonthViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecurring: () -> Unit,
    vm: IncomesViewModel = viewModel(factory = IncomesViewModel.factory(monthVm.month)),
) {
    val month by monthVm.month.collectAsStateWithLifecycle()
    val incomes by vm.incomes.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.tab_incomes),
                subtitle = null,
                sidePadding = 2.dp,
                below = { MonthPill(month, monthVm::previous, monthVm::next, monthVm::current) },
            ) {
                RoundIconButton(Icons.Outlined.EventRepeat, stringResource(R.string.recurring_incomes), onRecurring)
                RoundIconButton(Icons.Default.Add, stringResource(R.string.income_add), onAdd, filled = true)
            }
        }
        item {
            SegmentedTabs(
                options = listOf(
                    SegmentOption(stringResource(R.string.filter_all), Icons.Outlined.ViewAgenda),
                    SegmentOption(stringResource(R.string.filter_recurring), Icons.Outlined.Repeat),
                    SegmentOption(stringResource(R.string.filter_one_time), Icons.Outlined.Payments),
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        val list = incomes
        when {
            list == null -> item { LoadingState(Modifier) }
            list.isEmpty() -> item {
                AppCard {
                    EmptyState(
                        Icons.Outlined.AccountBalanceWallet,
                        stringResource(R.string.incomes_empty_title),
                        stringResource(R.string.incomes_empty_message),
                    )
                }
            }
            else -> {
                val shown = when (filter) {
                    1 -> list.filter { it.templateId != null }
                    2 -> list.filter { it.templateId == null }
                    else -> list
                }
                item {
                    AppCard {
                        CardRow(
                            title = stringResource(R.string.total),
                            trailing = {
                                Text(Money.format(shown.sumOf { it.amountBani }), style = MaterialTheme.typography.titleLarge.tabular)
                            },
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item { AppCard { EmptyState(Icons.Outlined.ViewAgenda, stringResource(R.string.filter_empty), "") } }
                } else {
                    item {
                        AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            shown.forEach { income ->
                                CardRow(
                                    title = income.name,
                                    subtitle = listOfNotNull(formatEpochDay(income.date), income.note).joinToString(" · "),
                                    leading = {
                                        IconBadge(
                                            if (income.templateId != null) Icons.Outlined.Repeat else Icons.Outlined.Payments,
                                            if (income.templateId != null) BadgeTone.INCOME else BadgeTone.NEUTRAL,
                                        )
                                    },
                                    trailing = {
                                        Text(Money.format(income.amountBani), style = MaterialTheme.typography.titleSmall.tabular)
                                    },
                                    onClick = { onEdit(income.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

