package ro.punguta.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.punguta.app.R
import ro.punguta.app.data.db.RecurringTemplateEntity
import ro.punguta.app.data.db.TemplateKind
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.data.repo.InvalidTemplateException
import ro.punguta.app.data.repo.RecurringRepository
import ro.punguta.app.data.repo.TemplateError
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.AmountField
import ro.punguta.app.ui.components.CategoryDropdown
import ro.punguta.app.ui.components.ConfirmDialog
import ro.punguta.app.ui.components.DateField
import ro.punguta.app.ui.components.LoadingState
import ro.punguta.app.ui.components.highlightColors
import ro.punguta.app.ui.components.DetailTopBar
import ro.punguta.app.ui.components.PillButton
import ro.punguta.app.ui.components.PillKind
import ro.punguta.app.ui.components.RoundIconButton
import androidx.compose.material.icons.outlined.Delete
import java.time.LocalDate

data class TemplateForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val kind: String = TemplateKind.INCOME,
    val name: String = "",
    val amount: String = "",
    val day: String = LocalDate.now().dayOfMonth.toString(),
    val categoryId: Long? = null,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: Long? = null,
    val note: String = "",
    val error: TemplateError? = null,
    val done: Boolean = false,
) {
    val isStopped: Boolean get() = endDate != null
}

class TemplateEditViewModel(
    private val repo: RecurringRepository,
    categories: CategoryRepository,
    state: SavedStateHandle,
) : ViewModel() {
    private val id: Long = state.get<Long>("id") ?: 0L
    private val kind: String = state.get<String>("kind") ?: TemplateKind.INCOME
    private val _form = MutableStateFlow(TemplateForm(kind = kind))
    val form = _form.asStateFlow()
    val categories = categories.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val t = if (id != 0L) repo.get(id) else null
            _form.value = if (t != null) {
                TemplateForm(
                    loaded = true, id = t.id, kind = t.kind, name = t.name, amount = Money.formatInput(t.amountBani),
                    day = t.dayOfMonth.toString(), categoryId = t.categoryId, startDate = LocalDate.ofEpochDay(t.startDate),
                    endDate = t.endDate, note = t.note.orEmpty(),
                )
            } else {
                TemplateForm(loaded = true, kind = kind)
            }
        }
    }

    fun update(transform: (TemplateForm) -> TemplateForm) = _form.update { transform(it).copy(error = null) }

    fun save() = viewModelScope.launch {
        val f = _form.value
        val amount = Money.parse(f.amount)
        val day = f.day.toIntOrNull()
        when {
            amount == null || amount <= 0 -> return@launch _form.update { it.copy(error = TemplateError.AMOUNT_INVALID) }
            day == null || day !in 1..31 -> return@launch _form.update { it.copy(error = TemplateError.DAY_INVALID) }
        }
        try {
            repo.save(
                RecurringTemplateEntity(
                    id = f.id, kind = f.kind, name = f.name, amountBani = amount!!, dayOfMonth = day!!,
                    categoryId = f.categoryId, startDate = f.startDate.toEpochDay(), endDate = f.endDate, note = f.note,
                ),
            )
            // Create anything already due (e.g. a salary added on or after its payday).
            repo.generateDue(LocalDate.now())
            _form.update { it.copy(done = true) }
        } catch (e: InvalidTemplateException) {
            _form.update { it.copy(error = e.error) }
        }
    }

    fun stop() = viewModelScope.launch {
        repo.stop(id, LocalDate.now())
        _form.update { it.copy(done = true) }
    }

    fun resume() = viewModelScope.launch {
        repo.resume(id, LocalDate.now())
        repo.generateDue(LocalDate.now())
        _form.update { it.copy(done = true) }
    }

    fun delete() = viewModelScope.launch {
        repo.delete(id)
        _form.update { it.copy(done = true) }
    }

    companion object {
        val Factory = appViewModelFactory { c, s -> TemplateEditViewModel(c.recurring, c.categories, s) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(onDone: () -> Unit, vm: TemplateEditViewModel = viewModel(factory = TemplateEditViewModel.Factory)) {
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    val isExpense = form.kind == TemplateKind.EXPENSE

    LaunchedEffect(form.done) { if (form.done) onDone() }

    Scaffold(
        topBar = {
            DetailTopBar(
                stringResource(
                    when {
                        form.id != 0L -> R.string.template_edit
                        isExpense -> R.string.template_new_expense
                        else -> R.string.template_new_income
                    },
                ),
                onDone,
            ) {
                if (form.id != 0L) {
                    RoundIconButton(Icons.Outlined.Delete, stringResource(R.string.delete), { confirm = "delete" })
                }
            }
        },
    ) { padding ->
        if (!form.loaded) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (form.id != 0L) {
                Text(
                    stringResource(R.string.template_edit_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                colors = highlightColors(false),
                value = form.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(if (isExpense) R.string.template_name_hint_expense else R.string.template_name_hint_income)) },
                isError = form.error == TemplateError.NAME_EMPTY,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AmountField(
                value = form.amount,
                onValueChange = { v -> vm.update { it.copy(amount = v) } },
                label = stringResource(R.string.amount_monthly),
                isError = form.error == TemplateError.AMOUNT_INVALID,
                supportingText = if (form.error == TemplateError.AMOUNT_INVALID) stringResource(R.string.error_amount) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                colors = highlightColors(false),
                value = form.day,
                onValueChange = { v -> vm.update { it.copy(day = v.filter(Char::isDigit).take(2)) } },
                label = { Text(stringResource(R.string.template_day_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (form.error == TemplateError.DAY_INVALID) R.string.error_day else R.string.template_day_hint,
                        ),
                    )
                },
                isError = form.error == TemplateError.DAY_INVALID,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isExpense) {
                CategoryDropdown(
                    categories = categories,
                    selectedId = form.categoryId,
                    onSelect = { id -> vm.update { it.copy(categoryId = id) } },
                    isError = form.error == TemplateError.CATEGORY_MISSING,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (form.id == 0L) {
                DateField(
                    date = form.startDate,
                    onDateChange = { d -> vm.update { it.copy(startDate = d) } },
                    label = stringResource(R.string.template_start),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                colors = highlightColors(false),
                value = form.note,
                onValueChange = { v -> vm.update { it.copy(note = v) } },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            PillButton(stringResource(R.string.save), vm::save, modifier = Modifier.padding(top = 8.dp))
            if (form.id != 0L) {
                if (form.isStopped) {
                    PillButton(stringResource(R.string.template_resume), vm::resume, kind = PillKind.SECONDARY)
                } else {
                    PillButton(stringResource(R.string.template_stop), { confirm = "stop" }, kind = PillKind.SECONDARY)
                }
            }
        }
    }

    when (confirm) {
        "stop" -> ConfirmDialog(
            title = stringResource(R.string.template_stop),
            message = stringResource(R.string.template_stop_message),
            confirmLabel = stringResource(R.string.template_stop),
            onConfirm = { confirm = null; vm.stop() },
            onDismiss = { confirm = null },
        )
        "delete" -> ConfirmDialog(
            title = stringResource(R.string.template_delete_title),
            message = stringResource(R.string.template_delete_message),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { confirm = null; vm.delete() },
            onDismiss = { confirm = null },
        )
    }
}

