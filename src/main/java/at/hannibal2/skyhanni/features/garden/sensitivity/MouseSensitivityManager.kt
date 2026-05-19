package at.hannibal2.skyhanni.features.garden.sensitivity

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule

@SkyHanniModule
object MouseSensitivityManager {

    private val config get() = SkyHanniMod.feature.garden.sensitivityReducer

    private var state: SensitivityState = SensitivityState.UNCHANGED

    @JvmStatic
    fun remapSensitivity(original: Double): Double {
        return original * state.getFactor()
    }

    @HandleEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Mouse Sensitivity")
        event.addData {
            add("current state: $state")
        }
    }

    enum class SensitivityState(val getFactor: () -> Double) {
        UNCHANGED({ 1.0 }),
        REDUCED({ config.reducingFactor.get().toDouble() }),
        LOCKED({ 0.0 }),
        ;

        fun isActive() = state == this
        fun setActive() {
            state = this
        }
    }
}
