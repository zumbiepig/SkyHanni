package at.hannibal2.skyhanni.features.commands

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.inPartialSeconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object TransferCooldown {

    private val config get() = SkyHanniMod.feature.misc.commands

    private var lastRunCompleted: SimpleTimeMark = SimpleTimeMark.farPast()

    @HandleEvent
    fun onWorldChange() {
        // there are several world change events per warp, but the cooldown starts on the first one
        if (lastRunCompleted.isInFuture()) return
        lastRunCompleted = DelayedRun.runDelayed(3.seconds) {
            if (config.transferCooldownMessage && SkyBlockUtils.inSkyBlock)
                ChatUtils.chat("§aPlayer Transfer Cooldown has ended.")
        }
    }
}
