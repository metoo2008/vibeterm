package dev.vibeterm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HostProfileTest {

    @Test
    fun safePort_clampsOutOfRangeToDefault() {
        assertEquals(22, HostProfile(port = 0).safePort)
        assertEquals(22, HostProfile(port = -1).safePort)
        assertEquals(22, HostProfile(port = 70000).safePort)
        assertEquals(22, HostProfile(port = 65536).safePort)
    }

    @Test
    fun safePort_keepsValidPort() {
        assertEquals(22, HostProfile(port = 22).safePort)
        assertEquals(2222, HostProfile(port = 2222).safePort)
        assertEquals(1, HostProfile(port = 1).safePort)
        assertEquals(65535, HostProfile(port = 65535).safePort)
    }

    @Test
    fun displayName_prefersLabelThenUserHost() {
        assertEquals("myserver", HostProfile(label = "myserver", username = "u", host = "h").displayName)
        assertEquals("u@h", HostProfile(label = "", username = "u", host = "h").displayName)
    }
}
