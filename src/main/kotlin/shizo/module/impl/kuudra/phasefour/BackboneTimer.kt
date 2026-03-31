package shizo.module.impl.kuudra.phasefour

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.EquipmentSlot
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onSend
import shizo.module.impl.Module
import shizo.utils.Colors
import shizo.utils.isHoldingByName
import shizo.utils.modMessage
import shizo.utils.skyblock.kuudra.KuudraUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BackboneTimer : Module(
    name = "Backbone Timer",
    description = "Displays a Backbone/Rend timer for Bonemerangs.",
    subcategory = "Phase 4"
) {
    private val devMode by BooleanSetting("Dev Mode", false, desc = "Works anywhere for testing.")
    private val joy by BooleanSetting("Joy Mode", false, desc = "Slightly longer so I dont' swap too fast ops")
    private val renderScale by NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1, desc = "Scale of the HUD.")

    private const val BAR_SIZE = 20
    private const val STARTING_TICKS = 22
    private const val JOY = 24

    private var ticksRemaining = 0
    private var cooldown = 0
    private var rendDisplayTicks = 0

    private val hornSound = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("shizo:horn"))

    val hud by HUD("Backbone Timer", "Displays the backbone rend timer.", false) { example ->
        val matrix = pose()
        val scale = renderScale.toFloat()
        var width = 0
        var height = 0

        matrix.pushMatrix()
        matrix.scale(scale, scale)

        if (example) {
            val (barStr, percentStr) = generateBackboneStrings(10, STARTING_TICKS)
            val barWidth = mc.font.width(barStr)
            val percentWidth = mc.font.width(percentStr)

            drawString(mc.font, barStr, 0, 0, Colors.WHITE.rgba)
            drawString(mc.font, percentStr, ((barWidth - percentWidth) / 2f).toInt(), 15, Colors.WHITE.rgba)

            width = (barWidth * scale).toInt()
            height = (25 * scale).toInt()

        } else if (rendDisplayTicks > 0) {
            val rendText = "§a§lREND!"
            matrix.scale(2f, 2f)

            drawString(mc.font, rendText, 0, 0, Colors.WHITE.rgba)

            width = (mc.font.width(rendText) * 2f * scale).toInt()
            height = (mc.font.lineHeight * 2f * scale).toInt()

        } else if (ticksRemaining > 0) {
            val (barStr, percentStr) = generateBackboneStrings(ticksRemaining, STARTING_TICKS)
            val barWidth = mc.font.width(barStr)
            val percentWidth = mc.font.width(percentStr)

            drawString(mc.font, barStr, 0, 0, Colors.WHITE.rgba)
            drawString(mc.font, percentStr, ((barWidth - percentWidth) / 2f).toInt(), 15, Colors.WHITE.rgba)

            width = (barWidth * scale).toInt()
            height = (25 * scale).toInt()
        }

        matrix.popMatrix()
        width to height
    }

    init {
        on<WorldEvent.Load> {
            ticksRemaining = 0
            cooldown = 0
            rendDisplayTicks = 0
        }

        onSend<ServerboundUseItemPacket> { handleBoneThrow() }
        onSend<ServerboundUseItemOnPacket> { handleBoneThrow() }

        on<TickEvent.Server> {
            if (cooldown > 0) cooldown--
            if (rendDisplayTicks > 0) rendDisplayTicks--

            if (ticksRemaining > 0) {
                ticksRemaining--

                if (ticksRemaining == 0) {
                    rendDisplayTicks = 20

                    val player = mc.player ?: return@on

                    mc.soundManager.play(SimpleSoundInstance.forUI(hornSound, 1.0f, 1.0f))

                    val currentHeldName = if (player.mainHandItem.isEmpty) "Nothing" else player.mainHandItem.hoverName.string
                    val helmet = player.getItemBySlot(EquipmentSlot.HEAD)
                    val helmetName = if (helmet.isEmpty) "No Helmet" else helmet.hoverName.string

                    val timeSeconds = STARTING_TICKS / 20.0
                    modMessage("Backbone hit in ${timeSeconds}s, while holding $currentHeldName §fand wearing $helmetName")
                }
            }
        }
    }

    private fun handleBoneThrow() {
        if (!devMode && (!KuudraUtils.inKuudra || KuudraUtils.phase == 0)) return

        if (isHoldingByName("bonemerang")) {
            if (cooldown > 0) return

            cooldown = 32
            if (joy) {
                ticksRemaining = JOY
            }
            else {
                ticksRemaining = STARTING_TICKS
            }
            rendDisplayTicks = 0
        }
    }

    private fun generateBackboneStrings(currentTicks: Int, totalTicks: Int): Pair<String, String> {
        if (totalTicks == 0) return "" to ""

        val elapsedTicks = totalTicks - currentTicks
        val percent = min(1.0, max(0.0, elapsedTicks.toDouble() / totalTicks.toDouble()))

        val filledBars = (percent * BAR_SIZE).roundToInt()
        val emptyBars = BAR_SIZE - filledBars

        val color = when {
            percent > 0.85 -> "§a"
            percent > 0.6 -> "§6"
            else -> "§c"
        }

        val filledStr = "$color§l|".repeat(filledBars)
        val emptyStr = "§7§l|".repeat(emptyBars)

        val progressBar = "§f§l[§r$filledStr§r$emptyStr§r§f§l]"
        val percentString = "§f${(percent * 100).toInt()}%"

        return progressBar to percentString
    }
}