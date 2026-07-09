package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigFileType
import at.hannibal2.skyhanni.data.jsonobjects.local.FriendsJson
import at.hannibal2.skyhanni.data.jsonobjects.local.FriendsJson.PlayerFriends.Friend
import at.hannibal2.skyhanni.events.FriendAddEvent
import at.hannibal2.skyhanni.events.FriendRemoveEvent
import at.hannibal2.skyhanni.events.FriendRequestDeclinedEvent
import at.hannibal2.skyhanni.events.FriendRequestExpiredEvent
import at.hannibal2.skyhanni.events.FriendRequestSentEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.hypixel.HypixelJoinEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.StringUtils.cleanPlayerName
import at.hannibal2.skyhanni.utils.compat.command
import at.hannibal2.skyhanni.utils.compat.hover
import at.hannibal2.skyhanni.utils.compat.takeUnlessEmpty
import at.hannibal2.skyhanni.utils.compat.unformattedTextCompat
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@SkyHanniModule
object FriendApi {
    private val patternGroup = RepoPattern.group("data.friends")

    /**
     * REGEX-TEST: You removed [MVP+] Throwpo from your friends list!
     */
    private val removedFriendPattern by patternGroup.pattern(
        "remove",
        "-*\n?You removed (?<name>.+) from your friends list!\n?-*",
    )

    /**
     * REGEX-TEST: You are now friends with [MVP+] Throwpo
     */
    private val addedFriendPattern by patternGroup.pattern(
        "add",
        "You are now friends with (?<name>.+)",
    )

    /**
     * REGEX-TEST: [MVP+] hannibal2 is no longer a best friend!
     */
    private val noBestFriendPattern by patternGroup.pattern(
        "removebest",
        "-*\n?(?<name>.+) is no longer a best friend!\n?-*",
    )

    /**
     * REGEX-TEST: [MVP+] hannibal2 is now a best friend!
     */
    private val bestFriendPattern by patternGroup.pattern(
        "addbest",
        "-*\n?(?<name>.+) is now a best friend!\n?-*",
    )

    /**
     * REGEX-TEST: Friends (Page 1 of 10)
     */
    private val friendListPattern by patternGroup.pattern(
        "friendlist",
        "-*\n.*Friends \\(Page \\d+ of \\d+\\).*\n-*",
    )

    /**
     * REGEX-TEST: Click here to view Throwpo's profile
     */
    private val rawNamePattern by patternGroup.pattern(
        "rawname",
        "\n§eClick here to view §.(?<name>.+)§e's profile",
    )

    /**
     * REGEX-TEST: /viewprofile 503450fc-72c2-4e87-8243-94e264977437
     */
    private val readFriendListPattern by patternGroup.pattern(
        "readfriends",
        "/viewprofile (?<uuid>.+)",
    )

    /**
     * REGEX-TEST: The friend request to ouppy has expired.
     */
    private val friendRequestExpiredPattern by patternGroup.pattern(
        "friend-request-expired",
        "The friend request to (?<name>.+) has expired.",
    )

    /**
     * REGEX-TEST: You sent a friend request to enbylae! They have 5 minutes to accept it!
     */
    private val friendRequestSentPattern by patternGroup.pattern(
        "friend-request-sent",
        "You sent a friend request to (?<name>.+)! They have 5 minutes to accept it!",
    )

    private val pendingRequests = mutableListOf<String>()
    private val tempFriends = mutableListOf<Friend>()

    private fun getFriends() = SkyHanniMod.friendsData.players.getOrPut(PlayerUtils.getRawUuid()) {
        FriendsJson.PlayerFriends().also { it.friends = mutableMapOf() }
    }.friends

    @HandleEvent
    fun onHypixelJoin(event: HypixelJoinEvent) {
        if (SkyHanniMod.friendsData.players == null) {
            SkyHanniMod.friendsData.players = mutableMapOf()
            saveConfig()
        }
    }

    fun getAllFriends(): List<Friend> {
        val list = mutableListOf<Friend>()
        list.addAll(getFriends().values)
        list.addAll(tempFriends)
        return list
    }

    fun saveConfig() {
        SkyHanniMod.configManager.saveConfig(ConfigFileType.FRIENDS, "Save file")
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent.Allow) {
        friendListPattern.matchMatcher(event.cleanMessage) {
            readFriendsList(event)
            return
        }

        removedFriendPattern.matchMatcher(event.cleanMessage) {
            val name = group("name").cleanPlayerName()
            removedFriend(name)
            return
        }
        addedFriendPattern.matchMatcher(event.cleanMessage) {
            val name = group("name").cleanPlayerName()
            removePendingRequest(name)
            addFriend(name)
            return
        }

        noBestFriendPattern.matchMatcher(event.cleanMessage) {
            val name = group("name").cleanPlayerName()
            setBestFriend(name, false)
            return
        }
        bestFriendPattern.matchMatcher(event.cleanMessage) {
            val name = group("name").cleanPlayerName()
            setBestFriend(name, true)
            return
        }

        friendRequestExpiredPattern.matchMatcher(event.cleanMessage) {
            val name = group("name")
            removePendingRequest(name)
            FriendRequestExpiredEvent(name).post()
            return
        }
        friendRequestSentPattern.matchMatcher(event.cleanMessage) {
            val name = group("name")
            addPendingRequest(name)
            return
        }
    }

    private fun setBestFriend(name: String, bestFriend: Boolean) {
        getFriends().entries.firstOrNull { it.value.name == name }?.let {
            it.value.bestFriend = bestFriend
            saveConfig()
        }
    }

    private fun addFriend(name: String) {
        tempFriends.add(Friend().also { it.name = name })
        FriendAddEvent(name).post()
    }

    private fun removedFriend(name: String) {
        tempFriends.removeIf { it.name == name }
        getFriends().entries.removeIf { it.value.name == name }
        saveConfig()
        FriendRemoveEvent(name).post()
    }

    private fun readFriendsList(event: SkyHanniChatEvent.Allow) {
        for (sibling in event.chatComponent.siblings) {
            val chatStyle = sibling.style.takeUnlessEmpty() ?: continue
            val value = sibling.command ?: continue

            val uuid = readFriendListPattern.matchMatcher(value) {
                group("uuid")?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        ErrorManager.logErrorWithData(
                            e, "Error reading friend list.",
                            "raw uuid" to it,
                            "value" to value,
                            "chatStyle" to chatStyle,
                            "event.chatComponent" to event.chatComponent,
                            "event.cleanMessage" to event.cleanMessage,
                        )
                        return
                    }
                }
            }
            val bestFriend = sibling.unformattedTextCompat().split(" ").firstOrNull()?.contains("§l") ?: false
            val name = readName(sibling)
            if (uuid != null && name != null) {
                getFriends()[uuid] = Friend().also {
                    it.name = name
                    it.bestFriend = bestFriend
                }
            }
        }

        saveConfig()
    }

    private fun readName(chatComponent: Component): String? {
        val hoverEventSiblings = chatComponent.hover?.siblings ?: return null
        for (component in hoverEventSiblings) {
            val rawName = component.unformattedTextCompat()
            rawNamePattern.matchMatcher(rawName) {
                return group("name").cleanPlayerName()
            }
        }

        return null
    }

    private fun addPendingRequest(name: String) {
        pendingRequests.add(name)

        // This is 6 minutes instead of 5 minutes to be extra sure that the request has expired
        DelayedRun.runDelayed(6.minutes) {
            val stillPending = pendingRequests.contains(name)
            if (!stillPending) return@runDelayed

            removePendingRequest(name)
            if (!SkyBlockUtils.onHypixel) return@runDelayed
            FriendRequestDeclinedEvent(name).post()
        }

        FriendRequestSentEvent(name).post()
    }

    private fun removePendingRequest(name: String) {
        pendingRequests.removeIf { it == name }
    }
}
