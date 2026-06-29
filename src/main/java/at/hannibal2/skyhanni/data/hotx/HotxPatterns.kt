package at.hannibal2.skyhanni.data.hotx

import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object HotxPatterns {

    private val patternGroup = RepoPattern.group("misc.hotx")

    /**
     * REGEX-TEST: New buff: Gain +5% ∮ Sweep.
     * REGEX-TEST: New buff: Gain +50 ☘ Mangrove Fortune.
     * REGEX-TEST: New buff: Gain +50 ☘ Fig Fortune.
     *
     * REGEX-TEST: New buff: Gain +50☘ Mining Fortune.
     */
    val chatRotatingPerkPattern by patternGroup.pattern(
        "perk.chat.generic",
        "New buff: (?<perk>.+)",
    )

    /**
     * REGEX-TEST: §8 ■ §7Gain §a+5% §2∮ Sweep§7.
     * REGEX-TEST: §8 ■ §7Gain §a+50 §6☘ Mangrove Fortune§7.
     * REGEX-TEST: §8 ■ §7Gain §a+50 §6☘ Fig Fortune§7
     *
     * REGEX-TEST: §8 ■ §7Gain §6+100⸕ Mining Speed§7.
     * REGEX-TEST: §8 ■ §7Gain §6+50☘ Mining Fortune§7.
     * REGEX-TEST: §8 ■ §7Gain §a+15% §7more Powder while mining.
     * REGEX-TEST: §8 ■ §7§a-20%§7 Pickaxe Ability cooldowns.
     * REGEX-TEST: §8 ■ §7§a10x §7chance to find Golden and
     * REGEX-TEST: §8 ■ §7Gain §a5x §9Titanium §7drops.
     */
    val itemRotatingPerkPattern by patternGroup.pattern(
        "perk.item.generic",
        "§8 ■ §7(?<perk>.+)",
    )

    /**
     * REGEX-TEST: §aYour Current Effect
     */
    // The line that appears before the "current" perk effect in the item tooltip.
    val itemPreEffectPattern by patternGroup.pattern(
        "perk.item.before",
        "§aYour Current Effect"
    )

    fun Enum<*>.asPatternId(): String = name.lowercase().replace("_", ".")
}
