package shizo.module.impl.kuudra.qolplus.fireball

import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.ChatPacketEvent
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.drawStyledBox

object Fireball : Module(
    name = "Fireball",
    description = "YOU SPIN MY HEAD RIGHT ROUND RIGHT ROUND WHEN MYTH DOES DOWN.",
    subcategory = "QOL"

) {
    private val defaultCycles by NumberSetting("Cycles", 10.0, 1.0, 30.0, 1.0, "How many points to teleport to before stopping.")
    private val startPos by NumberSetting("Start Position", 0.0, 0.0, 5.0, 1.0, "Icba typing which is x and so on...")

    private var testPacketsRemaining = 0
    private var testSequence = 0
    private var fbseq = 0

//    private val testStart by ActionSetting("NEVER CLICK OR BAN ", "don't use NEVER") {
//        if (mc.player == null) return@ActionSetting
//        testPacketsRemaining = 35
//        testSequence = 0
//        modMessage("§estart.")
//    }

    private var inFireballBuild = false
    private var cyclesLeft = 0
    private var nextCoordIndex = 0
    private var tickCounter = 0
    init {
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (msg == "[NPC] Elle: It's time to build the Ballista again! Cover me!") {
                inFireballBuild = true
                cyclesLeft = defaultCycles.toInt()
                nextCoordIndex = startPos.toInt()
                modMessage("§aBeyblade activated! Cycles: $cyclesLeft, Start Index: $nextCoordIndex")
            } else if (msg == "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!") {
                inFireballBuild = false
                modMessage("§cBuild process stopped - Ballista is ready.")
            }
        }
        on<TickEvent.Start> {
            if (mc.player == null || mc.level == null) return@on
//
//            if (testPacketsRemaining > 0) {
//                val nextTarget = FireballUtils.ballistaCoords[testPacketsRemaining % 6]
//                val (targetYaw, targetPitch) = FireballUtils.getYawAndPitch(nextTarget)
//                val currentYaw = mc.player?.yRot ?: 0f
//                val currentPitch = mc.player?.xRot ?: 0f
//                fireballClicks(mutableListOf(currentYaw, currentPitch), testSequence)
//                testSequence++
//                testPacketsRemaining--
//
//                if (testPacketsRemaining == 0) modMessage("§aTest finished.")
//
//                return@on
//            }

            if (!inFireballBuild || !this@Fireball.enabled) return@on

            val heldItem = mc.player?.mainHandItem?.hoverName?.string?.noControlCodes ?: ""
            if (!heldItem.contains("Aspect of the Void")) return@on

            if (cyclesLeft <= 0) {
                inFireballBuild = false
                modMessage("§cBeyblade finished cycles. Stopping.")
                return@on
            }

            val targetCoord = FireballUtils.ballistaCoords[nextCoordIndex]

            if (FireballUtils.getDistance2D(targetCoord) < 1.5) {
                nextCoordIndex = (nextCoordIndex + 1) % 6
                cyclesLeft--

                val nextTarget = FireballUtils.ballistaCoords[nextCoordIndex]
                val (targetYaw, targetPitch) = FireballUtils.getYawAndPitch(nextTarget)

                fireballClicks(mutableListOf(targetYaw, targetPitch), fbseq)
                fbseq++
            }
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on
            FireballUtils.ballistaCoords.forEachIndexed { index, coord ->
                val box = AABB(coord.x - 0.5, coord.y - 0.5, coord.z - 0.5, coord.x + 0.5, coord.y + 0.5, coord.z + 0.5)
                val color = if (inFireballBuild && index == nextCoordIndex) Color(0, 255, 0, 100f) else Color(128, 0, 128, 100f)
                this.drawStyledBox(box, color, 2, true)
            }
        }

        on<WorldEvent.Load> {
            inFireballBuild = false
        }
//        on<TickEvent.Start> { tickCounter++ }
//
//        on<PacketEvent.Send> {
//            if (packet is ServerboundSwingPacket) {
//                modMessage("§eTick $tickCounter: §cSent SWING (Left Click)")
//            }
//            if (packet is ServerboundUseItemPacket) {
//                modMessage("§eTick $tickCounter: §aSent USE ITEM (Right Click)")
//            }
//        }
    }

    private fun fireballClicks(rotations: MutableList<Float>, startSequence: Int) { // basically cubey shit but with leftclick
        require(rotations.size % 2 == 0) { "Rotations must have equal length" }
        val connection = mc.connection ?: return

        var i = 0
        var sequence = startSequence
        while (i < rotations.size) {
            val yRot = rotations[i]
            val xRot = rotations[i + 1]

            //leftClick()
            connection.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
            connection.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yRot, xRot))
            i += 2
            sequence++
        }
    }

}