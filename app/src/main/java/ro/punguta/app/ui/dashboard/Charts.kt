package ro.punguta.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import ro.punguta.app.R
import ro.punguta.app.domain.stats.CategoryTotal
import ro.punguta.app.domain.stats.MonthTotals
import ro.punguta.app.ui.components.RO
import java.time.format.TextStyle

/** Distinguishable colors for categories; repeats after the end of the list. */
val categoryPalette = listOf(
    Color(0xFF2E7D32), Color(0xFFE6A100), Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF6A1B9A),
    Color(0xFF00838F), Color(0xFFEF6C00), Color(0xFF5D4037), Color(0xFFAD1457), Color(0xFF546E7A),
    Color(0xFF9E9D24), Color(0xFF283593),
)

fun categoryColor(index: Int): Color = categoryPalette[index % categoryPalette.size]

/** Donut chart of spending shares. Drawn directly; display only, so floats are fine here. */
@Composable
fun SpendingDonut(categories: List<CategoryTotal>, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val total = categories.sumOf { it.bani }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.18f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        var start = -90f
        categories.forEachIndexed { i, c ->
            val sweep = 360f * c.bani.toFloat() / total
            drawArc(categoryColor(i), start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable
fun ColorDot(color: Color) {
    Canvas(Modifier.size(12.dp)) { drawCircle(color) }
}

/** Grouped columns: income and spending for each of the recent months. */
@Composable
fun TrendChart(trend: List<MonthTotals>, modifier: Modifier = Modifier) {
    val incomeColor = MaterialTheme.colorScheme.primary
    val spendingColor = MaterialTheme.colorScheme.secondary
    val producer = remember { CartesianChartModelProducer() }
    val labels = trend.map { it.month.month.getDisplayName(TextStyle.SHORT, RO).trimEnd('.') }

    LaunchedEffect(trend) {
        producer.runTransaction {
            columnSeries {
                // Lei as doubles for plotting only; amounts stay integer bani everywhere else.
                series(trend.map { it.incomeBani / 100.0 })
                series(trend.map { it.spendingBani / 100.0 })
            }
        }
    }

    val bottomFormatter = remember(labels) {
        CartesianValueFormatter { _, x, _ -> labels.getOrElse(x.toInt()) { "" } }
    }
    val startFormatter = remember {
        CartesianValueFormatter { _, y, _ ->
            when {
                y >= 1_000_000 -> "%.1fM".format(RO, y / 1_000_000)
                y >= 1_000 -> "%.0fk".format(RO, y / 1_000)
                else -> "%.0f".format(RO, y)
            }
        }
    }

    Column(modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = fill(incomeColor), thickness = 10.dp),
                        rememberLineComponent(fill = fill(spendingColor), thickness = 10.dp),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(incomeColor)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.dash_income), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(16.dp))
            ColorDot(spendingColor)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.dash_spending), style = MaterialTheme.typography.labelMedium)
        }
    }
}
