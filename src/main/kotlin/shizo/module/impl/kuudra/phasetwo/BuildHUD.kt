package shizo.module.impl.kuudra.phasetwo

import net.minecraft.world.entity.decoration.ArmorStand
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Colors
import shizo.utils.devMessage
import shizo.utils.noControlCodes
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.textDim
import kotlin.math.roundToInt

object BuildHUD : Module(
    name = "Build Progress HUD",
    description = "Calculates instant build progress from piles.",
    subcategory = "Phase 2"

) {
    private val showProgress by BooleanSetting("Show Progress HUD", true, "Displays real-time build progress.")
    private val safeFPS by BooleanSetting(
        "Saves FPS",
        true,
        "Doesn't update every tick, but every 5: stun don't use this."
    )


    private val pileProgressMap = HashMap<Pair<Int, Int>, Int>()

    var currentCalculatedProgress = 0

    val progressHud by HUD("Build Progress", "Displays real-time phase 2 progress.", false) { example ->
        if (!showProgress) return@HUD 0 to 0

        if (!example && KuudraUtils.phase != 2) return@HUD 0 to 0

        val displayProgress = if (example) 75 else currentCalculatedProgress

        val color = when {
            displayProgress >= 80 -> "§a"
            displayProgress >= 60 -> "§2"
            displayProgress >= 40 -> "§e"
            displayProgress >= 20 -> "§6"
            else -> "§c"
        }

        val text1 = "§6§lBuild Progress"
        val text2 = "§fProgress: $color$displayProgress%"

        textDim(text1, 0, 0, Colors.WHITE)
        textDim(text2, 0, 10, Colors.WHITE)

        val width = maxOf(getStringWidth(text1), getStringWidth(text2))
        return@HUD width to 20
    }

    init {
        on<WorldEvent.Load> {
            pileProgressMap.clear()
            currentCalculatedProgress = 0
        }

        on<TickEvent.Start> {
            if (KuudraUtils.phase != 2) {
                if (pileProgressMap.isNotEmpty()) {
                    pileProgressMap.clear()
                    currentCalculatedProgress = 0
                }
                return@on
            }
            if (safeFPS) { if ((mc.player?.tickCount ?: 0) % 10 != 0) return@on }

            val level = mc.level ?: return@on

            for (entity in level.entitiesForRendering()) {
                if (entity !is ArmorStand) continue

                val name = entity.customName?.string?.noControlCodes ?: continue

                if (name.startsWith("PROGRESS:")) {
                    val key = Pair(entity.x.roundToInt(), entity.z.roundToInt())
                    // this fucked thjings up a bit lol
                    if (name.contains("COMPLETE")) {
                        pileProgressMap[key] = 100
                        devMessage("one pile completed")
                    }
                    else if (name.contains("%")) {
                        val percentStr = name.substringAfter("PROGRESS:").substringBefore("%").trim()
                        val progress = percentStr.toIntOrNull() ?: continue
                        pileProgressMap[key] = progress
                    }
                }
            }

            var total = 0
            for (prog in pileProgressMap.values) {
                total += prog
            }
            currentCalculatedProgress = total / 6
        }
    }
}