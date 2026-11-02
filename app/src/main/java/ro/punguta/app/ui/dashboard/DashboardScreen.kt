package ro.punguta.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.punguta.app.R
import ro.punguta.app.domain.budget.BudgetLevel
import ro.punguta.app.domain.budget.BudgetStatus
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.money.formatBasisPoints
import ro.punguta.app.domain.stats.DashboardData
import ro.punguta.app.ui.MonthViewModel
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.ErrorState
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.MonthSwitcher
import ro.punguta.app.ui.theme.MoneyColors

@Composable
fun DashboardScreen(
    monthVm: MonthViewModel,
    onCategory: (Long) -> Unit,
    onBudgets: () -> Unit,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(monthVm.month)),
) {
    val month by monthVm.month.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        MonthSwitcher(month, monthVm::previous, monthVm::next, monthVm::current)
        when (val s = state) {
            DashboardState.Loading -> LoadingState()
            DashboardState.Error -> ErrorState(stringResource(R.string.dash_error))
            is DashboardState.Ready -> Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Summary(s.data)
                if (s.data.isEmpty) {
                    EmptyState(
                        Icons.Default.Insights,
                        stringResource(R.string.dash_empty_title),
                        stringResource(R.string.dash_empty_message),
                    )
                }
                Budgets(s.data.budgets, onBudgets)
                if (s.data.categories.isNotEmpty()) Categories(s.data, onCategory)
                Section(stringResource(R.string.dash_trend)) { TrendChart(s.data.trend) }
                Changes(s.data)
            }
        }
    }
}

@Composable
private fun Section(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun incomeColor() = if (androidx.compose.foundation.isSystemInDarkTheme()) MoneyColors.incomeDark else MoneyColors.income

@Composable
private fun Summary(d: DashboardData) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.dash_balance), style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(d.balanceBani),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (d.balanceBani < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SummaryRow(stringResource(R.string.dash_income), d.incomeBani, d.incomeBani - d.previousIncomeBani, higherIsGood = true)
            SummaryRow(stringResource(R.string.dash_spending), d.spendingBani, d.spendingBani - d.previousSpendingBani, higherIsGood = false)
            Row {
                Text(stringResource(R.string.dash_savings_rate), Modifier.weight(1f))
                Text(
                    d.savingsRateBasisPoints?.let(::formatBasisPoints) ?: stringResource(R.string.dash_savings_na),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, bani: Long, delta: Long, higherIsGood: Boolean) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text(Money.format(bani), fontWeight = FontWeight.SemiBold)
        }
        if (delta != 0L) DeltaText(delta, higherIsGood, stringResource(R.string.dash_vs_previous))
    }
}

/** "▲ 120,00 lei față de luna trecută", green when the change is good, red when it is bad. */
@Composable
private fun DeltaText(delta: Long, higherIsGood: Boolean, suffix: String) {
    val good = (delta > 0) == higherIsGood
    val color = if (good) incomeColor() else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (delta > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            null,
            tint = color,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            (if (delta > 0) "+" else "") + Money.format(delta) + " " + suffix,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun Budgets(budgets: List<BudgetStatus>, onBudgets: () -> Unit) {
    Section(
        stringResource(R.string.budgets),
        action = { TextButton(onClick = onBudgets) { Text(stringResource(R.string.manage)) } },
    ) {
        if (budgets.isEmpty()) {
            Text(stringResource(R.string.budgets_none), style = MaterialTheme.typography.bodyMedium)
        }
        budgets.forEach { b ->
            val color = when (b.level) {
                BudgetLevel.OK -> MaterialTheme.colorScheme.primary
                BudgetLevel.WARNING -> MoneyColors.warning
                BudgetLevel.EXCEEDED -> MaterialTheme.colorScheme.error
            }
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (b.level != BudgetLevel.OK) {
                        Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(b.categoryName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${Money.format(b.spentBani)} / ${Money.format(b.limitBani)}", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { b.progress },
                    color = color,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                when (b.level) {
                    BudgetLevel.WARNING -> Text(
                        stringResource(R.string.budget_warning, Money.format(b.remainingBani)),
                        color = color, style = MaterialTheme.typography.bodySmall,
                    )
                    BudgetLevel.EXCEEDED -> Text(
                        stringResource(R.string.budget_exceeded, Money.format(-b.remainingBani)),
                        color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                    )
                    BudgetLevel.OK -> Unit
                }
            }
        }
    }
}

@Composable
private fun Categories(d: DashboardData, onCategory: (Long) -> Unit) {
    Section(stringResource(R.string.dash_by_category)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            SpendingDonut(d.categories)
        }
        Spacer(Modifier.height(12.dp))
        val total = d.spendingBani.coerceAtLeast(1)
        d.categories.forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().clickable { onCategory(c.categoryId) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorDot(categoryColor(i))
                Spacer(Modifier.width(8.dp))
                Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatBasisPoints(c.bani * 10_000 / total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(Money.format(c.bani), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Changes(d: DashboardData) {
    if (d.categoryChanges.isEmpty()) return
    Section(stringResource(R.string.dash_changes)) {
        d.categoryChanges.take(8).forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name)
                    Text(
                        stringResource(R.string.dash_change_detail, Money.format(c.previousBani), Money.format(c.currentBani)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val color: Color = if (c.deltaBani > 0) MaterialTheme.colorScheme.error else incomeColor()
                Text((if (c.deltaBani > 0) "+" else "") + Money.format(c.deltaBani), color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
