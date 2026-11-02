package ro.punguta.app.ui.incomes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.punguta.app.R
import ro.punguta.app.data.db.IncomeEntity
import ro.punguta.app.data.repo.IncomeError
import ro.punguta.app.data.repo.IncomeRepository
import ro.punguta.app.data.repo.InvalidIncomeException
import ro.punguta.app.domain.money.Money
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.AmountField
import ro.punguta.app.ui.components.ConfirmDialog
import ro.punguta.app.ui.components.DateField
import ro.punguta.app.ui.components.LoadingState
import java.time.LocalDate
import java.time.YearMonth

data class IncomeForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val name: String = "",
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val isGenerated: Boolean = false,
    val error: IncomeError? = null,
    val done: Boolean = false,
)

class IncomeEditViewModel(private val repo: IncomeRepository, state: SavedStateHandle) : ViewModel() {
    private val id: Long = state.get<Long>("id") ?: 0L
    private val _form = MutableStateFlow(IncomeForm())
    val form = _form.asStateFlow()

    fun load(defaultMonth: YearMonth) = viewModelScope.launch {
        if (_form.value.loaded) return@launch
        val existing = if (id != 0L) repo.get(id) else null
        _form.value = if (existing != null) {
            IncomeForm(
                loaded = true, id = existing.id, name = existing.name,
                amount = Money.formatInput(existing.amountBani), date = LocalDate.ofEpochDay(existing.date),
                note = existing.note.orEmpty(), isGenerated = existing.templateId != null,
            )
        } else {
            IncomeForm(loaded = true, date = defaultDate(defaultMonth))
        }
    }

    fun update(transform: (IncomeForm) -> IncomeForm) = _form.update { transform(it).copy(error = null) }

    fun save() = viewModelScope.launch {
        val f = _form.value
        val amount = Money.parse(f.amount)
        if (amount == null || amount <= 0) {
            _form.update { it.copy(error = IncomeError.AMOUNT_INVALID) }
            return@launch
        }
        try {
            // The template link is preserved by the repository for generated entries.
            repo.save(IncomeEntity(id = f.id, name = f.name, amountBani = amount, date = f.date.toEpochDay(), note = f.note))
            _form.update { it.copy(done = true) }
        } catch (e: InvalidIncomeException) {
            _form.update { it.copy(error = e.error) }
        }
    }

    fun delete() = viewModelScope.launch {
        if (id != 0L) repo.delete(id)
        _form.update { it.copy(done = true) }
    }

    companion object {
        val Factory = appViewModelFactory { c, s -> IncomeEditViewModel(c.incomes, s) }
    }
}

/** Today when adding in the current month, otherwise the first day of the month being viewed. */
fun defaultDate(month: YearMonth): LocalDate {
    val today = LocalDate.now()
    return if (YearMonth.from(today) == month) today else month.atDay(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeEditScreen(
    defaultMonth: YearMonth,
    onDone: () -> Unit,
    vm: IncomeEditViewModel = viewModel(factory = IncomeEditViewModel.Factory),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load(defaultMonth) }
    LaunchedEffect(form.done) { if (form.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.id == 0L) R.string.income_add else R.string.income_edit)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (form.id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                },
            )
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
            if (form.isGenerated) {
                Text(
                    stringResource(R.string.income_generated_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.income_name)) },
                isError = form.error == IncomeError.NAME_EMPTY,
                supportingText = if (form.error == IncomeError.NAME_EMPTY) {
                    { Text(stringResource(R.string.error_name_empty)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AmountField(
                value = form.amount,
                onValueChange = { v -> vm.update { it.copy(amount = v) } },
                label = stringResource(R.string.amount),
                isError = form.error == IncomeError.AMOUNT_INVALID,
                supportingText = if (form.error == IncomeError.AMOUNT_INVALID) stringResource(R.string.error_amount) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                date = form.date,
                onDateChange = { d -> vm.update { it.copy(date = d) } },
                label = stringResource(R.string.date),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> vm.update { it.copy(note = v) } },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.income_delete_title),
            message = stringResource(R.string.delete_cannot_undo),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
