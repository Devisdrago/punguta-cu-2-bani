package ro.punguta.app.ui.incomes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.MonthSwitcher
import ro.punguta.app.ui.components.formatEpochDay
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class IncomesViewModel(repo: IncomeRepository, month: StateFlow<YearMonth>) : ViewModel() {
    val incomes: StateFlow<List<IncomeEntity>?> = month.flatMapLatest { repo.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(month: StateFlow<YearMonth>) = appViewModelFactory { c, _ -> IncomesViewModel(c.incomes, month) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_incomes)) },
                actions = {
                    IconButton(onClick = onRecurring) {
                        Icon(Icons.Default.EventRepeat, stringResource(R.string.recurring_incomes))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.income_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            MonthSwitcher(month, monthVm::previous, monthVm::next, monthVm::current)
            val list = incomes
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(
                    Icons.Default.Payments,
                    stringResource(R.string.incomes_empty_title),
                    stringResource(R.string.incomes_empty_message),
                )
                else -> {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.total)) },
                        trailingContent = {
                            Text(
                                Money.format(list.sumOf { it.amountBani }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                    HorizontalDivider()
                    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                        items(list, key = { it.id }) { income ->
                            ListItem(
                                headlineContent = { Text(income.name) },
                                supportingContent = {
                                    Text(listOfNotNull(formatEpochDay(income.date), income.note).joinToString(" · "))
                                },
                                leadingContent = {
                                    if (income.templateId != null) {
                                        Icon(Icons.Default.Repeat, stringResource(R.string.recurring))
                                    }
                                },
                                trailingContent = { Text(Money.format(income.amountBani)) },
                                modifier = Modifier.clickable { onEdit(income.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
