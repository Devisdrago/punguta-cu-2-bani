package ro.punguta.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ro.punguta.app.R
import ro.punguta.app.ui.theme.LocalMoneyPalette

/** Subtle press feedback for anything tappable: scale to 0.97 in 160 ms, back on release. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** White rounded card on the grey ground. Tappable when [onClick] is set. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick == null) {
        Surface(modifier, shape = MaterialTheme.shapes.large, color = color) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.pressScale(interaction),
            shape = MaterialTheme.shapes.large,
            color = color,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Top of a main tab: a big bold title, a quiet subtitle, and round actions on the right. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    /** Side padding; lists that already pad their content pass a smaller value. */
    sidePadding: Dp = 18.dp,
    /** Small actions at the end of the subtitle line, e.g. month arrows. */
    subtitleActions: @Composable RowScope.() -> Unit = {},
    below: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = sidePadding + 2.dp, end = sidePadding, top = 16.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
        if (subtitle != null) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = subtitleActions)
            }
        }
        below?.invoke()
    }
}

/** Top bar for pushed screens: a round back button and a bold title. */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(backIcon, stringResource(R.string.back), onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 48 dp round button: white by default, solid ink for the screen's add action. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (filled) scheme.primary else scheme.surface,
        contentColor = if (filled) scheme.onPrimary else scheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.size(48.dp).pressScale(interaction),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(22.dp)) }
    }
}

enum class PillKind { PRIMARY, SECONDARY, DANGER }

/** Full-width pill button. PRIMARY is the one main action on a screen. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: PillKind = PillKind.PRIMARY,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        PillKind.PRIMARY -> scheme.primary to scheme.onPrimary
        PillKind.SECONDARY -> scheme.surface to scheme.onSurface
        PillKind.DANGER -> scheme.surface to scheme.error
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) container else scheme.surfaceContainerHighest,
        contentColor = if (enabled) content else scheme.onSurfaceVariant,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp).pressScale(interaction),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

enum class BadgeTone { ACCENT, NEUTRAL, INK, INCOME }

/** Round icon tile used at the start of cards and rows. */
@Composable
fun IconBadge(icon: ImageVector, tone: BadgeTone = BadgeTone.NEUTRAL, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    val money = LocalMoneyPalette.current
    val (bg, fg) = when (tone) {
        BadgeTone.ACCENT -> money.accent to Color.White
        BadgeTone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurface
        BadgeTone.INK -> scheme.primary to scheme.onPrimary
        BadgeTone.INCOME -> money.income.copy(alpha = 0.14f) to money.income
    }
    Surface(Modifier.size(size), shape = CircleShape, color = bg, contentColor = fg) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.48f)) }
    }
}

data class SegmentOption(val label: String, val icon: ImageVector? = null)

/**
 * Segmented control: a white card with a solid ink pill behind the selected option.
 * The pill slides between options with a spring; it is the same motion as the bottom bar.
 */
@Composable
fun SegmentedTabs(
    options: List<SegmentOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = scheme.surface) {
        BoxWithConstraints(Modifier.padding(6.dp)) {
            val segment = maxWidth / options.size
            val offset by animateDpAsState(
                segment * selected,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                label = "segment",
            )
            Surface(
                Modifier.width(segment).heightIn(min = 52.dp).offset { IntOffset(offset.roundToPx(), 0) }.fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                color = scheme.primary,
            ) {}
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selected
                    val color by animateColorAsState(
                        if (isSelected) scheme.onPrimary else scheme.onSurface,
                        tween(160),
                        label = "segmentColor",
                    )
                    Column(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp))
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides color) {
                            option.icon?.let { Icon(it, null, Modifier.size(20.dp)) }
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/** Floating pill navigation bar with a soft grey pill that slides to the current tab. */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = scheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BoxWithConstraints(Modifier.padding(6.dp)) {
                val segment = maxWidth / items.size
                val offset by animateDpAsState(
                    segment * selected.coerceAtLeast(0),
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "navPill",
                )
                if (selected >= 0) {
                    Surface(
                        Modifier.width(segment).heightIn(min = 56.dp).offset { IntOffset(offset.roundToPx(), 0) },
                        shape = RoundedCornerShape(26.dp),
                        color = scheme.surfaceContainerHighest,
                    ) {}
                }
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selected
                        val color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant
                        Column(
                            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(26.dp))
                                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(item.icon, null, Modifier.size(22.dp), tint = color)
                            Text(item.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** A row inside a card: optional badge, title and subtitle, optional trailing content. */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
    val clickable = if (onClick != null) {
        base.selectable(selected = false, onClick = onClick, role = Role.Button)
    } else base
    Row(clickable.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Heading inside a card, with an optional action on the right. */
@Composable
fun CardTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Thick rounded progress bar in the accent color, like a slider track. */
@Composable
fun AccentProgress(progress: Float, modifier: Modifier = Modifier, color: Color = LocalMoneyPalette.current.accent) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "progress")
    Box(modifier.fillMaxWidth().heightIn(min = 8.dp).clip(CircleShape)) {
        Surface(Modifier.fillMaxWidth().heightIn(min = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {}
        Surface(Modifier.fillMaxWidth(animated).heightIn(min = 8.dp), color = color, shape = CircleShape) {}
    }
}
