package at.hannibal2.skyhanni.features.garden.sensitivity

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.features.garden.SensitivityReducerConfig
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.fishing.FishingApi
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.pests.PestApi
import at.hannibal2.skyhanni.features.garden.sensitivity.MouseSensitivityManager.SensitivityState
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.BlockUtils
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils.afterChange
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderable
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import net.minecraft.client.Minecraft
import kotlin.math.roundToInt

@SkyHanniModule
object SensitivityReducer {

    private val config get() = SkyHanniMod.feature.garden.sensitivityReducer
    private val commandMessageId = ChatUtils.getUniqueMessageId()

    private val SQUEAKY_MOUSEMAT = "SQUEAKY_MOUSEMAT".toInternalName()

    private var manualState: SensitivityState? = null
        set(value) {
            field = value
            onTick()
        }

    /**
     * REGEX-TEST: Teleported you to The Barn!
     * REGEX-TEST: Teleported you to Plot - 1!
     * REGEX-TEST: Teleported you to Plot - 20!
     */
    private val gardenTeleportPattern by RepoPattern.pattern(
        "chat.garden.teleport.colorless",
        "Teleported you to .*!",
    )

    /**
     * REGEX-TEST: §7Warping...
     */
    private val warpingPattern by RepoPattern.pattern(
        "data.entity.warping",
        "§7(?:Warping|Warping you to your SkyBlock island|Warping using transfer token|Finding player|Sending a visit request)\\.\\.\\.",
    )

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent.Allow) {
        if (!config.disableOnTeleport.get()) return
        val state = manualState ?: return
        if (event.chatComponent.let { gardenTeleportPattern.matches(it) || warpingPattern.matches(it) }) {
            val text = if (state == SensitivityState.REDUCED) "sensitivity has been restored" else "rotation has been unlocked"
            manualState = null
            ChatUtils.notifyOrDisable("§bMouse $text because you teleported.", config::disableOnTeleport, messageId = commandMessageId)
        }
    }


    @HandleEvent
    fun onWorldChange() {
        manualState = null
    }

    @HandleEvent
    fun onTick() {
        manualState?.let {
            it.setActive()
            return
        }

        if (!shouldAutoReduce()) SensitivityState.UNCHANGED.setActive()
        else if (!config.lockMouse.get()) SensitivityState.REDUCED.setActive()
        else SensitivityState.LOCKED.setActive()
    }

    private fun shouldAutoReduce(): Boolean {
        if (!GardenApi.inGarden() || !config.enabled.get()) return false

        if (config.mode.none {
            when (it) {
                SensitivityReducerConfig.Mode.TOOL -> GardenApi.toolInHand != null
                SensitivityReducerConfig.Mode.FISHING_ROD -> FishingApi.holdingRod
                SensitivityReducerConfig.Mode.KEYBIND -> config.keybind.isKeyHeld() && Minecraft.getInstance().screen == null
                SensitivityReducerConfig.Mode.MOUSEMAT -> GardenApi.itemInHand?.getInternalName() == SQUEAKY_MOUSEMAT
                SensitivityReducerConfig.Mode.VACUUM -> PestApi.hasVacuumInHand()
                SensitivityReducerConfig.Mode.SPRAYONATOR -> PestApi.hasSprayonatorInHand()
            }
        }) return false

        if (config.onlyPlot.get() && GardenApi.onUnfarmablePlot) return false

        if (config.onGround.get()) {
            // explanation: player is onGround, tolerance > 0, player isnt flying, raycast down
            val tolerance = config.onGroundTolerance.get()
            if (!PlayerUtils.onGround() && (tolerance == 0f || PlayerUtils.isFlying() || PlayerUtils.getLocation().let {
                    // return if miss != false, miss == false means the raycast hit a block (player is close to ground)
                    BlockUtils.raycast(it, it.down(tolerance))?.miss != false
                })) return false
        }

        return true
    }

    @HandleEvent
    fun onConfigLoad() {
        config.reducingFactor.afterChange {
            val coerced = coerceIn(1f..100f)
            if (this != coerced) {
                config.reducingFactor.set(coerced)
                ChatUtils.debug("SensitivityReducer: Fixed invalid reducingFactor ($this -> $coerced)")
            }
        }
        config.onGroundTolerance.afterChange {
            val coerced = coerceIn(0f..2f)
            if (this != coerced) {
                config.onGroundTolerance.set(coerced)
                ChatUtils.debug("SensitivityReducer: Fixed invalid onGroundTolerance ($this -> $coerced)")
            }
        }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("shsensreduce") {
            description = "Lowers the mouse sensitivity for easier small adjustments (for farming)"
            category = CommandCategory.USERS_ACTIVE
            simpleCallback {
                if (manualState != SensitivityState.REDUCED) {
                    manualState = SensitivityState.REDUCED
                    ChatUtils.chat(
                        "§bMouse sensitivity is now lowered. Type /shsensreduce to restore your sensitivity.",
                        messageId = commandMessageId,
                    )
                } else {
                    manualState = null
                    ChatUtils.chat("§bMouse sensitivity is now restored.", messageId = commandMessageId)
                }
            }
        }
        event.registerBrigadier("shmouselock") {
            description = "Lock/Unlock the mouse so it will no longer rotate the player (for farming)"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("shlockmouse")
            simpleCallback {
                if (manualState != SensitivityState.LOCKED) {
                    manualState = SensitivityState.LOCKED
                    ChatUtils.chat("§bMouse rotation is now locked. Type /shlockmouse to unlock your mouse.", messageId = commandMessageId)
                } else {
                    manualState = null
                    ChatUtils.chat("§bMouse rotation is now unlocked.", messageId = commandMessageId)
                }
            }
        }
    }

    @HandleEvent
    fun onGuiRenderOverlay() {
        if (!config.showGui) return

        if (SensitivityState.REDUCED.isActive()) {
            config.position.renderRenderable(
                Renderable.text("§eSensitivity Lowered"),
                posLabel = "Sensitivity Reducer",
            )
        } else if (SensitivityState.LOCKED.isActive()) {
            config.position.renderRenderable(
                Renderable.text("§eMouse Locked"),
                posLabel = "Sensitivity Reducer",
            )
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        val base = "garden.sensitivityReducer"
        event.move(80, "garden.sensitivityReducerConfig", base)
        event.move(81, "$base.showGUI", "$base.showGui")
        event.transform(116, "$base.mode") { element ->
            event.add(116, "$base.enabled") {
                JsonPrimitive(element.asString != "OFF")
            }
            val newList = JsonArray()
            when (element.asString) {
                "OFF" -> newList.add("TOOL")
                else -> newList.add(element.asString)
            }
            newList
        }
        event.transform(134, "$base.reducingFactor") {
            JsonPrimitive((100f / it.asFloat.coerceIn(1f..100f)).roundToInt())
        }
    }
}
