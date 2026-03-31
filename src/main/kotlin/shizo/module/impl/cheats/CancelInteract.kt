package shizo.module.impl.cheats

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.module.impl.Module
import shizo.utils.containsOneOf
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import shizo.utils.hasAbility
import shizo.utils.heldItem
import shizo.utils.isHoldingByName
import shizo.utils.modMessage

object CancelInteract : Module(
    name = "Cancel Interact",
    description = "Cancels interaction with certain blocks"
) {
    private val cancelInteract by BooleanSetting("Cencel Interact", true, desc = "Cancels interaction with certain blocks")
    private val interactionWhitelist = setOf<Block>(
        Blocks.LEVER, Blocks.CHEST, Blocks.TRAPPED_CHEST,
        Blocks.STONE_BUTTON, Blocks.OAK_BUTTON, Blocks.DARK_OAK_BUTTON,
        Blocks.ACACIA_BUTTON, Blocks.BIRCH_BUTTON, Blocks.JUNGLE_BUTTON,
        Blocks.SPRUCE_BUTTON
    )

    private val interactionBlacklist = setOf<Block>(
        Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE, Blocks.DARK_OAK_FENCE,
        Blocks.ACACIA_FENCE, Blocks.BIRCH_FENCE, Blocks.JUNGLE_FENCE,
        Blocks.NETHER_BRICK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE,
        Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE, Blocks.OAK_FENCE_GATE,
        Blocks.JUNGLE_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.HOPPER,
    )

    @JvmStatic
    fun cancelInteractHook(player: LocalPlayer?, hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (player == null) return false
        if (!(cancelInteract && enabled)) return false
        val world = player.level() as? ClientLevel ?: return false
        val pos: BlockPos = hit.blockPos
        val state = world.getBlockState(pos)
        val block = state.block
        if (interactionBlacklist.contains(block)) return false
        if (isHoldingByName("Ender")) return true

        return false
    }
}