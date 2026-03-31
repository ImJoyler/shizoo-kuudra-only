package shizo.module.impl.kuudra.phaseone

import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.modMessage
import shizo.utils.sendCommand
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.textDim
import kotlin.collections.iterator
//todo fix
object NOPre : Module(
    name = "No Pre Alert",
    description = "Alerts your party if crates don't spawn at your pre-spot.",
    subcategory = "Phase 1"
) {
    private val noPreHud by HUD("No Pre HUD", "Displays missing crate.") { example ->
        if (example) return@HUD textDim("§cNo pre: Triangle", 0, 0)

        if (!enabled || !KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@HUD 0 to 0

        val spot = preSpot ?: return@HUD 0 to 0

        if (!KuudraUtils.spawnedCrates.contains(spot)) {
            textDim("§b§lNo pre: §f§l${spot.chatName}", 0, 0)
        } else {
            0 to 0
        }
    }

    private data class Loc(val x: Double, val z: Double, val radiusSq: Double)

    private val preLocations = mapOf(
        KuudraUtils.Crate.X to Loc(-133.5, -137.5, 400.0), // 20^2
        KuudraUtils.Crate.TRI to Loc(-67.5, -122.5, 400.0),
        KuudraUtils.Crate.EQUALS to Loc(-65.5, -87.5, 400.0),
        KuudraUtils.Crate.SLASH to Loc(-113.5, -68.5, 400.0)
    )

    private var preSpot: KuudraUtils.Crate? = null
    private var alertDelay = -1
    private var hasAlerted = false

    init {
        on<TickEvent.Start> {
            if (!enabled || !KuudraUtils.inKuudra) return@on

            if (KuudraUtils.phase != 1) {
                preSpot = null
                alertDelay = -1
                hasAlerted = false
                return@on
            }

            if (hasAlerted) return@on

            val player = mc.player ?: return@on

            if (KuudraUtils.giantZombies.isNotEmpty()) {
                if (preSpot == null) {
                    val px = player.x
                    val pz = player.z

                    for ((crate, loc) in preLocations) {
                        val distSq = (px - loc.x) * (px - loc.x) + (pz - loc.z) * (pz - loc.z)
                        if (distSq < loc.radiusSq) {
                            preSpot = crate
                            break
                        }
                    }
                }

                if (alertDelay == -1 && preSpot != null) {
                    alertDelay = 20 // 1 second delay
                }
            }

            if (alertDelay > 0) {
                alertDelay--
                if (alertDelay == 0) {
                    sendAlert()
                    hasAlerted = true
                    alertDelay = -1
                }
            }
        }
    }

    private fun sendAlert() {
        val spot = preSpot ?: return

        val second = when (spot) {
            KuudraUtils.Crate.TRI -> KuudraUtils.Crate.SHOP
            KuudraUtils.Crate.X -> KuudraUtils.Crate.XC
            KuudraUtils.Crate.SLASH -> KuudraUtils.Crate.SQUARE
            KuudraUtils.Crate.EQUALS -> null
            else -> return
        }

        if (!KuudraUtils.spawnedCrates.contains(spot)) {
            sendCommand("pc No ${spot.chatName}!")
            modMessage("No ${spot.chatName}")
        }

        if (second != null && !KuudraUtils.spawnedCrates.contains(second)) {
            sendCommand("pc No ${second.chatName}!")
            modMessage("No ${second.chatName}")
        }
    }
}