package at.hannibal2.skyhanni.test.garden

import at.hannibal2.skyhanni.features.garden.sensitivity.MouseSensitivityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensitivityReducerTest {

    @Test
    fun `locked sensitivity maps to exact zero`() {
        try {
            MouseSensitivityManager.SensitivityState.UNCHANGED.setActive()
            assertEquals(1.0, MouseSensitivityManager.remapSensitivity(1.0))

            MouseSensitivityManager.SensitivityState.LOCKED.setActive()
            assertEquals(0.0, MouseSensitivityManager.remapSensitivity(1.0))
        } finally {
            MouseSensitivityManager.SensitivityState.UNCHANGED.setActive()
        }
    }
}
