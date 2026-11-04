package ro.punguta.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ro.punguta.app.R
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.ui.categories.CategoriesScreen
import ro.punguta.app.ui.budgets.BudgetsScreen
import ro.punguta.app.ui.components.FloatingNavBar
import ro.punguta.app.ui.components.NavItem
import ro.punguta.app.ui.dashboard.CategoryMonthScreen
import ro.punguta.app.ui.dashboard.DashboardScreen
import ro.punguta.app.ui.expenses.ExpenseEditScreen
import ro.punguta.app.ui.expenses.ExpensesScreen
import ro.punguta.app.ui.incomes.IncomeEditScreen
import ro.punguta.app.ui.incomes.IncomesScreen
import ro.punguta.app.ui.recurring.TemplateEditScreen
import ro.punguta.app.ui.scan.ScanScreen
import ro.punguta.app.ui.recurring.TemplatesScreen
import ro.punguta.app.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val INCOMES = "incomes"
    const val EXPENSES = "expenses"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val INCOME_EDIT = "income/{id}"
    const val EXPENSE_EDIT = "expense/{id}?draft={draft}"
    const val TEMPLATES = "templates/{kind}"
    const val TEMPLATE_EDIT = "template/{kind}/{id}"
    const val CATEGORY_MONTH = "category/{id}/{month}"
    const val BUDGETS = "budgets"
    const val SCAN = "scan"

    fun categoryMonth(id: Long, month: String) = "category/$id/$month"

    fun incomeEdit(id: Long = 0) = "income/$id"
    fun expenseEdit(id: Long = 0) = "expense/$id"
    fun expenseReview(draftKey: String) = "expense/0?draft=$draftKey"
    fun templates(kind: String) = "templates/$kind"
    fun templateEdit(kind: String, id: Long = 0) = "template/$kind/$id"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, R.string.tab_dashboard, Icons.Outlined.Home),
    Tab(Routes.INCOMES, R.string.tab_incomes, Icons.Outlined.AccountBalanceWallet),
    Tab(Routes.EXPENSES, R.string.tab_expenses, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Outlined.Settings),
)

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    // Activity-scoped so the selected month is shared between tabs.
    val monthVm: MonthViewModel = viewModel()
    val selectedMonth by monthVm.month.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen applies its own status-bar inset; the bar applies the navigation-bar inset.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                FloatingNavBar(
                    items = tabs.map { NavItem(stringResource(it.label), it.icon) },
                    selected = tabs.indexOfFirst { it.route == currentRoute },
                    onSelect = { index ->
                        nav.navigate(tabs[index].route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    monthVm = monthVm,
                    onCategory = { nav.navigate(Routes.categoryMonth(it, selectedMonth.toString())) },
                    onBudgets = { nav.navigate(Routes.BUDGETS) },
                    onAddExpense = { nav.navigate(Routes.expenseEdit()) },
                    onScan = { nav.navigate(Routes.SCAN) },
                )
            }
            composable(
                Routes.CATEGORY_MONTH,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("month") { type = NavType.StringType },
                ),
            ) {
                CategoryMonthScreen(onBack = back, onExpense = { nav.navigate(Routes.expenseEdit(it)) })
            }
            composable(Routes.BUDGETS) { BudgetsScreen(onBack = back) }
            composable(Routes.SCAN) {
                ScanScreen(
                    onBack = back,
                    onReview = { key ->
                        nav.navigate(Routes.expenseReview(key)) { popUpTo(Routes.SCAN) { inclusive = true } }
                    },
                )
            }
            composable(Routes.INCOMES) {
                IncomesScreen(
                    monthVm = monthVm,
                    onAdd = { nav.navigate(Routes.incomeEdit()) },
                    onEdit = { nav.navigate(Routes.incomeEdit(it)) },
                    onRecurring = { nav.navigate(Routes.templates(TemplateKind.INCOME)) },
                )
            }
            composable(Routes.EXPENSES) {
                ExpensesScreen(
                    monthVm = monthVm,
                    onAddManual = { nav.navigate(Routes.expenseEdit()) },
                    onScan = { nav.navigate(Routes.SCAN) },
                    onEdit = { nav.navigate(Routes.expenseEdit(it)) },
                    onRecurring = { nav.navigate(Routes.templates(TemplateKind.EXPENSE)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onBudgets = { nav.navigate(Routes.BUDGETS) },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = back) }
            composable(Routes.INCOME_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                IncomeEditScreen(defaultMonth = selectedMonth, onDone = back)
            }
            composable(
                Routes.EXPENSE_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("draft") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                ExpenseEditScreen(defaultMonth = selectedMonth, onDone = back)
            }
            composable(Routes.TEMPLATES, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
                val kind = entry.arguments?.getString("kind") ?: TemplateKind.INCOME
                TemplatesScreen(
                    kind = kind,
                    onBack = back,
                    onAdd = { nav.navigate(Routes.templateEdit(kind)) },
                    onEdit = { nav.navigate(Routes.templateEdit(kind, it)) },
                )
            }
            composable(
                Routes.TEMPLATE_EDIT,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                ),
            ) {
                TemplateEditScreen(onDone = back)
            }
        }
    }
}
