package at.hannibal2.skyhanni.features.garden.visitor

import at.hannibal2.skyhanni.SkyHanniMod.launch
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.hypixel.chat.event.NpcChatEvent
import at.hannibal2.skyhanni.data.jsonobjects.repo.GardenJson
import at.hannibal2.skyhanni.data.title.TitleManager
import at.hannibal2.skyhanni.events.RepositoryReloadEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.garden.visitor.VisitorArrivalEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ComponentMatcherUtils.matchStyledMatcher
import at.hannibal2.skyhanni.utils.ComponentSpan
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.SkyHanniLogger
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.compat.componentBuilder
import at.hannibal2.skyhanni.utils.compat.formattedTextCompatLessResets
import at.hannibal2.skyhanni.utils.coroutines.CoroutineSettings
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.player.RemotePlayer
import kotlin.time.Duration.Companion.seconds

/**
 * Handles chat filtering and visitor arrival notifications.
 * Filters spam messages and sends titles/chat messages when visitors arrive.
 */
@SkyHanniModule
object GardenVisitorChat {

    private val config get() = VisitorApi.config
    private val logger = SkyHanniLogger("garden/visitors/chat")

    private val patternGroup = RepoPattern.group("garden.visitor.chat")
    private var allowedChatMessageVisitors: Set<String> = emptySet()

    /**
     * REGEX-TEST: Banker Broadjaw has arrived on your Garden!
     */
    private val visitorArrivePattern by patternGroup.pattern(
        "visitorarrive",
        ".+ has arrived on your Garden!",
    )

    /**
     * REGEX-TEST: §e[NPC] §6Madame Eleanor Q. Goldsworth III
     * REGEX-TEST: §e[NPC] §aRhys
     */
    private val visitorChatMessagePattern by patternGroup.pattern(
        "visitorchat.author",
        "§e\\[NPC] (?<color>§.)?(?<name>.+)",
    )

    /**
     * REGEX-TEST: You gave some of the required items!
     */
    private val partialAcceptedPattern by patternGroup.pattern(
        "partialaccepted",
        "You gave some of the required items!",
    )

    private val repoReloadCoroutine = CoroutineSettings("allowed chat message visitors repo reload")

    @HandleEvent
    fun onRepoReload(event: RepositoryReloadEvent) = repoReloadCoroutine.launch {
        val data = event.getConstantAsync<GardenJson>("Garden")
        allowedChatMessageVisitors = data.visitors.filterValues { visitorData -> visitorData.showChatMessage }.keys.toSet()
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onChat(event: SkyHanniChatEvent.Allow) {
        if (handleArrivalMessage(event)) return
        if (handlePartialAccepted(event)) return
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onNpcChat(event: NpcChatEvent.Allow) {
        if (handleVisitorMessage(event)) return
    }

    /**
     * Blocks the Hypixel arrival message if configured.
     */
    private fun handleArrivalMessage(event: SkyHanniChatEvent.Allow): Boolean {
        if (config.hypixelArrivedMessage && visitorArrivePattern.matcher(event.cleanMessage).matches()) {
            event.blockedReason = "new_visitor_arrived"
            return true
        }
        return false
    }

    /**
     * Filters visitor chat messages to reduce spam.
     */
    private fun handleVisitorMessage(event: NpcChatEvent.Allow): Boolean {
        if (config.hideChat && hideVisitorMessage(event.authorComponent)) {
            event.blockedReason = "garden_visitor_message"
            return true
        }
        return false
    }

    /**
     * Reminds user to reopen visitor GUI after partial acceptance.
     */
    private fun handlePartialAccepted(event: SkyHanniChatEvent.Allow): Boolean {
        if (config.shoppingList.enabled) {
            partialAcceptedPattern.matchMatcher(event.cleanMessage) {
                ChatUtils.chat("Talk to the visitor again to update the number of items needed!")
                return true
            }
        }
        return false
    }

    /**
     * Determines if a chat message should be hidden.
     * Hides messages from visitors but keeps messages from permanent NPCs like Jacob.
     */
    private fun hideVisitorMessage(authorComponent: ComponentSpan): Boolean =
        visitorChatMessagePattern.matchStyledMatcher(authorComponent) {
            val color = group("color")?.getText()
            if (color == null || color == "§e") return false // Non-visitor NPC, probably Jacob

            val name = group("name")?.getText()
            if (name == null || name in allowedChatMessageVisitors) return false

            val isInKnownVisitors = VisitorApi.getVisitorsMap().keys.any { it.removeColor() == name }

            return if (isInKnownVisitors) true
            else doesVisitorEntityExist(name)
        } ?: false

    /**
     * Checks if a visitor entity with the given name exists in the barn area.
     * Used as fallback when tab list hasn't updated yet.
     */
    private fun doesVisitorEntityExist(name: String) =
        EntityUtils.getEntitiesInBoundingBox<RemotePlayer>(GardenApi.barnArea).any {
            it.name.formattedTextCompatLessResets().trim().equals(name, true)
        }

    /**
     * Sends chat and title notifications when a visitor arrives.
     * Also triggers status update and item blinking effects.
     */
    @HandleEvent
    fun onVisitorArrival(event: VisitorArrivalEvent) {
        val visitor = event.visitor
        val name = visitor.visitorName

        GardenVisitorStatus.update()

        logger.log("New visitor detected: '$name'")

        if (SkyBlockUtils.lastWorldSwitch.passedSince() < 3.seconds) return

        sendArrivalNotification(visitor)
    }

    /**
     * Sends title and chat notifications based on config.
     */
    private fun sendArrivalNotification(visitor: VisitorApi.Visitor) {
        if (config.notificationTitle) {
            TitleManager.sendTitle("§eNew Visitor")
        }
        if (config.notificationChat) {
            val displayName = GardenVisitorColorNames.getColoredName(visitor.visitorName)
            ChatUtils.chat(
                componentBuilder {
                    append(displayName)
                    append(" is visiting your garden!")
                }
            )
        }
    }
}
