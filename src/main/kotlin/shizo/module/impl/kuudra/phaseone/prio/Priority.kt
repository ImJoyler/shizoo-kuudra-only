package shizo.module.impl.kuudra.phaseone.prio

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.projectile.ThrownEnderpearl
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.ChatPacketEvent
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.events.core.onSend
import shizo.module.impl.Module
import shizo.utils.*
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.renderUtils.renderUtils.drawText
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.skyblock.kuudra.KuudraUtils.Crate
import shizo.utils.skyblock.kuudra.PearlUtils
import kotlin.collections.get
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Priority : Module(
    name = "Crate Priority",
    description = "Displays where to go during Kuudra supply phase.",
    subcategory = "Phase 1"
) {
    val crateAlerts by BooleanSetting("On-Screen Alerts", false, "Use alerts.")
    val debugMode by BooleanSetting("Debug Mode", false, "")
    val renderTarget by BooleanSetting("Render Target", true, "Master toggle for rendering.")
    val renderTargetBlocks by BooleanSetting("Render Target Blocks", true, "Makes pearling blocks purple.").withDependency { renderTarget }
    val renderTextLabels by BooleanSetting("Render Text Labels", true, "Draws prios names.").withDependency { renderTarget }
    val alertTime by NumberSetting("Alert Duration", 40.0, 10.0, 200.0, 5.0, "How long the HUD/Alert stays on screen (ticks)")
    val myth by BooleanSetting("Myth", true, "el cinco")

    var pearlLandingPos: Vec3? = null
    var landingPosTimer = 0
    var myPearlId: Int? = null

    var currentPreset: Crate? = null
    var lastInstruction: PriorityConfig.Instruction? = null
    private var predictionThrottle = 0
    private var hudTimer = 0

    data class TargetSpot(val name: String, val x: Double, val y: Double, val z: Double)

    private val pileSpots = mapOf(
        Crate.SQUARE to listOf(
            TargetSpot("SQUARE SAFE", -139.5, 78.0, -89.5),
            TargetSpot("SQUARE MIDDLE", -138.5, 78.0, -87.5)
        ),
        Crate.XC to listOf(
            // -129.5 79 -114.5
            // -129.5 79 -114.5
            TargetSpot("XC CANNON", -129.5, 78.0, -114.5),
            TargetSpot("XC MIDDLE", -134.5, 78.0, -127.5),
            TargetSpot("XC COAL", -133.5, 78.0, -129.5)
        ),
        Crate.SHOP to listOf(
            TargetSpot("SHOP COAL", -87.5, 78.0, -127.5),
            TargetSpot("SHOP MIDDLE", -77.5, 78.0, -134.5),
            TargetSpot("SHOP SAFE", -72.5, 78.0, -134.5)
        )
    )

    private val priorityHud by HUD("Crate Priority HUD", "Displays where to go next based on callouts.") { example ->
        if (example) return@HUD textDim("§b§lGO X CANNON", 0, 0)
        if (!KuudraUtils.inKuudra || hudTimer <= 0) return@HUD 0 to 0

        val inst = lastInstruction ?: return@HUD 0 to 0
        val text = "§b§l${getTranslatedTask(inst.task)}"
        textDim(text, 0, 0)
    }

    init {
        PriorityConfig.init()


        onReceive<ClientboundAddEntityPacket> {
            val player = mc.player ?: return@onReceive

            if (this.type == net.minecraft.world.entity.EntityType.ENDER_PEARL) {
                val spawnPos = Vec3(this.x, this.y, this.z)

                if (player.eyePosition.distanceToSqr(spawnPos) < 2.0) {
                    myPearlId = this.id
                }
            }
        }

        on<TickEvent.Start> {
            if (hudTimer > 0) hudTimer--
            // test
            if (landingPosTimer > 0) {
                val level = mc.level
                predictionThrottle++
                if (predictionThrottle >= 5) {
                    predictionThrottle = 0
                    if (level != null && myPearlId != null) {
                        val activePearl = level.getEntity(myPearlId!!)

                        if (activePearl != null) {
                            val updatedLanding = PearlUtils.predictPearlLandingFromEntity(activePearl)
                            if (updatedLanding != null) {
                                pearlLandingPos = updatedLanding
                                landingPosTimer = 100
                            }
                        }
                    }

                    landingPosTimer--
                    if (landingPosTimer <= 0) {
                        pearlLandingPos = null
                        myPearlId = null
                    }
                }
            }
        }

        on<ChatPacketEvent> {
            val cleanMsg = value.noControlCodes

            if (cleanMsg.contains("[NPC] Elle: Head over to the main platform")) {
                val player = mc.player ?: return@on
                currentPreset = KuudraUtils.getPlayerPreSpot(player.x, player.z)
                if (crateAlerts) alert("§b§lPRE = ${currentPreset?.name}", false, alertTime.toInt())
            }

            val crateRegex = Regex("Party > .+: No (.+)")
            crateRegex.find(cleanMsg)?.let {
                val captured = it.groupValues[1].replace("!", "").uppercase().trim()

                val missing: Crate? = when {
                    captured.contains("TRI") -> Crate.TRI
                    captured.contains("=") || captured.contains("EQUALS") -> Crate.EQUALS
                    captured.contains("/") || captured.contains("SLASH") -> Crate.SLASH
                    captured.contains("SHOP") -> Crate.SHOP
                    captured.contains("SQUARE") -> Crate.SQUARE
                    captured == "X" -> Crate.X
                    captured.contains("XC") || captured.contains("CANNON") -> Crate.XC
                    else -> null
                }

                if (debugMode) {
                    modMessage("§7[DEBUG] Msg: §f\"$captured\" §7-> Mapped: §b${missing?.name} §7| Current Pre: §f${currentPreset?.name}")
                }

                if (currentPreset != null && missing != null) {
                    val move = PriorityConfig.customLogic[currentPreset]?.get(missing)
                    if (move != null) {
                        lastInstruction = move
                        hudTimer = alertTime.toInt()
                        if (crateAlerts) {
                            alert("§b§l${getTranslatedTask(move.task)}", true, alertTime.toInt())
                        }
                    }
                }
            }

            if (cleanMsg.contains("[NPC] Elle: OMG! Great work collecting my supplies!")) {
                currentPreset = null
                lastInstruction = null
                hudTimer = 0
            }
        }

        onSend<ServerboundUseItemPacket> {
            val player = mc.player ?: return@onSend

            if (player.mainHandItem.itemId != "ENDER_PEARL") return@onSend

            val predictedLanding = PearlUtils.predictPearlLandingFromPlayer()
            if (predictedLanding != null) {
                pearlLandingPos = predictedLanding
                landingPosTimer = 100
                devMessage("§aPearl Landing: ${predictedLanding.x}, ${predictedLanding.y}, ${predictedLanding.z}")
            }
        }

        on<RenderEvent.Extract> {
            if (!KuudraUtils.inKuudra || !renderTarget) return@on

            val inst = lastInstruction ?: return@on
            val player = mc.player ?: return@on
            val landing = pearlLandingPos ?: return@on

            val spotsToRender = pileSpots[inst.destinationPile] ?: return@on

            val boxSize = 0.5
            val landingAABB = AABB(
                landing.x - boxSize, landing.y - 1.0, landing.z - boxSize,
                landing.x + boxSize, landing.y + (boxSize * 2) - 1.0, landing.z + boxSize
            )
            if (renderTargetBlocks) {
                this.drawStyledBox(landingAABB, Color(0, 255, 0, 0.3f), style = 2, depth = false)
            }

            val originEyePos = Vec3(landing.x, landing.y + 1.62 - 0.08, landing.z)

            spotsToRender.forEach { spot ->
                if (renderTargetBlocks) {
                    val targetAABB = AABB(
                        spot.x - 0.5, spot.y, spot.z - 0.5,
                        spot.x + 0.5, spot.y + 1.0, spot.z + 0.5
                    )
                    this.drawStyledBox(targetAABB, Color(255, 0, 255, 0.8f), style = 2, depth = false)
                }

                val targetCenter = Vec3(spot.x, spot.y + 1.0, spot.z)
                val dx = targetCenter.x - originEyePos.x
                val dy = targetCenter.y - originEyePos.y
                val dz = targetCenter.z - originEyePos.z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)

                val dynamicYaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
                val dynamicPitch = Math.toDegrees(Math.asin(-dy / distance)).toFloat()

                val floatingPos = drawTargetBox(this, player.eyePosition, dynamicYaw, dynamicPitch, Color(0, 255, 255, 0.8f))

                if (renderTextLabels) {
                    val displayName = getTranslatedSpot(spot.name)
                    this.drawText("§d§l$displayName", Vec3(floatingPos.x, floatingPos.y - 0.3, floatingPos.z), 1.0f, false)
                }
            }
        }

        on<WorldEvent.Load> {
            currentPreset = null
            lastInstruction = null
            hudTimer = 0
            pearlLandingPos = null
            myPearlId = null
        }
    }

    private fun drawTargetBox(event: RenderEvent.Extract, originEyePos: Vec3, dynamicYaw: Float, dynamicPitch: Float, color: Color): Vec3 {
        val yawRad = Math.toRadians(dynamicYaw.toDouble())
        val pitchRad = Math.toRadians(dynamicPitch.toDouble())

        val dirX = -sin(yawRad) * cos(pitchRad)
        val dirY = -sin(pitchRad)
        val dirZ = cos(yawRad) * cos(pitchRad)

        val distance = 30.0
        val floatingPos = originEyePos.add(dirX * distance, dirY * distance, dirZ * distance)

        val boxSize = 0.05
        val floatingAABB = AABB(
            floatingPos.x - boxSize, floatingPos.y - boxSize, floatingPos.z - boxSize,
            floatingPos.x + boxSize, floatingPos.y + boxSize, floatingPos.z + boxSize
        )
        event.drawStyledBox(floatingAABB, color, style = 2, depth = false)

        return floatingPos
    }

    private fun getTranslatedTask(task: String): String {
        if (!myth) return task
        return when(task) {
            "GO SHOP" -> "IDŹ DO SKLEPU"
            "GO X CANNON", "GO XCANNON" -> "IDŹ DO DZIAŁKA X"
            "GO SQUARE" -> "IDŹ DO KWADRATU"
            else -> task
        }
    }

    private fun getTranslatedSpot(name: String): String {
        if (!myth) return name
        return when (name) {
            "SQUARE SAFE" -> "BEZPIECZNY KWADRAT"
            "SQUARE MIDDLE" -> "KWADRAT NA ŚRODKU"
            "XC CANNON" -> "DZIAŁKO X"
            "XC MIDDLE" -> "DZIAŁKO X NA ŚRODKU"
            "XC COAL" -> "DZIAŁKO X PRZY WĘGLU"
            "SHOP COAL" -> "WĘGIEL SKLEP"
            "SHOP MIDDLE" -> "SKLEP NA ŚRODKU"
            "SHOP SAFE" -> "SKLEP BEZPIECZNY"
            "X SAFE" -> "X BEZPIECZNY"
            "SLASH SAFE" -> "UKOŚNIK BEZPIECZNY"
            "EQUALS SAFE" -> "ZNAK RÓWNOŚCI BEZPIECZNY"
            "TRI SAFE" -> "TRÓJKĄT BEZPIECZNY"
            else -> name
        }
    }
}
