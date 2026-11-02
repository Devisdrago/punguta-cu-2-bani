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
    ) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            if (availability == AuthAvailability.AVAILABLE) {
                Text(stringResource(R.string.lock_locked), textAlign = TextAlign.Center)
                message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Button(onClick = prompt, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, null)
                    Text(stringResource(R.string.lock_unlock), Modifier.padding(start = 8.dp))
                }
            } else {
                // The phone's screen lock was removed after enabling the app lock.
                Text(stringResource(R.string.lock_unavailable), textAlign = TextAlign.Center)
                OutlinedButton(onClick = onDisableLock, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lock_disable_continue))
                }
            }
        }
    }
}
