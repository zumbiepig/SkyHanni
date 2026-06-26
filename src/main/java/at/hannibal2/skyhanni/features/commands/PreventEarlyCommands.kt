package at.hannibal2.skyhanni.features.commands

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.hypixel.chat.event.SystemMessageEvent
import at.hannibal2.skyhanni.events.MessageSendToServerEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.utils.compat.append
import at.hannibal2.skyhanni.utils.compat.withColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import at.hannibal2.skyhanni.utils.roundedUpSeconds
import net.minecraft.ChatFormatting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object PreventEarlyCommands {

    private val MESSAGE_ID = ChatUtils.getUniqueMessageId()

    private val config get() = SkyHanniMod.feature.misc.commands

    private var lastCommand: String? = null
    private var action: (() -> Unit)? = null
    private var actionId: Int = 0

    /**
     * REGEX-TEST: You may only use this command after 4s on the server!
     */
    private val cooldownPattern by RepoPattern.pattern(
        "commands.cooldown",
        "You may only use this command after 4s on the server!",
    )

    /**
     * REGEX-TEST: Couldn't warp you! Try again later. (PLAYER_TRANSFER_COOLDOWN)
     * REGEX-FAIL: Unable to use this launch pad at the moment, please try again later. (PLAYER_TRANSFER_COOLDOWN)
     */
    private val transferCooldownPattern by RepoPattern.pattern(
        "commands.transfercooldown",
        "Couldn't warp you! Try again later\\. \\(PLAYER_TRANSFER_COOLDOWN\\)",
    )

    @HandleEvent
    fun onWorldChange() {
        // invalidate scheduled commands
        lastCommand = null
        action = null
        actionId++
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onMessageSendToServer(event: MessageSendToServerEvent) {
        if (!event.isCommand) return
        lastCommand = event.message
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onSystemMessage(event: SystemMessageEvent.Allow) {
        val command = lastCommand ?: return

        if (config.preventEarlyExecution)
            cooldownPattern.matchMatcher(event.cleanMessage) {
                lastCommand = null
                event.blockedReason = "prevent_early_command"
                handleEarlyCommand(command)
                return
            }

        if (config.transferCooldown)
            transferCooldownPattern.matchMatcher(event.cleanMessage) {
                lastCommand = null
                event.blockedReason = "fix_transfer_cooldown"
                handleTransferCooldown(command)
                return
            }
    }

    private fun handleEarlyCommand(command: String) {
        val untilCooldown = (SkyBlockUtils.lastWorldSwitch + 4.seconds).timeUntil().coerceAtLeast(Duration.ZERO)
        runAfterDelay(command, untilCooldown)
    }

    private fun handleTransferCooldown(command: String) {
        val untilCooldown = (SkyBlockUtils.lastWorldSwitch + 3.seconds).timeUntil()

        if (untilCooldown < 2.seconds) runAfterDelay(command, 2.seconds)
        else runAfterDelay(command, untilCooldown)
    }

    private fun runAfterDelay(command: String, delay: Duration) {
        action = { ChatUtils.sendMessageToServer(command) }
        val id = ++actionId
        DelayedRun.runDelayed(delay) {
            if (actionId != id) return@runDelayed // this is a different command, dont run it yet

            action?.invoke()
            action = null
        }

        ChatUtils.chat(messageId = MESSAGE_ID) {
            withColor(ChatFormatting.RED)
            append("Cannot execute ")
            append(command) {
                withColor(ChatFormatting.YELLOW)
            }
            append(" yet. ")
            append("Running it in ${StringUtils.pluralize(delay.roundedUpSeconds, "second", withNumber = true)}.") {
                withColor(ChatFormatting.GREEN)
            }
        }
    }
}
