package shizo.module.impl.kuudra.general.trackers

import com.github.stivais.commodore.Commodore
import net.minecraft.resources.ResourceLocation
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.ChatPacketEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.module.impl.kuudra.qolplus.vesuvius.NPCUtils.getClickableNPC
import shizo.utils.Colors
import shizo.utils.devMessage
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.draw2DImage
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.textDim

object KuudraTrackers: Module(
    name = "Trackers",
    description = "Runs and keys"
) {
    val keyTracker by BooleanSetting("Key Tracker", true, "Tracks Infernal Keys.")
    val runTracker by BooleanSetting("Run Tracker", true, "Tracks Pending Runs.")

    private var isNearNpc = false
    private val targetNpcs = listOf("Vesuvius", "Barbarian Emissary", "Mage Emissary")
    // yes its swapped cause myth :D
    private val keyTexture = ResourceLocation.fromNamespaceAndPath("shizo", "textures/gui/run.png")
    private val runTexture = ResourceLocation.fromNamespaceAndPath("shizo", "textures/gui/key.png")

    val keyImageHud by HUD("Infernal Key Image", "Displays the Infernal Key icon.", false) { example ->
        if (!keyTracker) return@HUD 0 to 0
        if (!example && !isNearNpc) return@HUD 0 to 0

        draw2DImage(this, keyTexture, 0, 0, 32, 32)
        return@HUD 32 to 32
    }

    val keyTextHud by HUD("Infernal Key Text", "Displays the Infernal Key count.", false) { example ->
        if (!keyTracker) return@HUD 0 to 0
        if (!example && !isNearNpc) return@HUD 0 to 0

        val text = "Infernal Keys: ${TrackerData.infernalKeys}"
        textDim(text, 0, 0, Colors.WHITE)

        return@HUD getStringWidth(text) to 10
    }

    val runImageHud by HUD("Pending Run Image", "Displays the Pending Runs icon.", false) { example ->
        if (!runTracker) return@HUD 0 to 0

        draw2DImage(this, runTexture, 0, 0, 32, 32)
        return@HUD 32 to 32
    }

    val runTextHud by HUD("Pending Run Text", "Displays the Pending Runs count.", false) { example ->
        if (!runTracker) return@HUD 0 to 0

        val text = "Pending Runs: ${TrackerData.kuudraRuns}"
        textDim(text, 0, 0, Colors.WHITE)

        return@HUD getStringWidth(text) to 10
    }

    init {
        TrackerData.load()

        on<TickEvent.Start> {
            if (!keyTracker) {
                isNearNpc = false
                return@on
            }

            if ((mc.player?.tickCount ?: 0) % 10 != 0) return@on

            val player = mc.player ?: return@on
            var found = false
            for (npcName in targetNpcs) {
                val npc = getClickableNPC(npcName)
                if (npc != null && player.distanceTo(npc) < 10) {
                    found = true
                    break
                }
            }

            isNearNpc = found
        }

        on<ChatPacketEvent> {
            val msg = value.noControlCodes

            if (keyTracker) {
                if (msg.contains("You bought Infernal Kuudra Key!")) {
                    TrackerData.infernalKeys++
                    TrackerData.save()
                    devMessage("§aAdded 1 Infernal Key, total: ${TrackerData.infernalKeys}")
                } else if (msg.contains("Crimson Essence x2000")) {
                    if (TrackerData.infernalKeys > 0) TrackerData.infernalKeys--
                    TrackerData.save()
                    devMessage("§cRemoved 1 Infernal Key, total: ${TrackerData.infernalKeys}")
                }
            }

            if (runTracker) {
                if (msg.contains("KUUDRA DOWN!") || msg.contains("DEFEAT")) {
                    TrackerData.kuudraRuns++
                    TrackerData.save()
                    devMessage("§aKuudra Down, total: ${TrackerData.kuudraRuns}")
                } else if (msg.contains("PAID CHEST REWARDS") || msg.contains("FREE CHEST REWARDS")) {
                    if (TrackerData.kuudraRuns > 0) TrackerData.kuudraRuns--
                    TrackerData.save()
                    devMessage("§cOpened Chest, removed 1 Run. Remaining: ${TrackerData.kuudraRuns}")
                }
            }
        }
    }
    val trackerCommand = Commodore("trackers") {
        runs {
            modMessage("§b§l--- Tracker Commands ---")
            modMessage("§e/trackers keys <amount> §7- Sets Infernal Keys.")
            modMessage("§e/trackers runs <amount> §7- Sets Pending Runs.")
        }

        literal("keys").runs { amount: Int ->
            TrackerData.infernalKeys = amount
            TrackerData.save()
            modMessage("§aSet Infernal Keys to §b$amount")
        }

        literal("runs").runs { amount: Int ->
            TrackerData.kuudraRuns = amount
            TrackerData.save()
            modMessage("§aSet Pending Runs to §b$amount")
        }
    }
}