package shizo.module.impl.unusedshit


import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.EntityInteractEvent
import shizo.events.RenderEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.addVec
import shizo.utils.component1
import shizo.utils.component2
import shizo.utils.component3
import shizo.utils.handlers.TickTask
import shizo.utils.renderUtils.renderUtils.drawText
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.dungeon.M7Phases
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items

object ArrowAlign : Module(
    name = "Arrow Align",
    description = "Shows the solution for the Arrow Align device."
) {
    private val blockWrong by BooleanSetting("Block Wrong Clicks", false, desc = "Blocks wrong clicks, shift will override this.")
    private val invertSneak by BooleanSetting("Invert Sneak", false, desc = "Only block wrong clicks whilst sneaking, instead of whilst standing").withDependency { blockWrong }

    private val recentClickTimestamps = mutableMapOf<Int, Long>()
    private val clicksRemaining = mutableMapOf<Int, Int>()
    private var currentFrameRotations: List<Int>? = null
    private var targetSolution: List<Int>? = null
    private val frameGridCorner = BlockPos(-2, 120, 75)
    private val centerBlock = BlockPos(0, 120, 77)

    init {
        TickTask(1) {
            if (!enabled || DungeonUtils.getF7Phase() != M7Phases.P3) return@TickTask
            clicksRemaining.clear()
            if ((mc.player?.blockPosition()?.distSqr(centerBlock) ?: return@TickTask) > 200) {
                currentFrameRotations = null
                targetSolution = null
                return@TickTask
            }

            currentFrameRotations = getFrames()

            possibleSolutions.forEach { arr ->
                for (i in arr.indices) {
                    if ((arr[i] == -1 || currentFrameRotations?.get(i) == -1) && arr[i] != currentFrameRotations?.get(i)) return@forEach
                }

                targetSolution = arr

                for (i in arr.indices) {
                    clicksRemaining[i] = calculateClicksNeeded(currentFrameRotations?.get(i) ?: return@forEach, arr[i]).takeIf { it != 0 } ?: continue
                }
            }
        }

        on<EntityInteractEvent> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3) return@on
            if (entity !is ItemFrame || entity.item?.item != Items.ARROW) return@on
            val (x, y, z) = entity.blockPosition()

            val frameIndex = ((y - frameGridCorner.y) + (z - frameGridCorner.z) * 5)
            if (x != frameGridCorner.x || currentFrameRotations?.get(frameIndex) == -1 || frameIndex !in 0..24) return@on

            if (!clicksRemaining.containsKey(frameIndex) && mc.player?.isCrouching == invertSneak && blockWrong) {
                cancel()
                return@on
            }

            recentClickTimestamps[frameIndex] = System.currentTimeMillis()
            currentFrameRotations = currentFrameRotations?.toMutableList()?.apply { this[frameIndex] = (this[frameIndex] + 1) % 8 }

            if (calculateClicksNeeded(currentFrameRotations?.get(frameIndex) ?: return@on, targetSolution?.get(frameIndex) ?: return@on) == 0) clicksRemaining.remove(frameIndex)
        }

        on<RenderEvent.Extract> {
            if (clicksRemaining.isEmpty() || DungeonUtils.getF7Phase() != M7Phases.P3) return@on
            clicksRemaining.forEach { (index, clickNeeded) ->
                val colorCode = when {
                    clickNeeded == 0 -> return@forEach
                    clickNeeded < 3 -> 'a'
                    clickNeeded < 5 -> '6'
                    else -> 'c'
                }
                drawText(
                    "§$colorCode$clickNeeded",
                    getFramePositionFromIndex(index).center.addVec(y = 0.1, x = -0.3),
                    1f, false
                )
            }
        }
    }

    private fun getFrames(): List<Int> {
        val itemFrames = mc.level?.entitiesForRendering()?.mapNotNull {
            if (it is ItemFrame && it.item?.item?.asItem() == Items.ARROW) it else null
        }?.takeIf { it.isNotEmpty() } ?: return List(25) { -1 }

        return (0..24).map { index ->
            if (recentClickTimestamps[index]?.let { System.currentTimeMillis() - it < 1000 } == true && currentFrameRotations != null)
                currentFrameRotations?.get(index) ?: -1
            else
                itemFrames.find { it.blockPosition() == getFramePositionFromIndex(index) }?.rotation ?: -1
        }
    }

    private fun getFramePositionFromIndex(index: Int): BlockPos =
        frameGridCorner.offset(0, index % 5, index / 5)

    private fun calculateClicksNeeded(currentRotation: Int, targetRotation: Int): Int =
        (8 - currentRotation + targetRotation) % 8

    private val possibleSolutions = listOf(
        listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
        listOf(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
        listOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
        listOf(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
        listOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        listOf(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        listOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        listOf(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1),
        listOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1)
    )
}