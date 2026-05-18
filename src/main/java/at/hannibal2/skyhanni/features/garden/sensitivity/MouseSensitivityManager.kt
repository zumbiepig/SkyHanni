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
    fun remapSensitivity(sens: Double): Double {
        return state.get()
    }

    @HandleEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Mouse Sensitivity")
        event.addData {
            add("current state: $state")
        }
    }

    enum class SensitivityState(modifier: (() -> Float)) {
        UNCHANGED({ 1 }),
        REDUCED({ config.reducingFactor.get() }),
        LOCKED({ 0 }),
        ;

        val modifier get() = modifier()
        fun isActive() = state == this
        fun setActive() = if (state != this) state = this
    }
}
