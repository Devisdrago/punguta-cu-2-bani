package ro.punguta.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ro.punguta.app.security.LockState
import ro.punguta.app.ui.AppNav
import ro.punguta.app.ui.lock.LockScreen
import ro.punguta.app.ui.theme.PungutaTheme

/** FragmentActivity because BiometricPrompt requires it. */
class MainActivity : FragmentActivity() {
    /**
     * The UI exists only in Romanian, so resources, plural rules ("2 articole", "20 de articole")
     * and the date picker follow Romanian even on a phone set to another language.
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(Locale("ro", "RO")) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PungutaApp).container
        setContent {
            PungutaTheme {
                val lockState by container.appLock.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    // The app stays composed under the lock so in-progress work (e.g. a receipt
                    // review) survives; it is hidden from touch and accessibility while locked.
                    val hidden = lockState != LockState.UNLOCKED
                    Box(if (hidden) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                        AppNav()
                    }
                    when (lockState) {
                        LockState.CHECKING -> Surface(Modifier.fillMaxSize()) {}
                        LockState.LOCKED -> LockScreen(
                            lock = container.appLock,
                            onDisableLock = {
                                scope.launch { container.settings.setLockEnabled(false) }
                            },
                        )
                        LockState.UNLOCKED -> Unit
                    }
                }
            }
        }
    }
}
