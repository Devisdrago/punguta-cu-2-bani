package ro.punguta.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ro.punguta.app.R
import ro.punguta.app.data.db.CategoryEntity
import ro.punguta.app.ui.theme.LocalMoneyPalette
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val RO: Locale = Locale("ro", "RO")
private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", RO)
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", RO)
private const val MILLIS_PER_DAY = 86_400_000L

fun formatMonth(month: YearMonth): String =
    month.format(monthFormat).replaceFirstChar { it.titlecase(RO) }

fun formatDate(date: LocalDate): String = date.format(dateFormat)

fun formatEpochDay(epochDay: Long): String = formatDate(LocalDate.ofEpochDay(epochDay))

/** Month name only ("Noiembrie"), for the dashboard's headline. */
fun monthName(month: YearMonth): String =
    month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, RO).replaceFirstChar { it.titlecase(RO) }

/** Round previous/next month buttons for a [ScreenHeader]'s actions. */
@Composable
fun MonthNavButtons(onPrevious: () -> Unit, onNext: () -> Unit) {
    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.month_previous), onPrevious)
    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.month_next), onNext)
}

/** Chip under a header that jumps back to the current month; hidden when already there. */
@Composable
fun BackToCurrentMonth(month: YearMonth, onCurrent: () -> Unit) {
    if (month == YearMonth.now()) return
    TextButton(onClick = onCurrent, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Default.Today, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.month_current))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(icon, BadgeTone.NEUTRAL, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier) {
    EmptyState(Icons.Default.ErrorOutline, stringResource(R.string.error_title), message, modifier)
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Amount input in lei; the caller parses the text with Money.parse. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    highlight: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        suffix = { Text("lei") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = supportingText?.let { { Text(it) } },
        colors = highlightColors(highlight),
        modifier = modifier,
    )
}

@Composable
fun highlightColors(highlight: Boolean): androidx.compose.material3.TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return if (highlight) {
        // Uncertain OCR value: an accent-tinted field the eye finds before saving.
        val tint = LocalMoneyPalette.current.accent
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = tint,
            focusedBorderColor = scheme.primary,
            unfocusedContainerColor = scheme.secondaryContainer.copy(alpha = 0.55f),
            focusedContainerColor = scheme.secondaryContainer.copy(alpha = 0.55f),
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = scheme.outlineVariant,
            focusedBorderColor = scheme.primary,
            unfocusedContainerColor = scheme.surface,
            focusedContainerColor = scheme.surface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.category),
    highlight: Boolean = false,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = highlightColors(highlight),
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = formatDate(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        colors = highlightColors(highlight),
        trailingIcon = {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Default.CalendarMonth, stringResource(R.string.pick_date))
            }
        },
        singleLine = true,
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(LocalDate.ofEpochDay(it / MILLIS_PER_DAY)) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}


/** White pill with the month between previous/next arrows, for list screens. */
@Composable
fun MonthPill(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onCurrent: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.month_previous))
                }
                Text(
                    formatMonth(month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.month_next))
                }
            }
        }
        BackToCurrentMonth(month, onCurrent)
    }
}
