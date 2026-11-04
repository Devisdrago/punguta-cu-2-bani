package ro.punguta.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.punguta.app.R
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.data.repo.BudgetRepository
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.AmountField
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.AppCard
import ro.punguta.app.ui.components.CardRow
import ro.punguta.app.ui.components.DetailTopBar
import ro.punguta.app.ui.theme.tabular
import androidx.compose.foundation.layout.PaddingValues

data class BudgetRow(val category: CategoryEntity, val limitBani: Long?)

class BudgetsViewModel(categories: CategoryRepository, private val budgets: BudgetRepository) : ViewModel() {
    val rows: StateFlow<List<BudgetRow>?> = combine(categories.categories, budgets.budgets) { cats, limits ->
        cats.map { BudgetRow(it, limits[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun set(categoryId: Long, limitBani: Long?) = viewModelScope.launch { budgets.set(categoryId, limitBani) }

    companion object {
        val Factory = appViewModelFactory { c, _ -> BudgetsViewModel(c.categories, c.budgets) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(onBack: () -> Unit, vm: BudgetsViewModel = viewModel(factory = BudgetsViewModel.Factory)) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }

    Scaffold(
        topBar = { DetailTopBar(stringResource(R.string.budgets), onBack) },
    ) { padding ->
        val list = rows
        if (list == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    stringResource(R.string.budgets_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                )
            }
            item {
                AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    list.forEach { row ->
                        CardRow(
                            title = row.category.name,
                            trailing = {
                                Text(
                                    row.limitBani?.let { Money.format(it) } ?: stringResource(R.string.budget_unset),
                                    style = MaterialTheme.typography.titleSmall.tabular,
                                    color = if (row.limitBani == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = { editing = row },
                        )
                    }
                }
            }
        }
    }

    editing?.let { row ->
        var text by remember(row) { mutableStateOf(row.limitBani?.let(Money::formatInput).orEmpty()) }
        val parsed = Money.parse(text)
        val invalid = text.isNotBlank() && parsed == null
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.budget_for, row.category.name)) },
            text = {
                Column {
                    AmountField(
                        value = text,
                        onValueChange = { text = it },
                        label = stringResource(R.string.budget_monthly_limit),
                        isError = invalid,
                        supportingText = stringResource(if (invalid) R.string.error_amount else R.string.budget_empty_removes),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !invalid, onClick = {
                    vm.set(row.category.id, if (text.isBlank()) null else parsed)
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
