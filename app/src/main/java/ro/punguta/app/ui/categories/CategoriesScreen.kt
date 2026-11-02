package ro.punguta.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.punguta.app.R
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.data.repo.CategoryInUseException
import ro.punguta.app.data.repo.CategoryNameError
import ro.punguta.app.data.repo.CategoryNameException
import ro.punguta.app.data.repo.CategoryRepository
import ro.punguta.app.ui.appViewModelFactory
import ro.punguta.app.ui.components.LoadingState

sealed interface CategoryDialog {
    data object Add : CategoryDialog
    data class Rename(val category: CategoryEntity) : CategoryDialog
    data class Delete(val category: CategoryEntity, val usage: Int) : CategoryDialog
}

class CategoriesViewModel(private val repo: CategoryRepository) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>?> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dialog = MutableStateFlow<CategoryDialog?>(null)
    val dialog = _dialog.asStateFlow()

    private val _nameError = MutableStateFlow<CategoryNameError?>(null)
    val nameError = _nameError.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()

    fun showAdd() = open(CategoryDialog.Add)
    fun showRename(c: CategoryEntity) = open(CategoryDialog.Rename(c))
    fun showDelete(c: CategoryEntity) = viewModelScope.launch {
        open(CategoryDialog.Delete(c, repo.usageCount(c.id)))
    }

    fun dismiss() = open(null)
    fun messageShown() { _message.value = null }

    fun save(name: String) = viewModelScope.launch {
        try {
            when (val d = _dialog.value) {
                is CategoryDialog.Rename -> repo.rename(d.category.id, name)
                else -> repo.add(name)
            }
            dismiss()
        } catch (e: CategoryNameException) {
            _nameError.value = e.error
        }
    }

    fun delete(moveTo: Long?) = viewModelScope.launch {
        val d = _dialog.value as? CategoryDialog.Delete ?: return@launch
        if ((categories.value?.size ?: 0) <= 1) {
            _message.value = R.string.category_cannot_delete_last
            dismiss()
            return@launch
        }
        try {
            repo.delete(d.category.id, moveTo)
            _message.value = R.string.category_deleted
        } catch (e: CategoryInUseException) {
            _message.value = R.string.category_pick_target
        }
        dismiss()
    }

    private fun open(d: CategoryDialog?) {
        _nameError.value = null
        _dialog.value = d
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> CategoriesViewModel(c.categories) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory)) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val nameError by vm.nameError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::showAdd) { Icon(Icons.Default.Add, stringResource(R.string.category_add)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = categories
        if (list == null) {
            LoadingState(Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(list, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { vm.showRename(category) }) {
                                    Icon(Icons.Default.Edit, stringResource(R.string.rename))
                                }
                                IconButton(onClick = { vm.showDelete(category) }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete))
                                }
                            }
                        },
                        modifier = Modifier.clickable { vm.showRename(category) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    when (val d = dialog) {
        CategoryDialog.Add -> NameDialog(stringResource(R.string.category_add), "", nameError, vm::save, vm::dismiss)
        is CategoryDialog.Rename ->
            NameDialog(stringResource(R.string.rename), d.category.name, nameError, vm::save, vm::dismiss)
        is CategoryDialog.Delete -> DeleteDialog(d, categories.orEmpty(), vm::delete, vm::dismiss)
        null -> Unit
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    error: CategoryNameError?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let {
                    {
                        Text(
                            stringResource(
                                when (it) {
                                    CategoryNameError.EMPTY -> R.string.error_name_empty
                                    CategoryNameError.TOO_LONG -> R.string.error_name_too_long
                                    CategoryNameError.DUPLICATE -> R.string.error_name_duplicate
                                },
                            ),
                        )
                    }
                },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    dialog: CategoryDialog.Delete,
    categories: List<CategoryEntity>,
    onDelete: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val others = categories.filter { it.id != dialog.category.id }
    var target by remember { mutableStateOf<Long?>(null) }
    val inUse = dialog.usage > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_delete_title, dialog.category.name)) },
        text = {
            Column {
                if (inUse) {
                    Text(pluralStringResource(R.plurals.category_in_use, dialog.usage, dialog.usage))
                    LazyColumn(Modifier.padding(top = 8.dp)) {
                        items(others, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = target == c.id, onClick = { target = c.id }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = target == c.id, onClick = { target = c.id })
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.category_delete_unused))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(target) }, enabled = !inUse || target != null) {
                Text(if (inUse) stringResource(R.string.move_and_delete) else stringResource(R.string.delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
