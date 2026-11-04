package ro.punguta.app.ui.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.punguta.app.R
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.domain.expense.DraftError
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.receipt.ReceiptWarning
import ro.punguta.app.ui.components.AmountField
import ro.punguta.app.ui.components.CategoryDropdown
import ro.punguta.app.ui.components.ConfirmDialog
import ro.punguta.app.ui.components.DateField
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.DetailTopBar
import ro.punguta.app.ui.components.PillButton
import ro.punguta.app.ui.components.PillKind
import ro.punguta.app.ui.components.RoundIconButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import ro.punguta.app.ui.components.highlightColors
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    defaultMonth: YearMonth,
    onDone: () -> Unit,
    vm: ExpenseEditViewModel = viewModel(factory = ExpenseEditViewModel.Factory),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val confirmMismatch by vm.confirmMismatch.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.load(defaultMonth) }
    LaunchedEffect(form.done) { if (form.done) onDone() }

    // Leaving an unsaved receipt review asks first, so parsed data is never lost by accident.
    val guardBack = form.isReceipt && form.id == null
    BackHandler(enabled = guardBack && !form.done) { confirm = "discard" }
    val onClose = { if (guardBack) confirm = "discard" else vm.discard() }

    Scaffold(
        topBar = {
            DetailTopBar(
                stringResource(
                    when {
                        form.isReceipt && form.id == null -> R.string.receipt_review
                        form.id != null -> R.string.expense_edit
                        else -> R.string.expense_add
                    },
                ),
                onBack = onClose,
                backIcon = if (guardBack) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
            ) {
                TextButton(onClick = { vm.save() }, enabled = form.loaded) { Text(stringResource(R.string.save)) }
            }
        },
        bottomBar = { if (form.loaded) TotalsBar(form) },
    ) { padding ->
        if (!form.loaded) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (form.hasUncertainty) {
                item { Banner(stringResource(R.string.receipt_uncertain_banner)) }
            }
            form.receiptWarnings.mapNotNull(::warningText).forEach { text ->
                item { Banner(stringResource(text)) }
            }
            if (form.unusedLines.isNotEmpty()) {
                item { UnusedLines(form.unusedLines) }
            }
            form.error?.let { error ->
                item { Banner(stringResource(draftErrorText(error)), isError = true) }
            }
            item { Header(form, vm) }
            item {
                Text(
                    stringResource(R.string.items_title, form.items.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(form.items, key = { it.key }) { item ->
                ItemCard(
                    item = item,
                    categories = categories,
                    invalid = item.key in form.invalidItemKeys,
                    canRemove = form.items.size > 1,
                    vm = vm,
                )
            }
            item {
                PillButton(stringResource(R.string.item_add), vm::addItem, icon = Icons.Default.Add, kind = PillKind.SECONDARY)
            }
            item {
                PillButton(stringResource(R.string.save), { vm.save() })
                if (form.id != null) {
                    PillButton(
                        stringResource(R.string.expense_delete),
                        { confirm = "delete" },
                        icon = Icons.Outlined.Delete,
                        kind = PillKind.DANGER,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (confirmMismatch) {
        val diff = form.totalDifferenceBani ?: 0
        ConfirmDialog(
            title = stringResource(R.string.total_mismatch_title),
            message = stringResource(
                R.string.total_mismatch_message,
                Money.format(form.itemsTotalBani),
                Money.format(form.receiptTotalBani ?: 0),
                Money.format(diff),
            ),
            confirmLabel = stringResource(R.string.save_anyway),
            onConfirm = { vm.save(ignoreMismatch = true) },
            onDismiss = vm::dismissMismatch,
        )
    }
    when (confirm) {
        "discard" -> ConfirmDialog(
            title = stringResource(R.string.receipt_discard_title),
            message = stringResource(R.string.receipt_discard_message),
            confirmLabel = stringResource(R.string.discard),
            onConfirm = { confirm = null; vm.discard() },
            onDismiss = { confirm = null },
        )
        "delete" -> ConfirmDialog(
            title = stringResource(R.string.expense_delete_title),
            message = stringResource(R.string.delete_cannot_undo),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { confirm = null; vm.delete() },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun Header(form: ExpenseForm, vm: ExpenseEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> vm.updateHeader(HeaderField.TITLE) { it.copy(title = v) } },
            label = { Text(stringResource(if (form.isReceipt) R.string.merchant else R.string.description)) },
            colors = highlightColors(HeaderField.TITLE in form.uncertainHeader),
            isError = form.error == DraftError.TITLE_EMPTY,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            date = form.date,
            onDateChange = { d -> vm.updateHeader(HeaderField.DATE) { it.copy(date = d) } },
            label = stringResource(R.string.date),
            highlight = HeaderField.DATE in form.uncertainHeader,
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.isReceipt) {
            AmountField(
                value = form.receiptTotal,
                onValueChange = { v -> vm.updateHeader(HeaderField.TOTAL) { it.copy(receiptTotal = v) } },
                label = stringResource(R.string.receipt_total),
                highlight = HeaderField.TOTAL in form.uncertainHeader,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = form.note,
            onValueChange = { v -> vm.updateHeader(null) { it.copy(note = v) } },
            label = { Text(stringResource(R.string.note_optional)) },
            colors = highlightColors(false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ItemCard(
    item: ItemForm,
    categories: List<CategoryEntity>,
    invalid: Boolean,
    canRemove: Boolean,
    vm: ExpenseEditViewModel,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { v -> vm.updateItem(item.key, ItemField.NAME) { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.item_name)) },
                    colors = highlightColors(ItemField.NAME in item.uncertain),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onClick = { vm.removeItem(item.key) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.item_remove))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(
                    value = item.amount,
                    onValueChange = { v -> vm.updateItem(item.key, ItemField.AMOUNT) { it.copy(amount = v) } },
                    label = stringResource(R.string.amount),
                    isError = invalid,
                    highlight = ItemField.AMOUNT in item.uncertain,
                    modifier = Modifier.weight(1f),
                )
                CategoryDropdown(
                    categories = categories,
                    selectedId = item.categoryId,
                    onSelect = { id -> vm.updateItem(item.key, ItemField.CATEGORY) { it.copy(categoryId = id) } },
                    highlight = ItemField.CATEGORY in item.uncertain,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { vm.toggleDetails(item.key) }) {
                Icon(if (item.showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                Text(stringResource(R.string.item_details))
            }
            if (item.showDetails) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = item.quantity,
                        onValueChange = { v ->
                            vm.updateItem(item.key, ItemField.QUANTITY) {
                                it.copy(quantity = v.filter { c -> c.isDigit() || c == ',' || c == '.' })
                            }
                        },
                        label = { Text(stringResource(R.string.quantity)) },
                        colors = highlightColors(ItemField.QUANTITY in item.uncertain),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    AmountField(
                        value = item.unitPrice,
                        onValueChange = { v -> vm.updateItem(item.key, ItemField.UNIT_PRICE) { it.copy(unitPrice = v) } },
                        label = stringResource(R.string.unit_price),
                        highlight = ItemField.UNIT_PRICE in item.uncertain,
                        modifier = Modifier.weight(1f),
                    )
                }
                AmountField(
                    value = item.discount,
                    onValueChange = { v -> vm.updateItem(item.key, ItemField.DISCOUNT) { it.copy(discount = v) } },
                    label = stringResource(R.string.discount),
                    highlight = ItemField.DISCOUNT in item.uncertain,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TotalsBar(form: ExpenseForm) {
    val diff = form.totalDifferenceBani
    val mismatch = diff != null && diff != 0L
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)), shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row {
                Text(stringResource(R.string.items_total), Modifier.weight(1f))
                Text(Money.format(form.itemsTotalBani), fontWeight = FontWeight.Bold)
            }
            if (form.isReceipt) {
                Row {
                    Text(stringResource(R.string.receipt_total), Modifier.weight(1f))
                    Text(form.receiptTotalBani?.let(Money::format) ?: "—")
                }
                if (mismatch) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.total_mismatch_short, Money.format(diff!!)),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else if (diff == 0L) {
                    Text(stringResource(R.string.total_matches), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, isError: Boolean = false) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UnusedLines(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            TextButton(onClick = { open = !open }) {
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                Text(stringResource(R.string.receipt_unused_lines, lines.size))
            }
            if (open) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

private fun warningText(warning: ReceiptWarning): Int? = when (warning) {
    ReceiptWarning.NO_ITEMS -> R.string.warn_no_items
    ReceiptWarning.NO_TOTAL -> R.string.warn_no_total
    ReceiptWarning.TOTAL_FROM_PAYMENT -> R.string.warn_total_from_payment
    ReceiptWarning.NO_DATE -> R.string.warn_no_date
    ReceiptWarning.NO_MERCHANT -> null // the empty, highlighted field says enough
    ReceiptWarning.UNUSED_LINES -> null // shown as its own card
    ReceiptWarning.ORPHAN_DISCOUNT -> R.string.warn_orphan_discount
    ReceiptWarning.RECEIPT_DISCOUNT_SPREAD -> R.string.warn_discount_spread
}

private fun draftErrorText(error: DraftError): Int = when (error) {
    DraftError.TITLE_EMPTY -> R.string.error_title_empty
    DraftError.NO_ITEMS -> R.string.error_no_items
    DraftError.ITEM_NAME_EMPTY -> R.string.error_item_name
    DraftError.ITEM_AMOUNT_INVALID -> R.string.error_item_amount
    DraftError.ITEM_CATEGORY_MISSING -> R.string.error_item_category
    DraftError.TOTAL_ZERO -> R.string.error_total_zero
}
