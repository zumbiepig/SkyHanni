package at.hannibal2.skyhanni.features.bingo

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.hypixel.chat.event.Direction
import at.hannibal2.skyhanni.data.hypixel.chat.event.PrivateMessageChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ComponentMatcherUtils.matchStyledMatcher
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object PartyOnBoop {

    private val config get() = SkyHanniMod.feature.misc.boopParty
    private val patternGroup = RepoPattern.group("bingo")

    /**
     * REGEX-TEST: Boop!
     */
    private val boopPattern by patternGroup.pattern(
        "boop.chat",
        "§d§lBoop!",
    )

    @HandleEvent
    fun onPrivateMessageChat(event: PrivateMessageChatEvent.Allow) {
        if (!isEnabled()) return
        if (event.direction == Direction.OUTGOING) return

        boopPattern.matchStyledMatcher(event.messageComponent) {
            val username = event.cleanAuthor
            if (username == PlayerUtils.getName()) return

            ChatUtils.clickableChat(
                "Click to invite $username to the party!",
                onClick = {
                    HypixelCommands.partyInvite(username)
                },
            )
        }
    }

    private fun isEnabled() = (SkyBlockUtils.isBingoProfile && config.boopPartyBingo) || config.boopParty
}
