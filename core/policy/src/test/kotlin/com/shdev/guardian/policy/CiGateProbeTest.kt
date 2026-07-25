package com.shdev.guardian.policy

import kotlin.test.Test
import kotlin.test.assertEquals

class CiGateProbeTest {
    @Test
    fun probe() = assertEquals(expected = 1, actual = 2, message = "CI gate probe")
}