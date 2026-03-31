package shizo.module.impl.misc

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import org.lwjgl.glfw.GLFW
import shizo.Shizo.mc
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.skyblock.dungeon.DungeonUtils

object GhostBlocks : Module(
    name = "Ghost block",
    description = "casper",
    subcategory = "Mining"
) {
    private val gKey by BooleanSetting("G Key", true, "Ghost blocking")
    private val swingHand by BooleanSetting("Swing hand", true, "Swings the player's hand.").withDependency { gKey }
    private val gKeyDelay by NumberSetting("Delay", 50.0, 0.0, 1000.0, 50.0, "Delay for gkey").withDependency { gKey }

    private val gKeyKeyBind by KeybindSetting("GKey Key", GLFW.GLFW_KEY_UNKNOWN, "Creates ghost blocks where you are looking.").withDependency { gKey }

    private var lastGhostBlockTime = 0L

    init {
        on<TickEvent.Start> {
            if (!gKey || !this@GhostBlocks.enabled || mc.screen != null) return@on

            val isKeyDown = gKeyKeyBind != InputConstants.UNKNOWN && InputConstants.isKeyDown(mc.window, gKeyKeyBind.value)
            if (!isKeyDown) return@on

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGhostBlockTime < gKeyDelay.toLong()) return@on

            val hitResult = mc.hitResult as? BlockHitResult ?: return@on
            val blockPos = hitResult.blockPos

            if (DungeonUtils.isSecret(mc.level?.getBlockState(blockPos), blockPos)) return@on

            if (swingHand) {
                mc.player?.swing(InteractionHand.MAIN_HAND)
            }

            toAir(blockPos)

            lastGhostBlockTime = currentTime
        }
    }

    fun toAir(blockPos: BlockPos?): Boolean {
        if (blockPos != null) {
            mc.level?.removeBlock(blockPos, false)
            return true
        }
        return false
    }
}