package ro.punguta.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ro.punguta.app.ui.components.navSlot

class NavSlotTest {
    @Test
    fun `tabs map around the center slot`() {
        assertEquals(0f, navSlot(0f, 4))
        assertEquals(1f, navSlot(1f, 4))
        assertEquals(3f, navSlot(2f, 4)) // slot 2 is the scan button
        assertEquals(4f, navSlot(3f, 4))
    }

    @Test
    fun `swiping between the middle tabs jumps over the center button`() {
        assertEquals(2f, navSlot(1.5f, 4))
        assertEquals(0.5f, navSlot(0.5f, 4))
        assertEquals(3.5f, navSlot(2.5f, 4))
    }
}
