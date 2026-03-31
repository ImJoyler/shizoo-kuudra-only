package shizo.module.impl.kuudra.phaseone

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.ChatPacketEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.Colors
import shizo.utils.noControlCodes
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.textDim
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CrateOverlay : Module(
    name = "Crate Overlay",
    description = "Lohicc we MISS YOU.",
    subcategory = "Phase 1"
) {
    private val smoothOverlay by BooleanSetting(
        "Smooth Overlay",
        true,
        "Interpolates progress for a smoother animation."
    )

    private const val DEFAULT_BAR_SIZE = 20
    private const val RESET_TICKS_PER_STEP = 10

    private var currentPercent = -1.0
    private var lastPercent = -1.0
    private var serverTickCount = 0
    private var lastPickTick = 0
    private var lastUpdateTick = 0
    private var ticksPerStep = RESET_TICKS_PER_STEP

    private val crateRegex = Regex("\\[[|]+\\]\\s*(\\d+)%")

    val crateHud by HUD("Crate Progress", "Smooth progress bar for supplies.", false) { example ->
        if (example) {
            this.renderCrateHUD(93.0)
            return@HUD 100 to 25
        }

        if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1 || currentPercent < 0) return@HUD 0 to 0

        var predictedPercent = currentPercent

        if (smoothOverlay && lastPercent >= 0 && ticksPerStep > 0 && currentPercent > lastPercent) {
            val ticksSinceLastPick = serverTickCount - lastPickTick
            val percentDelta = currentPercent - lastPercent
            val interpolated = lastPercent + (percentDelta * min(ticksSinceLastPick, ticksPerStep) / ticksPerStep.toDouble())
            predictedPercent = min(interpolated, currentPercent)
        }

        this.renderCrateHUD(predictedPercent)
        return@HUD 100 to 25
    }
    private fun GuiGraphics.renderCrateHUD(progress: Double) {
        val prog = progress.coerceIn(0.0, 100.0)
        val filledBars = ((prog / 100.0) * DEFAULT_BAR_SIZE).roundToInt()
        val emptyBars = DEFAULT_BAR_SIZE - filledBars

        val filledBarString = "§b§l|".repeat(filledBars)
        val emptyBarString = "§f§l|".repeat(emptyBars)

        val progressBar = "§r§l§0[§r${filledBarString}§r${emptyBarString}§r§l§0]"

        val percentString = "§b${progress.toInt()}%"
//        val filledBarString = "§a§l:".repeat(filledBars)
//        val emptyBarString = "§8§l:".repeat(emptyBars)
//        val progressBar = "§c§l[§r$filledBarString§r$emptyBarString§c§l]"
//        val percentString = "§a${prog.toInt()}%"

        val width = max(getStringWidth(progressBar), getStringWidth(percentString))

        val barX = (width - getStringWidth(progressBar)) / 2
        val percentX = (width - getStringWidth(percentString)) / 2

        this.textDim(progressBar, barX, 0, Colors.WHITE)
        this.textDim(percentString, percentX, 12, Colors.WHITE)
    }

    private fun resetProgress() {
        currentPercent = -1.0
        lastPercent = -1.0
        ticksPerStep = RESET_TICKS_PER_STEP
    }

    init {
        on<WorldEvent.Load> {
            resetProgress()
            serverTickCount = 0
            lastPickTick = 0
        }

        on<TickEvent.Start> {
            serverTickCount++
        }

        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (msg.contains("You retrieved some of Elle's supplies") || msg.contains("You moved and the Chest slipped out")) {
                resetProgress()
            }
        }

        val checkCrateProgress = { msg: String ->
            if (KuudraUtils.inKuudra && KuudraUtils.phase == 1) {
                val match = crateRegex.find(msg)
                if (match != null) {
                    val percent = match.groupValues[1].toDoubleOrNull() ?: 0.0

                    if (currentPercent >= 0 && percent > currentPercent) {
                        lastPercent = currentPercent
                        lastUpdateTick = lastPickTick
                        ticksPerStep = max(1, serverTickCount - lastPickTick)
                    } else {
                        lastPercent = percent
                        lastUpdateTick = serverTickCount
                        ticksPerStep = RESET_TICKS_PER_STEP
                    }

                    currentPercent = percent
                    lastPickTick = serverTickCount
                    mc.execute {
                        mc.gui.setOverlayMessage(Component.empty(), false)
                        mc.gui.clearTitles()
                    }
                }
            }
        }

        onReceive<ClientboundSetActionBarTextPacket> { checkCrateProgress(this.text.string.noControlCodes) }
        onReceive<ClientboundSetTitleTextPacket> { checkCrateProgress(this.text.string.noControlCodes) }
        onReceive<ClientboundSetSubtitleTextPacket> { checkCrateProgress(this.text.string.noControlCodes) }
    }
}