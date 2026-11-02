package ro.punguta.app.security

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppLockTest {
    private val owner: LifecycleOwner = TestLifecycleOwner()

    @Test
    fun `unlocked when the lock is off`() = runTest(UnconfinedTestDispatcher()) {
        val lock = AppLock(MutableStateFlow(false), backgroundScope)
        assertEquals(LockState.UNLOCKED, lock.state.value)
        lock.onStop(owner)
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun `checking until the setting is known`() = runTest(UnconfinedTestDispatcher()) {
        val setting = MutableStateFlow<Boolean?>(null)
        val lock = AppLock(kotlinx.coroutines.flow.flow { setting.collect { if (it != null) emit(it) } }, backgroundScope)
        assertEquals(LockState.CHECKING, lock.state.value)
        setting.value = true
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `locks on open and again after going to the background`() = runTest(UnconfinedTestDispatcher()) {
        val lock = AppLock(MutableStateFlow(true), backgroundScope)
        assertEquals(LockState.LOCKED, lock.state.value)
        lock.unlock()
        assertEquals(LockState.UNLOCKED, lock.state.value)
        lock.onStop(owner)
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `turning the lock off unlocks, turning it on does not lock immediately`() = runTest(UnconfinedTestDispatcher()) {
        val setting = MutableStateFlow(true)
        val lock = AppLock(setting, backgroundScope)
        setting.value = false
        assertEquals(LockState.UNLOCKED, lock.state.value)
        setting.value = true
        assertEquals(LockState.UNLOCKED, lock.state.value)
        lock.onStop(owner)
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun `an expected trip to a system picker does not lock, but the exemption expires`() = runTest(UnconfinedTestDispatcher()) {
        val lock = AppLock(MutableStateFlow(true), backgroundScope)
        lock.unlock()

        lock.expectReturn()
        lock.onStop(owner)
        assertEquals(LockState.UNLOCKED, lock.state.value)
        lock.onStart(owner)
        lock.onStop(owner) // a later, real trip to the background
        assertEquals(LockState.LOCKED, lock.state.value)

        lock.unlock()
        lock.expectReturn()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        lock.onStop(owner)
        assertEquals(LockState.LOCKED, lock.state.value)
    }
}
