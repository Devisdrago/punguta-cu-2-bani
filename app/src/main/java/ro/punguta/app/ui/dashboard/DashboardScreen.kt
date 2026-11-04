package ro.punguta.app.ui.dashboard

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import ro.punguta.app.ui.components.AccentProgress
import ro.punguta.app.ui.components.AppCard
import ro.punguta.app.ui.components.BackToCurrentMonth
import ro.punguta.app.ui.components.BadgeTone
import ro.punguta.app.ui.components.CardRow
import ro.punguta.app.ui.components.CardTitle
import ro.punguta.app.ui.components.EmptyState
import ro.punguta.app.ui.components.ErrorState
import ro.punguta.app.ui.components.IconBadge
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.MonthNavButtons
import ro.punguta.app.ui.components.PillButton
import ro.punguta.app.ui.components.RoundIconButton
import ro.punguta.app.ui.components.ScreenHeader
import ro.punguta.app.ui.components.monthName
import ro.punguta.app.ui.theme.LocalMoneyPalette
import ro.punguta.app.ui.theme.tabular

@Composable
fun DashboardScreen(
    monthVm: MonthViewModel,
    onCategory: (Long) -> Unit,
    onBudgets: () -> Unit,
    onAddExpense: () -> Unit,
    onScan: () -> Unit,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(monthVm.month)),
) {
    val month by monthVm.month.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = monthName(month),
            subtitle = stringResource(R.string.dash_subtitle, month.year),
            subtitleActions = { MonthNavButtons(monthVm::previous, monthVm::next) },
            below = { BackToCurrentMonth(month, monthVm::current) },
        ) {
            RoundIconButton(Icons.Default.Add, stringResource(R.string.expense_add), onAddExpense, filled = true)
        }
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                DashboardState.Loading -> LoadingState(Modifier.height(240.dp))
                DashboardState.Error -> ErrorState(stringResource(R.string.dash_error))
                is DashboardState.Ready -> {
                    Balance(s.data)
                    SavingsRate(s.data)
                    PillButton(
                        stringResource(R.string.receipt_scan),
                        onScan,
                        icon = Icons.Outlined.DocumentScanner,
                    )
                    if (s.data.isEmpty) {
                        AppCard {
                            EmptyState(
                                Icons.Outlined.Insights,
                                stringResource(R.string.dash_empty_title),
                                stringResource(R.string.dash_empty_message),
                            )
                        }
                    }
                    Budgets(s.data.budgets, onBudgets)
                    if (s.data.categories.isNotEmpty()) Categories(s.data, onCategory)
                    AppCard {
                        CardTitle(stringResource(R.string.dash_trend))
                        TrendChart(s.data.trend)
                    }
                    Changes(s.data)
                }
            }
        }
    }
}

/** Balance, then the month's income and spending; the bar shows how much of the income is spent. */
@Composable
private fun Balance(d: DashboardData) {
    val money = LocalMoneyPalette.current
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Text(stringResource(R.string.dash_balance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Money.format(d.balanceBani),
            style = MaterialTheme.typography.headlineMedium.tabular,
            color = if (d.balanceBani < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )
        val spentShare = if (d.incomeBani > 0) d.spendingBani.toFloat() / d.incomeBani else if (d.spendingBani > 0) 1f else 0f
        AccentProgress(spentShare, color = if (spentShare > 1f) MaterialTheme.colorScheme.error else money.accent)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Flow(
                label = stringResource(R.string.dash_income),
                bani = d.incomeBani,
                delta = d.incomeBani - d.previousIncomeBani,
                higherIsGood = true,
                badge = { IconBadge(Icons.Outlined.AccountBalanceWallet, BadgeTone.NEUTRAL, size = 36.dp) },
                modifier = Modifier.weight(1f),
            )
            Flow(
                label = stringResource(R.string.dash_spending),
                bani = d.spendingBani,
                delta = d.spendingBani - d.previousSpendingBani,
                higherIsGood = false,
                badge = { IconBadge(Icons.Outlined.ShoppingBag, BadgeTone.ACCENT, size = 36.dp) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Flow(
    label: String,
    bani: Long,
    delta: Long,
    higherIsGood: Boolean,
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            badge()
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            Money.format(bani),
            style = MaterialTheme.typography.titleMedium.tabular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (delta != 0L) DeltaText(delta, higherIsGood)
    }
}

/** "+120,00 lei față de luna trecută", green when the change is good, red when it is bad. */
@Composable
private fun DeltaText(delta: Long, higherIsGood: Boolean) {
    val good = (delta > 0) == higherIsGood
    val color = if (good) LocalMoneyPalette.current.income else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (delta > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            null,
            tint = color,
            modifier = Modifier.padding(end = 4.dp).height(16.dp),
        )
        Text(
            (if (delta > 0) "+" else "") + Money.format(delta),
            style = MaterialTheme.typography.bodySmall.tabular,
            color = color,
            maxLines = 1,
        )
    }
}

/** Styled like a slider card: badge, label, accent track and the value on the right. */
@Composable
private fun SavingsRate(d: DashboardData) {
    val rate = d.savingsRateBasisPoints
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Savings, BadgeTone.NEUTRAL)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_savings_rate), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                AccentProgress((rate ?: 0L).coerceAtLeast(0L) / 10_000f)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                rate?.let(::formatBasisPoints) ?: "—",
                style = MaterialTheme.typography.titleMedium.tabular,
                color = if ((rate ?: 0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (rate == null) {
            Text(
                stringResource(R.string.dash_savings_na),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun Budgets(budgets: List<BudgetStatus>, onBudgets: () -> Unit) {
    val money = LocalMoneyPalette.current
    AppCard {
        CardTitle(stringResource(R.string.budgets)) {
            TextButton(onClick = onBudgets) { Text(stringResource(R.string.manage)) }
        }
        if (budgets.isEmpty()) {
            Text(
                stringResource(R.string.budgets_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        budgets.forEach { b ->
            val color = when (b.level) {
                BudgetLevel.OK -> MaterialTheme.colorScheme.primary
                BudgetLevel.WARNING -> money.warning
                BudgetLevel.EXCEEDED -> MaterialTheme.colorScheme.error
            }
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (b.level != BudgetLevel.OK) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.padding(end = 6.dp).height(18.dp))
                    }
                    Text(b.categoryName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${Money.format(b.spentBani)} / ${Money.format(b.limitBani)}",
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AccentProgress(b.progress, Modifier.padding(vertical = 8.dp), color = color)
                when (b.level) {
                    BudgetLevel.WARNING -> Text(
                        stringResource(R.string.budget_warning, Money.format(b.remainingBani)),
                        color = color, style = MaterialTheme.typography.bodySmall,
                    )
                    BudgetLevel.EXCEEDED -> Text(
                        stringResource(R.string.budget_exceeded, Money.format(-b.remainingBani)),
                        color = color, style = MaterialTheme.typography.labelMedium,
                    )
                    BudgetLevel.OK -> Unit
                }
            }
        }
    }
}

@Composable
private fun Categories(d: DashboardData, onCategory: (Long) -> Unit) {
    AppCard {
        CardTitle(stringResource(R.string.dash_by_category))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            SpendingDonut(d.categories)
        }
        val total = d.spendingBani.coerceAtLeast(1)
        d.categories.forEachIndexed { i, c ->
            CardRow(
                title = c.name,
                subtitle = formatBasisPoints(c.bani * 10_000 / total),
                leading = { ColorDot(categoryColor(i)) },
                trailing = { Text(Money.format(c.bani), style = MaterialTheme.typography.titleSmall.tabular) },
                onClick = { onCategory(c.categoryId) },
            )
        }
    }
}

@Composable
private fun Changes(d: DashboardData) {
    if (d.categoryChanges.isEmpty()) return
    val income = LocalMoneyPalette.current.income
    AppCard {
        CardTitle(stringResource(R.string.dash_changes))
        d.categoryChanges.take(8).forEach { c ->
            val color: Color = if (c.deltaBani > 0) MaterialTheme.colorScheme.error else income
            CardRow(
                title = c.name,
                subtitle = stringResource(R.string.dash_change_detail, Money.format(c.previousBani), Money.format(c.currentBani)),
                trailing = {
                    Text(
                        (if (c.deltaBani > 0) "+" else "") + Money.format(c.deltaBani),
                        color = color,
                        style = MaterialTheme.typography.titleSmall.tabular,
                    )
                },
            )
        }
    }
}
