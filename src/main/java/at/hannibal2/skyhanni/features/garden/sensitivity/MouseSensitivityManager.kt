package at.hannibal2.skyhanni.features.garden.sensitivity

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.features.garden.sensitivity.SensitivityReducer.REDUCING_FACTOR_HARD_BOUNDS
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule

@SkyHanniModule
object MouseSensitivityManager {

    private val config get() = SkyHanniMod.feature.garden.sensitivityReducer

    private var state: SensitivityState = SensitivityState.UNCHANGED

    private var lastIn = Float.NaN
    private var lastOut = Float.NaN

    fun getSensitivity(original: Float): Float {
        if (original != lastIn) {
            lastIn = original
            lastOut = state.apply(original)
        }

        return lastOut
    }

    @HandleEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Mouse Sensitivity")

        if (SensitivityState.UNCHANGED.isActive()) {
            event.addIrrelevant("not enabled")
            return
        }

        event.addData {
            add("current state: $state")
        }
    }

    enum class SensitivityState(private val transform: ((Float) -> Float)) {
        UNCHANGED({ it }),
        REDUCED({ ((it + 1f / 3f) / config.reducingFactor.get().coerceIn(REDUCING_FACTOR_HARD_BOUNDS)) - 1f / 3f }),
        LOCKED({ -1f / 3f }),
        ;

        fun apply(original: Float) = transform(original)
        fun isActive() = state == this
        fun setActive() {
            if (state == this) return
            state = this
            lastIn = Float.NaN
            lastOut = Float.NaN
        }
    }
}
