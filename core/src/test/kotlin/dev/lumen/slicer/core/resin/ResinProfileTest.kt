package dev.lumen.slicer.core.resin

import kotlin.test.Test
import kotlin.test.assertEquals

class ResinProfileTest {

    @Test
    fun `applyTo overlays exposure but keeps motion`() {
        val base = ResinSettings(liftSpeedMmPerMin = 99.0, antiAliasLevels = 4)
        val profile = ResinProfile.byName("Siraya Tech Blu (tough)")!!
        val out = profile.applyTo(base)
        assertEquals(6.0, out.exposureSec)
        assertEquals(40.0, out.bottomExposureSec)
        assertEquals(99.0, out.liftSpeedMmPerMin)      // motion untouched
        assertEquals(4, out.antiAliasLevels)           // unrelated untouched
    }

    @Test
    fun `every built-in is selectable by name`() {
        for (p in ResinProfile.builtIn) assertEquals(p, ResinProfile.byName(p.name))
    }
}
