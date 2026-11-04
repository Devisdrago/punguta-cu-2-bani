package ro.punguta.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ro.punguta.app.R
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.data.repo.RecurringRepository
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.recurring.RecurrenceRules
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.AppCard
import ro.punguta.app.ui.components.BadgeTone
import ro.punguta.app.ui.components.CardRow
import ro.punguta.app.ui.components.DetailTopBar
import ro.punguta.app.ui.components.IconBadge
import ro.punguta.app.ui.components.RoundIconButton
import ro.punguta.app.ui.theme.tabular
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Repeat
import ro.punguta.app.ui.components.formatDate
import java.time.LocalDate

class TemplatesViewModel(repo: RecurringRepository, private val kind: String) : ViewModel() {
    val templates: StateFlow<List<RecurringTemplateEntity>?> = repo.templates
        .map { all -> all.filter { it.kind == kind } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(kind: String) = appViewModelFactory { c, _ -> TemplatesViewModel(c.recurring, kind) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    kind: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: TemplatesViewModel = viewModel(key = "templates-$kind", factory = TemplatesViewModel.factory(kind)),
) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    val isIncome = kind == TemplateKind.INCOME
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            DetailTopBar(stringResource(if (isIncome) R.string.recurring_incomes else R.string.recurring_expenses), onBack) {
                RoundIconButton(Icons.Default.Add, stringResource(R.string.template_add), onAdd, filled = true)
            }
        },
    ) { padding ->
        val list = templates
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.template_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            )
            when {
                list == null -> LoadingState()
                list.isEmpty() -> AppCard {
                    EmptyState(
                        Icons.Outlined.EventRepeat,
                        stringResource(R.string.templates_empty_title),
                        stringResource(if (isIncome) R.string.templates_empty_income else R.string.templates_empty_expense),
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        AppCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            list.forEach { t ->
                                val next = RecurrenceRules.nextOccurrence(
                                    t.dayOfMonth, LocalDate.ofEpochDay(t.startDate), t.endDate?.let(LocalDate::ofEpochDay), today,
                                )
                                val stopped = (t.endDate != null && t.endDate <= today.toEpochDay()) || next == null
                                val status = if (stopped) {
                                    stringResource(R.string.template_stopped)
                                } else {
                                    stringResource(R.string.template_next, formatDate(next!!))
                                }
                                CardRow(
                                    title = t.name,
                                    subtitle = stringResource(R.string.template_day, t.dayOfMonth) + " · " + status,
                                    leading = {
                                        IconBadge(
                                            Icons.Outlined.Repeat,
                                            when {
                                                stopped -> BadgeTone.NEUTRAL
                                                isIncome -> BadgeTone.INCOME
                                                else -> BadgeTone.ACCENT
                                            },
                                        )
                                    },
                                    trailing = { Text(Money.format(t.amountBani), style = MaterialTheme.typography.titleSmall.tabular) },
                                    onClick = { onEdit(t.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
