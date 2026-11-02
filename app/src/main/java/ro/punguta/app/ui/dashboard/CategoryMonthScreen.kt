package ro.punguta.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ro.punguta.app.AppContainer
import ro.punguta.app.R
import ro.punguta.app.data.db.ItemWithExpense
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.formatEpochDay
import ro.punguta.app.ui.components.formatMonth
import java.time.YearMonth

data class CategoryMonth(val title: String, val month: YearMonth, val items: List<ItemWithExpense>)

class CategoryMonthViewModel(c: AppContainer, state: SavedStateHandle) : ViewModel() {
    private val categoryId: Long = state.get<Long>("id") ?: 0
    private val month: YearMonth = YearMonth.parse(state.get<String>("month") ?: YearMonth.now().toString())

    val data: StateFlow<CategoryMonth?> = combine(c.expenses.observeItems(month, month), c.categories.categories) { items, cats ->
        CategoryMonth(
            title = cats.firstOrNull { it.id == categoryId }?.name.orEmpty(),
            month = month,
            items = items.filter { it.item.categoryId == categoryId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = appViewModelFactory { c, s -> CategoryMonthViewModel(c, s) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMonthScreen(
    onBack: () -> Unit,
    onExpense: (Long) -> Unit,
    vm: CategoryMonthViewModel = viewModel(factory = CategoryMonthViewModel.Factory),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(data?.let { "${it.title} · ${formatMonth(it.month)}" }.orEmpty())
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val d = data
        when {
            d == null -> LoadingState(Modifier.padding(padding))
            d.items.isEmpty() -> EmptyState(
                Icons.Default.ShoppingCart,
                stringResource(R.string.category_month_empty),
                "",
                Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.total)) },
                        trailingContent = {
                            Text(
                                Money.format(d.items.sumOf { it.item.amountBani }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                    HorizontalDivider()
                }
                items(d.items, key = { it.item.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.item.name) },
                        supportingContent = { Text("${row.title} · ${formatEpochDay(row.date)}") },
                        trailingContent = { Text(Money.format(row.item.amountBani)) },
                        modifier = Modifier.clickable { onExpense(row.item.expenseId) },
                    )
                }
            }
        }
    }
}
