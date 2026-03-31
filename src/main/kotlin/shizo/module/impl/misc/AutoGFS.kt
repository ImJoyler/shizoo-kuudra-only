package shizo.module.impl.misc

import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.module.impl.Module
import shizo.utils.fillItemFromSack
import shizo.utils.handlers.schedule
import shizo.utils.itemId
import shizo.utils.skyblock.LocationUtils
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.kuudra.KuudraUtils

object AutoGFS : Module(
    name = "AutoGFS",
    description = "Automatically refills certain items from your sacks"
) {
    private val inSkyblock by BooleanSetting("In Skyblock", true, desc = "gfs everywhere in skyblock.")
    private val inKuudra by BooleanSetting("In Kuudra", true, desc = "Only gfs in Kuudra.").withDependency { !inSkyblock }
    private val inDungeon by BooleanSetting("In Dungeon", true, desc = "Only gfs in Dungeons.").withDependency { !inSkyblock }

    private val refillOnDungeonStart by BooleanSetting(
        "Refill on Dungeon Start",
        true,
        desc = "Refill when a dungeon starts."
    )
    private val refillOnTimer by BooleanSetting("Refill on Timer", false, desc = "Refill on a timed interval.")
    private val timerIncrements by NumberSetting(
        "Timer Increments",
        5,
        1,
        60,
        1,
        desc = "The interval in which to refill.",
        unit = "s"
    ).withDependency { refillOnTimer }

    private val refillPearl by BooleanSetting("Refill Pearl", true, desc = "Refill ender pearls.")
    private val refillJerry by BooleanSetting("Refill Jerry", true, desc = "Refill inflatable jerrys.")
    private val refillTNT by BooleanSetting("Refill TNT", true, desc = "Refill superboom tnt.")
    private val refillLeap by BooleanSetting("Refill Leaps", false, desc = "Refill spirit leaps.")
    private val autoGetDraft by BooleanSetting("Auto Get Draft", true, desc = "Automatically get draft from the sack.")

    private val puzzleFailRegex = Regex("^PUZZLE FAIL! (\\w{1,16}) .+$|^\\[STATUE] Oruo the Omniscient: (\\w{1,16}) chose the wrong answer! I shall never forget this moment of misrememberance\\.$")
    private val startRegex = Regex("\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.|\\[NPC] Mort: Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!")

    init {
        scheduleRefill()
//
//        on<ChatPacketEvent> {
//            when {
//                value.matches(puzzleFailRegex) -> {
//                    if (!autoGetDraft || DungeonUtils.currentRoom?.data?.type != RoomType.PUZZLE) return@on
//                    if ((PuzzleSolvers as PuzzleSolversAccessor).invokeDraft()) return@on
//
//                    schedule(30) {
//                        modMessage("§7Fetching Draft from sack...")
//                        sendCommand("gfs architect's first draft 1")
//                    }
//                }
//
//                value.matches(startRegex) -> {
//                    if (refillOnDungeonStart) refill()
//                }
//            }
//        }
    }

    private fun scheduleRefill() {
        val delayTicks = timerIncrements * 20
        schedule(delayTicks) {
            if (enabled && refillOnTimer) refill()
            scheduleRefill()
        }
    }

    private fun  refill() {
        if (mc.screen != null) return
        val inventory = mc.player?.inventory ?: return
        if (inSkyblock && !LocationUtils.isInSkyblock) return
        if (!inSkyblock && !((inKuudra && KuudraUtils.inKuudra) || (inDungeon && DungeonUtils.inDungeons))) return

        if (refillLeap) inventory.find { it?.itemId == "SPIRIT_LEAP" }?.also {
            fillItemFromSack(
                16,
                "SPIRIT_LEAP",
                "spirit_leap",
                false
            )
        }
        if (refillPearl) inventory.find { it?.itemId == "ENDER_PEARL" }?.also {
            fillItemFromSack(
                16,
                "ENDER_PEARL",
                "ender_pearl",
                false
            )
        }
        if (refillJerry) inventory.find { it?.itemId == "INFLATABLE_JERRY" }?.also {
            fillItemFromSack(
                64,
                "INFLATABLE_JERRY",
                "inflatable_jerry",
                false
            )
        }
        if (refillTNT) inventory.find { it?.itemId == "SUPERBOOM_TNT" }?.also {
            fillItemFromSack(
                64,
                "SUPERBOOM_TNT",
                "superboom_tnt",
                false
            )
        }
    }
}