package at.hannibal2.skyhanni.features.garden.sensitivity

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule

@SkyHanniModule
object MouseSensitivityManager {

    private val config get() = SkyHanniMod.feature.garden.sensitivityReducer

    private var state: SensitivityState = SensitivityState.UNCHANGED
        set(value) {
            field = value
            lastOriginal = Float.NaN
            lastReduced = Float.NaN
        }

    private var lastOriginal = Float.NaN
    private var lastReduced = Float.NaN

    fun getSensitivity(original: Float): Float {
        // if sensitivity has not changed, no need to recalculate
        if (lastOriginal != original) {
            lastOriginal = original
            lastReduced = state.transform(original)
        }

        return lastReduced
    }

    @HandleEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Mouse Sensitivity")
        event.addData {
            add("current state: $state")
        }
    }

    enum class SensitivityState(val transform: ((Float) -> Float)) {
        UNCHANGED({ it }),
        REDUCED({ it * config.reducingFactor.get() }),
        LOCKED({ 0 }),
        ;

        fun isActive() = state == this
        fun setActive() = if (state != this) state = this
    }
}
