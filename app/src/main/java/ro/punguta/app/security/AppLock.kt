package ro.punguta.app.security

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


enum class LockState { CHECKING, LOCKED, UNLOCKED }

/**
 * App-wide lock. Locks on a cold start and whenever the whole app goes to the background, except
 * while the app itself opened a system screen (file picker, gallery, PIN prompt) and expects to
 * come straight back.
 */
class AppLock(lockEnabled: Flow<Boolean>, scope: CoroutineScope) : DefaultLifecycleObserver {
    private val _state = MutableStateFlow(LockState.CHECKING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile private var enabled = false

    /** Uptime until which a trip to the background is expected and does not lock. */
    @Volatile private var expectReturnUntil = 0L

    init {
        scope.launch {
            lockEnabled.collect { isEnabled ->
                enabled = isEnabled
                when {
                    !isEnabled -> _state.value = LockState.UNLOCKED
                    _state.value == LockState.CHECKING -> _state.value = LockState.LOCKED
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (enabled && SystemClock.uptimeMillis() > expectReturnUntil) _state.value = LockState.LOCKED
    }

    override fun onStart(owner: LifecycleOwner) {
        clearExpectation()
    }

    /**
     * Call right before launching a system screen the user will return from. The exemption
     * expires on return or after a short time, so a later trip to the background still locks.
     */
    fun expectReturn() {
        expectReturnUntil = SystemClock.uptimeMillis() + EXPECT_RETURN_WINDOW_MS
    }

    fun clearExpectation() {
        expectReturnUntil = 0L
    }

    fun unlock() {
        clearExpectation()
        _state.value = LockState.UNLOCKED
    }

    private companion object {
        const val EXPECT_RETURN_WINDOW_MS = 120_000L
    }
}

enum class AuthAvailability { AVAILABLE, NOT_SET_UP, UNAVAILABLE }

/** Fingerprint (or face) with the device PIN, pattern or password as fallback. */
object Biometrics {
    /** Android 8-10 cannot combine strong biometrics with the device credential. */
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    fun availability(context: Context): AuthAvailability =
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.NOT_SET_UP
            else -> AuthAvailability.UNAVAILABLE
        }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        lock: AppLock,
        onSuccess: () -> Unit,
        onError: (code: Int) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lock.clearExpectation()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lock.clearExpectation()
                    onError(errorCode)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        // On older Android versions the PIN screen is a separate system activity.
        lock.expectReturn()
        prompt.authenticate(info)
    }
}
