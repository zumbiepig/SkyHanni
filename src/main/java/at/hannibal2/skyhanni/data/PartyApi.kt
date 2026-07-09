package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.hypixel.chat.event.PartyChatEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatchers
import at.hannibal2.skyhanni.utils.StringUtils.cleanPlayerName
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.StringUtils.trimWhiteSpace
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object PartyApi {

    private val patternGroup = RepoPattern.group("data.party")

    /**
     * REGEX-TEST: You have joined [MVP+] Throwpo's party!
     */
    private val youJoinedPartyPattern by patternGroup.pattern(
        "you.joined",
        "You have joined (?<name>.+)'s? party!",
    )

    /**
     * REGEX-TEST: [MVP+] Throwpo joined the party.
     */
    private val othersJoinedPartyPattern by patternGroup.pattern(
        "others.joined",
        "(?<name>.+) joined the party\\.",
    )

    /**
     * REGEX-TEST: You'll be partying with: [VIP] FungalBeatle550
     */
    private val othersInThePartyPattern by patternGroup.pattern(
        "others.inparty",
        "You'll be partying with: (?<names>.+)",
    )

    /**
     * REGEX-TEST: 246sweets has left the party.
     */
    private val otherLeftPattern by patternGroup.pattern(
        "others.left",
        "(?<name>.+) has left the party\\.",
    )

    /**
     * REGEX-TEST: riblets has been removed from the party.
     */
    private val otherKickedPattern by patternGroup.pattern(
        "others.kicked",
        "(?<name>.+) has been removed from the party\\.",
    )

    /**
     * REGEX-TEST: Kicked [MVP+] Throwpo because they were offline.
     */
    private val otherOfflineKickedPattern by patternGroup.pattern(
        "others.offline",
        "Kicked (?<name>.+) because they were offline\\.",
    )

    /**
     * REGEX-TEST: [MVP+] Throwpo was removed from your party because they disconnected.
     */
    private val otherDisconnectedPattern by patternGroup.pattern(
        "others.disconnect",
        "(?<name>.+) was removed from your party because they disconnected\\.",
    )

    /**
     * REGEX-TEST: The party was transferred to [MVP+] CalMWolfs because [MVP+] Throwpo left
     */
    private val transferOnLeavePattern by patternGroup.pattern(
        "others.transfer.leave",
        "The party was transferred to (?<newowner>.+) because (?<name>.+) left",
    )

    /**
     * REGEX-TEST: The party was transferred to [MVP+] Throwpo by [MVP+] CalMWolfs
     */
    val transferVoluntaryPattern by patternGroup.pattern(
        "others.transfer.voluntary",
        "The party was transferred to (?<newowner>.+) by (?<name>.+)",
    )

    /**
     * REGEX-TEST: [MVP+] Throwpo has disbanded the party!
     */
    private val disbandedPattern by patternGroup.pattern(
        "others.disband",
        ".+ has disbanded the party!",
    )

    /**
     * REGEX-TEST: You have been kicked from the party by [MVP+] Throwpo
     */
    private val kickedPattern by patternGroup.pattern(
        "you.kicked",
        "You have been kicked from the party by .+",
    )

    /**
     * REGEX-TEST: You left the party.
     * REGEX-TEST: The party was disbanded because all invites expired and the party was empty.
     * REGEX-TEST: You are not currently in a party.
     * REGEX-TEST: You are not in a party.
     * REGEX-TEST: The party was disbanded because the party leader disconnected.
     * REGEX-TEST: You are not in a party and were moved to the ALL channel.
     */
    private val leftPattern by patternGroup.list(
        "you.left",
        "You left the party\\.",
        "The party was disbanded because all invites expired and the party was empty\\.",
        "You are not currently in a party\\.",
        "You are not in a party\\.",
        "The party was disbanded because the party leader disconnected\\.",
        "You are not in a party and were moved to the ALL channel.",
    )

    /**
     * REGEX-TEST: Party Members (2)
     */
    private val partyMembersStartPattern by patternGroup.pattern(
        "members.start",
        "Party Members \\(\\d+\\)",
    )

    /**
     * REGEX-TEST: Party Members: [MVP+] Throwpo ●
     * REGEX-TEST: Party Leader: [MVP+] CalMWolfs ●
     */
    private val partyMemberListPattern by patternGroup.pattern(
        "members.list.withkind",
        "Party (?<kind>Leader|Moderators|Members): (?<names>.+)",
    )

    /**
     * REGEX-TEST: Party Finder > GhostsTM joined the group! (Combat Level 60)
     */
    private val kuudraFinderJoinPattern by patternGroup.pattern(
        "kuudrafinder.join",
        "Party Finder > (?<name>.+) joined the group! \\(Combat Level \\d+\\)",
    )

    /**
     * REGEX-TEST: Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)
     */
    private val dungeonFinderJoinPattern by patternGroup.pattern(
        "dungeonfinder.join",
        "Party Finder > (?<name>.+) joined the dungeon group! \\((?:Healer|Mage|Berserk|Archer|Tank) Level \\d+\\)",
    )

    val partyMembers = mutableListOf<String>()

    var partyLeader: String? = null
    var prevPartyLeader: String? = null

    fun isInParty() = partyMembers.isNotEmpty()

    private fun listMembers() {
        val size = partyMembers.size
        if (size == 0) {
            ChatUtils.chat("No tracked party members!")
            return
        }
        ChatUtils.chat("Tracked party members §7($size) §f:", prefixColor = "§a")
        for (member in partyMembers) {
            ChatUtils.chat(" §a- §7$member" + if (partyLeader == member) " §a(Leader)" else "", false)
        }

        if (partyLeader == PlayerUtils.getName()) {
            ChatUtils.chat("§aYou are leader")
        }
    }

    @HandleEvent
    fun onPartyChat(event: PartyChatEvent.Allow) {
        val name = event.cleanAuthor
        addPlayer(name)
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent.Allow) {
        val message = event.cleanMessage.trimWhiteSpace()

        // new member joined
        youJoinedPartyPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            partyLeader = name
            addPlayer(name)
            return
        }
        othersJoinedPartyPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            if (partyMembers.isEmpty()) {
                partyLeader = PlayerUtils.getName()
            }
            addPlayer(name)
            return
        }
        othersInThePartyPattern.matchMatcher(message) {
            for (name in group("names").split(", ")) {
                addPlayer(name.cleanPlayerName())
            }
            return
        }
        kuudraFinderJoinPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            addPlayer(name)
            return
        }
        dungeonFinderJoinPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            addPlayer(name)
            return
        }

        // one member got removed
        otherLeftPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            removeWithLeader(name)
            return
        }
        otherKickedPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            removeWithLeader(name)
            return
        }
        otherOfflineKickedPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            removeWithLeader(name)
            return
        }
        otherDisconnectedPattern.matchMatcher(message) {
            val name = group("name").cleanPlayerName()
            partyMembers.remove(name)
            return
        }
        transferOnLeavePattern.matchMatcher(message.removeColor()) {
            val name = group("name").cleanPlayerName()
            partyLeader = group("newowner").cleanPlayerName()
            partyMembers.remove(name)
            return
        }
        transferVoluntaryPattern.matchMatcher(message.removeColor()) {
            partyLeader = group("newowner").cleanPlayerName()
            prevPartyLeader = group("name").cleanPlayerName()
            return
        }

        // party disbanded
        disbandedPattern.matchMatcher(message) {
            partyLeft()
            return
        }
        kickedPattern.matchMatcher(message) {
            partyLeft()
            return
        }
        leftPattern.matchMatchers(message) {
            partyLeft()
            return
        }

        // party list
        partyMembersStartPattern.matchMatcher(message) {
            partyMembers.clear()
            return
        }

        partyMemberListPattern.matchMatcher(message) {
            val kind = group("kind")
            val isPartyLeader = kind == "Leader"
            for (name in group("names").split(" ● ")) {
                val playerName = name.replace(" ●", "").cleanPlayerName()
                addPlayer(playerName)
                if (isPartyLeader) {
                    partyLeader = playerName
                }
            }
            return
        }
    }

    private fun removeWithLeader(name: String) {
        partyMembers.remove(name)
        if (name == prevPartyLeader) {
            prevPartyLeader = null
        }
    }

    private fun addPlayer(playerName: String) {
        if (partyMembers.contains(playerName)) return
        if (playerName == PlayerUtils.getName()) return
        partyMembers.add(playerName)
    }

    private fun partyLeft() {
        partyMembers.clear()
        partyLeader = null
        prevPartyLeader = null
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("shpartydebug") {
            description = "List persons into the chat SkyHanni thinks are in your party."
            category = CommandCategory.DEVELOPER_TEST
            simpleCallback { listMembers() }
        }
    }

    @HandleEvent
    fun onDebugDataCollect(event: DebugDataCollectEvent) {
        event.title("Party")
        event.addIrrelevant {
            val size = partyMembers.size
            if (size == 0) {
                add("No tracked party members!")
            } else {
                add("Tracked party members ($size)")
                for (member in partyMembers) {
                    add(" - $member" + if (partyLeader == member) " (Leader)" else "")
                }
            }

            if (partyLeader == PlayerUtils.getName()) {
                add("")
                add("You are leader")
            }
        }
    }
}
