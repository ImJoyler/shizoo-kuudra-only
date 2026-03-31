package shizo.module.impl.kuudra.general

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.sendCommand
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.toFixed

object LagTracker : Module(
    name = "Lag Tracker",
    description = "Tracks time lost to server lag during a Kuudra run."
) {
    private val sendToParty by BooleanSetting("Send to Party", true, desc = "Sends the lag result to party chat.")

    private var isTracking = false
    private var runStart = 0L
    private var runTicks = 0

    init {
        on<WorldEvent.Load> {
            resetTracker()
        }

        onReceive<ClientboundSystemChatPacket> {
            if (!KuudraUtils.inKuudra) return@onReceive

            val msg = this.content.string.noControlCodes

            if (msg.contains("KUUDRA DOWN!") || msg.contains("DEFEAT")) {
                endRun()
            }
        }

        on<TickEvent.Server> {
            if (!KuudraUtils.inKuudra) {
                if (isTracking) resetTracker()
                return@on
            }

            if (KuudraUtils.phase == 1 && !isTracking && runStart == 0L) {
                runStart = System.currentTimeMillis()
                runTicks = 0
                isTracking = true
            }

            if (isTracking) {
                runTicks++
            }
        }
    }

    private fun endRun() {
        if (!isTracking) return
        isTracking = false

        val realTimeSeconds = (System.currentTimeMillis() - runStart) / 1000.0
        val serverTimeSeconds = runTicks / 20.0
        val timeLoss = (realTimeSeconds - serverTimeSeconds)

        val timeLossFormatted = timeLoss.toFloat().toFixed(2)

        if (sendToParty) {
            sendCommand("pc [Joyler] Time lost to lag: ${timeLossFormatted}s")
        }

        modMessage(" ")
        modMessage("§c${timeLossFormatted}s §flost to lag.")
        modMessage(" ")
    }

    private fun resetTracker() {
        isTracking = false
        runStart = 0L
        runTicks = 0
    }
}