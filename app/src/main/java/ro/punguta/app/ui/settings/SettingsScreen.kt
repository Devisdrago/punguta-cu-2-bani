package ro.punguta.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import ro.punguta.app.PungutaApp
import ro.punguta.app.R
import ro.punguta.app.security.AuthAvailability
import ro.punguta.app.security.Biometrics
import ro.punguta.app.ui.components.formatDate
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCategories: () -> Unit,
    onBudgets: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val lock = (context.applicationContext as PungutaApp).container.appLock
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val lastExport by vm.lastExport.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var availability by remember { mutableStateOf(Biometrics.availability(context)) }
    var lockError by remember { mutableStateOf<Int?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { availability = Biometrics.availability(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect(uri)
    }

    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.messageShown()
        }
    }

    val promptTitle = stringResource(R.string.lock_enable_prompt)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.categories)) },
                supportingContent = { Text(stringResource(R.string.categories_hint)) },
                leadingContent = { Icon(Icons.Default.Category, null) },
                modifier = Modifier.clickable(onClick = onCategories),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.budgets)) },
                supportingContent = { Text(stringResource(R.string.budgets_hint)) },
                leadingContent = { Icon(Icons.Default.Savings, null) },
                modifier = Modifier.clickable(onClick = onBudgets),
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.security))
            ListItem(
                headlineContent = { Text(stringResource(R.string.lock_setting)) },
                supportingContent = {
                    Text(
                        stringResource(
                            when {
                                lockError != null -> lockError!!
                                availability == AuthAvailability.AVAILABLE -> R.string.lock_setting_hint
                                availability == AuthAvailability.NOT_SET_UP -> R.string.lock_not_set_up
                                else -> R.string.lock_unsupported
                            },
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Default.Fingerprint, null) },
                trailingContent = {
                    Switch(
                        checked = lockEnabled,
                        enabled = lockEnabled || availability == AuthAvailability.AVAILABLE,
                        onCheckedChange = { enable ->
                            lockError = null
                            if (!enable) {
                                vm.setLock(false)
                            } else {
                                // Prove it works before turning it on, so nobody gets locked out.
                                Biometrics.authenticate(
                                    activity, promptTitle, promptSubtitle, lock,
                                    onSuccess = { vm.setLock(true) },
                                    onError = { code ->
                                        if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_CANCELED) {
                                            lockError = R.string.lock_error_generic
                                        }
                                    },
                                )
                            }
                        },
                    )
                },
            )
            if (availability == AuthAvailability.NOT_SET_UP && !lockEnabled) {
                TextButton(
                    onClick = {
                        lock.expectReturn()
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.lock_open_security_settings)) }
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.backup))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Warning, null)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.backup_warning_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.backup_warning_body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                lastExport?.let {
                    stringResource(R.string.backup_last_export, formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()))
                } ?: stringResource(R.string.backup_never_exported),
                style = MaterialTheme.typography.bodySmall,
                color = if (lastExport == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_hint)) },
                leadingContent = { Icon(Icons.Default.Upload, null) },
                modifier = Modifier.clickable(enabled = backup !is BackupUi.Working) {
                    lock.expectReturn()
                    exportLauncher.launch(vm.suggestedFileName())
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_restore)) },
                supportingContent = { Text(stringResource(R.string.backup_restore_hint)) },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier.clickable(enabled = backup !is BackupUi.Working) {
                    lock.expectReturn()
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            )
            if (backup is BackupUi.Working) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    when (val b = backup) {
        is BackupUi.ConfirmRestore -> AlertDialog(
            onDismissRequest = vm::dismiss,
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_message,
                        b.summary.createdAt.replace('T', ' '),
                        b.summary.incomes,
                        b.summary.expenses,
                        b.summary.templates,
                        b.summary.categories,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmRestore) {
                    Text(stringResource(R.string.backup_confirm_replace), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is BackupUi.Problem -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(stringResource(b.message)) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } },
        )
        else -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

