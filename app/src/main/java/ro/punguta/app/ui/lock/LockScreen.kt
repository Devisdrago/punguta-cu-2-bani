package ro.punguta.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import ro.punguta.app.R
import ro.punguta.app.ui.components.BadgeTone
import ro.punguta.app.ui.components.IconBadge
import ro.punguta.app.ui.components.PillButton
import ro.punguta.app.ui.components.PillKind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import ro.punguta.app.security.AppLock
import ro.punguta.app.security.AuthAvailability
import ro.punguta.app.security.Biometrics

/** Opaque cover shown over the app while it is locked. */
@Composable
fun LockScreen(lock: AppLock, onDisableLock: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val availability = remember { Biometrics.availability(activity) }
    var message by remember { mutableStateOf<Int?>(null) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    val prompt = {
        message = null
        Biometrics.authenticate(
            activity, title, subtitle, lock,
            onSuccess = lock::unlock,
            onError = { code ->
                message = when (code) {
                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> R.string.lock_error_lockout
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    -> null
                    else -> R.string.lock_error_generic
                }
            },
        )
    }

    LaunchedEffect(Unit) { if (availability == AuthAvailability.AVAILABLE) prompt() }
    BackHandler { activity.moveTaskToBack(true) }

    Surface(
        Modifier.fillMaxSize()
            // Swallow every touch so nothing underneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                stringResource(if (availability == AuthAvailability.AVAILABLE) R.string.lock_locked else R.string.lock_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconBadge(
                    if (availability == AuthAvailability.AVAILABLE) Icons.Outlined.Fingerprint else Icons.Outlined.Lock,
                    if (availability == AuthAvailability.AVAILABLE) BadgeTone.ACCENT else BadgeTone.NEUTRAL,
                    size = 96.dp,
                )
                message?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
            if (availability == AuthAvailability.AVAILABLE) {
                PillButton(stringResource(R.string.lock_unlock), prompt, icon = Icons.Outlined.Fingerprint)
            } else {
                PillButton(stringResource(R.string.lock_disable_continue), onDisableLock, kind = PillKind.SECONDARY)
            }
        }
    }
}
