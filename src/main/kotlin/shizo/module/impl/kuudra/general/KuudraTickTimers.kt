package shizo.module.impl.kuudra.general

import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.ChatPacketEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Colors
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.toFixed

object KuudraTickTimers : Module(
    name = "Kuudra Timers",
    description = "Tick timers for Kuudra phases using server-side ticks."
) {
    private val displayInTicks by BooleanSetting("DisplayInTicks", false, "")
    private val symbolDisplay by BooleanSetting("Display Symbol", true, "")

    private val cratesHud by HUD("Crates Spawn Hud", "Supplies Timer") {
        if (it) textDim(formatTimer(180, 180, "§b§lSupplies spawn in:"), 0, 0, Colors.MINECRAFT_DARK_RED)
        else if (cratesTicks >= 0) textDim(formatTimer(cratesTicks, 180, "§b§lSupplies spawn in:"), 0, 0, Colors.MINECRAFT_DARK_RED)
        else 0 to 0
    }

    private val buildHud by HUD("Build Start Hud", "Build Timer") {
        if (it) textDim(formatTimer(80, 80, "§b§lBuild starts in:"), 0, 0, Colors.MINECRAFT_DARK_RED)
        else if (buildTicks >= 0) textDim(formatTimer(buildTicks, 80, "§b§lBuild starts in:"), 0, 0, Colors.MINECRAFT_DARK_RED)
        else 0 to 0
    }

    private var cratesTicks = -1
    private var buildTicks = -1

    init {
        on<ChatPacketEvent> {
            when {
                value.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!") -> {
                    if (cratesHud.enabled) cratesTicks = 180 // 9 seconds
                }

                value.contains("[NPC] Elle: OMG! Great work collecting my supplies!") -> {
                    if (buildHud.enabled) buildTicks = 80 // 4 sceoncsds
                }
            }
        }

        on<TickEvent.Server> {
            if (!KuudraUtils.inKuudra) return@on

            if (cratesTicks >= 0 && cratesHud.enabled) cratesTicks--
            if (buildTicks >= 0 && buildHud.enabled) buildTicks--
        }

        on<WorldEvent.Load> {
            cratesTicks = -1
            buildTicks = -1
        }
    }
        // honestly i tried this and it looks bad
//    private fun formatTimer(time: Int, max: Int, prefix: String): String {
//        val color = when {
//            time.toFloat() >= max * 0.66 -> "§a"
//            time.toFloat() >= max * 0.33 -> "§6"
//            else -> "§c"
//        }
//        val timeDisplay = if (displayInTicks) "$time${if (symbolDisplay) "t" else ""}"
//        else "${(time / 20f).toFixed(2)}${if (symbolDisplay) "s" else ""}"
//
//        return "$prefix $color$timeDisplay"
//    }
        private fun formatTimer(time: Int, max: Int, prefix: String): String {
            val color = when {
                time.toFloat() >= max * 0.66 -> "§a"
                time.toFloat() >= max * 0.33 -> "§6"
                else -> "§c"
            }
            val timeDisplay = if (displayInTicks) "$time${if (symbolDisplay) "t" else ""}"
            else "${(time / 20f).toFixed(2)}${if (symbolDisplay) "s" else ""}"

            return "$prefix $color$timeDisplay"
        }
}