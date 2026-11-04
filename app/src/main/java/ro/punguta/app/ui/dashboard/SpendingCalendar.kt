package ro.punguta.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ro.punguta.app.R
import ro.punguta.app.domain.money.Money
import ro.punguta.app.domain.stats.DaySpending
import ro.punguta.app.domain.stats.MonthCalendar
import ro.punguta.app.ui.components.AppCard
import ro.punguta.app.ui.components.CardRow
import ro.punguta.app.ui.components.CardTitle
import ro.punguta.app.ui.components.RO
import ro.punguta.app.ui.theme.LocalMoneyPalette
import ro.punguta.app.ui.theme.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", RO)

/**
 * Month grid with what was spent each day. Busier days take a stronger coral tint, relative to
 * the month's most expensive day; tapping a day lists its expenses underneath.
 */
@Composable
fun SpendingCalendar(calendar: MonthCalendar, onExpense: (Long) -> Unit, today: LocalDate = LocalDate.now()) {
    var selected by rememberSaveable(calendar.month) {
        mutableStateOf(if (YearMonth.from(today) == calendar.month) today.dayOfMonth else null)
    }
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
            CardTitle(stringResource(R.string.calendar_title))
            Text(
                stringResource(R.string.calendar_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        WeekdayHeader()
        val cells = calendar.leadingBlanks + calendar.days.size
        val max = calendar.maxDayBani
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val index = week * 7 + column - calendar.leadingBlanks
                    val day = calendar.days.getOrNull(index)
                    Box(Modifier.weight(1f).padding(2.dp)) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                max = max,
                                isToday = day.date == today,
                                isFuture = day.date.isAfter(today),
                                isSelected = selected == day.date.dayOfMonth,
                                onClick = { selected = day.date.dayOfMonth },
                            )
                        }
                    }
                }
            }
        }
        Legend()
        selected?.let { calendar.days.getOrNull(it - 1) }?.let { DayDetails(it, onExpense) }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        DayOfWeek.entries.forEach { dow ->
            Text(
                dow.getDisplayName(TextStyle.SHORT, RO).trimEnd('.').replaceFirstChar { it.titlecase(RO) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    day: DaySpending,
    max: Long,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalMoneyPalette.current.accent
    val heat = if (max > 0 && day.bani > 0) 0.14f + 0.5f * (day.bani.toFloat() / max) else 0f
    val background by animateColorAsState(
        when {
            isSelected -> scheme.primary
            heat > 0f -> accent.copy(alpha = heat)
            else -> Color.Transparent
        },
        tween(160),
        label = "dayCell",
    )
    val content = when {
        isSelected -> scheme.onPrimary
        isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> scheme.onSurface
    }
    val shape = RoundedCornerShape(14.dp)
    val description = stringResource(
        R.string.calendar_day_description,
        day.date.format(dayTitleFormat),
        Money.format(day.bani),
    )
    Surface(
        color = background,
        contentColor = content,
        shape = shape,
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(shape)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
            Text(
                if (day.bani > 0) Money.formatCompact(day.bani) else "",
                style = MaterialTheme.typography.labelSmall.tabular.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/** Three tint steps from "less" to "more", so the shading reads without explanation. */
@Composable
private fun Legend() {
    val accent = LocalMoneyPalette.current.accent
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, end = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.calendar_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        listOf(0.18f, 0.4f, 0.64f).forEach { alpha ->
            Surface(Modifier.padding(horizontal = 2.dp).size(14.dp), shape = RoundedCornerShape(4.dp), color = accent.copy(alpha = alpha)) {}
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.calendar_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetails(day: DaySpending, onExpense: (Long) -> Unit) {
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    CardRow(
        title = day.date.format(dayTitleFormat).replaceFirstChar { it.titlecase(RO) },
        trailing = { Text(Money.format(day.bani), style = MaterialTheme.typography.titleMedium.tabular) },
    )
    if (day.expenses.isEmpty()) {
        Text(
            stringResource(R.string.calendar_day_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
    day.expenses.forEach { e ->
        CardRow(
            title = e.title,
            trailing = { Text(Money.format(e.bani), style = MaterialTheme.typography.titleSmall.tabular) },
            onClick = { onExpense(e.expenseId) },
        )
    }
}
