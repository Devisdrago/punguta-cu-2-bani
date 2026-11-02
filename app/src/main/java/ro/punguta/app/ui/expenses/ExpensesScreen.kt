package ro.punguta.app.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import ro.punguta.app.data.db.ExpenseSource
import ro.punguta.app.data.db.ExpenseWithTotal
import ro.punguta.app.data.repo.ExpenseRepository
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.MonthViewModel
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.MonthSwitcher
import ro.punguta.app.ui.components.formatEpochDay
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(repo: ExpenseRepository, month: StateFlow<YearMonth>) : ViewModel() {
    val expenses: StateFlow<List<ExpenseWithTotal>?> = month.flatMapLatest { repo.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(month: StateFlow<YearMonth>) = appViewModelFactory { c, _ -> ExpensesViewModel(c.expenses, month) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    monthVm: MonthViewModel,
    onAddManual: () -> Unit,
    onScan: (() -> Unit)?,
    onEdit: (Long) -> Unit,
    onRecurring: () -> Unit,
    vm: ExpensesViewModel = viewModel(factory = ExpensesViewModel.factory(monthVm.month)),
) {
    val month by monthVm.month.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_expenses)) },
                actions = {
                    IconButton(onClick = onRecurring) {
                        Icon(Icons.Default.EventRepeat, stringResource(R.string.recurring_expenses))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onAddManual) {
                    Icon(Icons.Default.Edit, stringResource(R.string.expense_add))
                }
                if (onScan != null) {
                    ExtendedFloatingActionButton(
                        onClick = onScan,
                        icon = { Icon(Icons.Default.DocumentScanner, null) },
                        text = { Text(stringResource(R.string.receipt_scan)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            MonthSwitcher(month, monthVm::previous, monthVm::next, monthVm::current)
            val list = expenses
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(
                    Icons.Default.ShoppingCart,
                    stringResource(R.string.expenses_empty_title),
                    stringResource(R.string.expenses_empty_message),
                )
                else -> {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.total)) },
                        trailingContent = {
                            Text(
                                Money.format(list.sumOf { it.itemsTotal }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                    HorizontalDivider()
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(list, key = { it.expense.id }) { row ->
                            val e = row.expense
                            ListItem(
                                headlineContent = { Text(e.title) },
                                supportingContent = {
                                    Text(
                                        formatEpochDay(e.date) + " · " +
                                            pluralStringResource(R.plurals.item_count, row.itemCount, row.itemCount),
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        when (e.source) {
                                            ExpenseSource.RECEIPT -> Icons.Default.Receipt
                                            ExpenseSource.RECURRING -> Icons.Default.Repeat
                                            else -> Icons.Default.Edit
                                        },
                                        null,
                                    )
                                },
                                trailingContent = { Text(Money.format(row.itemsTotal)) },
                                modifier = Modifier.clickable { onEdit(e.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
