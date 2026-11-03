package de.jtracht.piimager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortModelNameTest {
    @Test fun dropsTheBrandPrefix() {
        assertEquals("Pi 5", shortModelName("Raspberry Pi 5"))
        assertEquals("Pi Zero 2 W", shortModelName("Raspberry Pi Zero 2 W"))
    }

    @Test fun keepsOtherNamesUnchanged() {
        assertEquals("Compute Module 4", shortModelName("Compute Module 4"))
    }
}
