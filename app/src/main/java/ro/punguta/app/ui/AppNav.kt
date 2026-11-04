package ro.punguta.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ro.punguta.app.R
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.ui.budgets.BudgetsScreen
import ro.punguta.app.ui.categories.CategoriesScreen
import ro.punguta.app.ui.components.FloatingNavBar
import ro.punguta.app.ui.components.NavCenterAction
import ro.punguta.app.ui.components.NavItem
import ro.punguta.app.ui.dashboard.CategoryMonthScreen
import ro.punguta.app.ui.dashboard.DashboardScreen
import ro.punguta.app.ui.expenses.ExpenseEditScreen
import ro.punguta.app.ui.expenses.ExpensesScreen
import ro.punguta.app.ui.incomes.IncomeEditScreen
import ro.punguta.app.ui.incomes.IncomesScreen
import ro.punguta.app.ui.recurring.TemplateEditScreen
import ro.punguta.app.ui.recurring.TemplatesScreen
import ro.punguta.app.ui.scan.ScanScreen
import ro.punguta.app.ui.settings.SettingsScreen

object Routes {
    /** The four main tabs, side by side in a swipeable pager. */
    const val HOME = "home"
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

private data class Tab(val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_dashboard, Icons.Outlined.Home),
    Tab(R.string.tab_incomes, Icons.Outlined.AccountBalanceWallet),
    Tab(R.string.tab_expenses, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab(R.string.tab_settings, Icons.Outlined.Settings),
)

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    // Activity-scoped so the selected month is shared between tabs.
    val monthVm: MonthViewModel = viewModel()
    val selectedMonth by monthVm.month.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val showBar = backStack?.destination?.route == Routes.HOME
    val back: () -> Unit = { nav.popBackStack() }
    // Hoisted so the bar and the pager share one position, and it survives pushed screens.
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen applies its own status-bar inset; the bar applies the navigation-bar inset.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                FloatingNavBar(
                    items = tabs.map { NavItem(stringResource(it.label), it.icon) },
                    position = pager.currentPage + pager.currentPageOffsetFraction,
                    onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
                    center = NavCenterAction(Icons.Outlined.DocumentScanner, stringResource(R.string.receipt_scan)) {
                        nav.navigate(Routes.SCAN)
                    },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomePager(pager, monthVm, nav, selectedMonth.toString())
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

/** The main tabs; swipe left or right to move between them. */
@Composable
private fun HomePager(pager: PagerState, monthVm: MonthViewModel, nav: NavHostController, month: String) {
    val scope = rememberCoroutineScope()
    // Back from another tab returns to the dashboard first, then leaves the app.
    BackHandler(enabled = pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }

    // Off-screen tabs are disposed; their scroll position and filters are restored from saved state.
    HorizontalPager(pager) { page ->
        when (page) {
            0 -> DashboardScreen(
                monthVm = monthVm,
                onCategory = { nav.navigate(Routes.categoryMonth(it, month)) },
                onBudgets = { nav.navigate(Routes.BUDGETS) },
                onAddExpense = { nav.navigate(Routes.expenseEdit()) },
                onExpense = { nav.navigate(Routes.expenseEdit(it)) },
            )
            1 -> IncomesScreen(
                monthVm = monthVm,
                onAdd = { nav.navigate(Routes.incomeEdit()) },
                onEdit = { nav.navigate(Routes.incomeEdit(it)) },
                onRecurring = { nav.navigate(Routes.templates(TemplateKind.INCOME)) },
            )
            2 -> ExpensesScreen(
                monthVm = monthVm,
                onAddManual = { nav.navigate(Routes.expenseEdit()) },
                onEdit = { nav.navigate(Routes.expenseEdit(it)) },
                onRecurring = { nav.navigate(Routes.templates(TemplateKind.EXPENSE)) },
            )
            else -> SettingsScreen(
                onCategories = { nav.navigate(Routes.CATEGORIES) },
                onBudgets = { nav.navigate(Routes.BUDGETS) },
            )
        }
    }
}
