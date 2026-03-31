package shizo.module.impl.kuudra.phasetwo

import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.alert
import shizo.utils.noControlCodes
import shizo.utils.playSoundAtPlayer
import shizo.utils.skyblock.kuudra.KuudraUtils
import kotlin.math.max

object Vengeance : Module(
    name = "Vengeance",
    description = "Justice has come to Skyblock.",
    subcategory = "Phase 2"
) {
    private val batman by BooleanSetting("Master toggle", true, desc = "Master toggle for the custom sounds.")
    private val justice by BooleanSetting("Justice", true, desc = "Vengeance.")
    private val buildHp by BooleanSetting("Build Title", true, desc = "Shows a title when build finishes.")
    private val batmanSlider by NumberSetting(
        "Lego Frequency",
        1.0,
        1.0,
        10.0,
        1.0,
        desc = "How often the lego sound plays."
    )

    private var anvilCount = 0
    private var lastPhase = -1

    private val startSound = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("shizo:start"))
    private val defeatSound = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("shizo:defeat"))
    private val buildSound = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("shizo:build"))
    private val legoSound = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("shizo:lego"))

    init {
        on<WorldEvent.Load> {
            anvilCount = 0
            lastPhase = -1
        }

        on<TickEvent.Server> {
            if (!KuudraUtils.inKuudra) {
                lastPhase = -1
                return@on
            }

            val currentPhase = KuudraUtils.phase
            if (currentPhase != lastPhase) {

                if (currentPhase == 2) {
                    if (justice && batman) {
                        playSoundAtPlayer(startSound, 1f, 1f)
                        alert("§fI am §l§cVengeance", playSound = false, stay = 40)
                    }
                }
                // this is a bit horrible IMO
                else if (currentPhase == 3 && lastPhase == 2) {
                    if (batman) {
                        playSoundAtPlayer(buildSound, 1f, 1f)
                        if (buildHp) {
                            alert("§fBuild is done!", playSound = false, stay = 40)
                        }
                    }
                }

                lastPhase = currentPhase
            }
        }

        onReceive<ClientboundSystemChatPacket> {
            val msg = this.content.string.noControlCodes
            if (msg.contains("DEFEAT")) {
                playSoundAtPlayer(defeatSound, 1f, 1f)
                alert("§fI feel like §bNIky55 §frn", playSound = false, stay = 40)
            }
        }

        onReceive<ClientboundSoundPacket> {
            if (!batman || KuudraUtils.phase != 2) return@onReceive

            val soundPath = this.sound.value().location.path.lowercase()
            // a bit messy but tyeah
            if (soundPath.contains("anvil_land") || soundPath.contains("anvil.land")) {
                it.cancel()

                anvilCount++
                val frequency = max(1, batmanSlider.toInt())
                if (anvilCount % frequency == 0) {
                    playSoundAtPlayer(legoSound, 1f, 1f)
                }
            }
        }

        onReceive<ClientboundSoundEntityPacket> {
            if (!batman || KuudraUtils.phase != 2) return@onReceive

            val soundPath = this.sound.value().location.path.lowercase()

            if (soundPath.contains("anvil_land") || soundPath.contains("anvil.land")) {
                it.cancel()

                anvilCount++
                val frequency = max(1, batmanSlider.toInt())
                if (anvilCount % frequency == 0) {
                    playSoundAtPlayer(legoSound, 1f, 1f)
                }
            }
        }
    }
}