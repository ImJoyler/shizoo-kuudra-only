package shizo.module.impl.kuudra.qolplus.fireball

import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.events.ChatPacketEvent
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.module.impl.cheats.puzzles.solvers.WaterSolver
import shizo.utils.Color
import shizo.utils.handlers.EtherwarpUtils
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.skyblock.dungeon.DungeonUtils.currentRoom
import shizo.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import kotlin.math.floor
import shizo.module.impl.cheats.joything.InteractiveMapScreen
import net.minecraft.client.gui.screens.ChatScreen
object Fireball : Module(
    name = "Fireball",
    description = "YOU SPIN MY HEAD RIGHT ROUND RIGHT ROUND WHEN MYTH DOES DOWN.",
    subcategory = "QOL"
) {
    private val defaultCycles by NumberSetting("Cycles", 10.0, 1.0, 70.0, 1.0, "How many points to teleport to before stopping.")
    private val startPos by NumberSetting("Start Position", 0.0, 0.0, 5.0, 1.0, "Icba typing which is x and so on... just know x = 0 ")
    private val spin by NumberSetting("Spin speed", 2.0, 1.0, 10.0, 1.0, "Ticks between each teleport. Lower = faster spin.")

    private val testBind by KeybindSetting("Test Sequence Bind", GLFW.GLFW_KEY_UNKNOWN, "Press this key to forcefully start the sequence for testing.").onPress {startSequence(true)}

    private var fbseq = 0
    private var inFireballBuild = false
    private var cyclesLeft = 0
    private var nextCoordIndex = 0
    private var tickCounter = 0
    private var expectedPosition: Vec3? = null

    private fun startSequence(isTest: Boolean) {
        inFireballBuild = true
        cyclesLeft = defaultCycles.toInt()
        tickCounter = 0
        expectedPosition = FireballUtils.ballistaCoords[startPos.toInt()]
        nextCoordIndex = (startPos.toInt() + 1) % 6

        if (isTest) {
            modMessage("hi")
        } else {
            modMessage("stand on")
        }
    }

    private fun stopBeyblade(msg: String) {
        inFireballBuild = false
        cyclesLeft = 0
        expectedPosition = null
        mc.options.keyShift.setDown(false)
        if (msg.isNotEmpty()) modMessage(msg)
    }

    init {
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (msg == "[NPC] Elle: It's time to build the Ballista again! Cover me!") {
                startSequence(false)
            } else if (msg == "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!") {
                stopBeyblade("§cBuild process stopped - Ballista is ready.")
            }
        }

        on<TickEvent.Start> {
            if (mc.player == null || mc.level == null) return@on

            if (!inFireballBuild) return@on

            if  (mc.screen != null && mc.screen !is InteractiveMapScreen && mc.screen !is ChatScreen)  {
                mc.options.keyShift.setDown(false)
                return@on
            }

            val heldItem = mc.player?.mainHandItem?.hoverName?.string?.noControlCodes ?: ""
            if (!heldItem.contains("Aspect of the Void")) {
                mc.options.keyShift.setDown(false)
                return@on
            }

            if (cyclesLeft <= 0) {
                stopBeyblade("§cBeyblade finished cycles. Stopping.")
                return@on
            }

            if (cyclesLeft == defaultCycles.toInt()) {
                if (!inStart()) {
                    mc.options.keyShift.setDown(false)
                    return@on
                } else {
                    if (tickCounter == 0) modMessage("§aIn position! Spinning...")
                }
            }


            mc.options.keyShift.setDown(true)

            tickCounter++
            if (tickCounter >= spin.toInt()) {
                tickCounter = 0

                val nextTarget = FireballUtils.ballistaCoords[nextCoordIndex]
                val sourcePos = expectedPosition ?: return@on

                val dx = nextTarget.x - sourcePos.x
                val dy = nextTarget.y - (sourcePos.y + EtherwarpUtils.SNEAK_EYE_HEIGHT)
                val dz = nextTarget.z - sourcePos.z

                val angles = EtherwarpUtils.getYawAndPitch(dx, dy, dz)
                val targetYaw = angles[0]
                val targetPitch = angles[1]

                fireballClicks(mutableListOf(targetYaw, targetPitch), fbseq)
                fbseq++

                expectedPosition = nextTarget
                nextCoordIndex = (nextCoordIndex + 1) % 6
                cyclesLeft--
            }
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on

            FireballUtils.ballistaCoords.forEachIndexed { index, coord ->
                val box = AABB(coord.x - 0.5, coord.y - 0.5, coord.z - 0.5, coord.x + 0.5, coord.y + 0.5, coord.z + 0.5)

                val color = if (inFireballBuild && cyclesLeft == defaultCycles.toInt() && index == startPos.toInt()) {
                    Color(0, 255, 0, 100f)
                } else if (inFireballBuild && index == nextCoordIndex) {
                    Color(0, 255, 255, 100f)
                } else {
                    Color(128, 0, 128, 100f)
                }

                this.drawStyledBox(box, color, 2, true)
            }
        }

        on<WorldEvent.Load> {
            if (inFireballBuild) stopBeyblade("")
        }
    }

    private fun fireballClicks(rotations: MutableList<Float>, startSequence: Int) {
        require(rotations.size % 2 == 0) { "Rotations must have equal length" }
        val connection = mc.connection ?: return

        var i = 0
        var sequence = startSequence
        while (i < rotations.size) {
            val yRot = rotations[i]
            val xRot = rotations[i + 1]

            connection.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
            connection.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yRot, xRot))
            i += 2
            sequence++
        }
    }
    private fun inStart(): Boolean {
        val p = mc.player ?: return false
        val startPlatform = FireballUtils.ballistaCoords[startPos.toInt()]

        val targetX = floor(startPlatform.x).toInt()
        val targetY = floor(startPlatform.y).toInt()
        val targetZ = floor(startPlatform.z).toInt()

        return p.blockPosition().x == targetX &&
                p.blockPosition().z == targetZ &&
                p.blockPosition().y in targetY..(targetY + 2)
    }
}
