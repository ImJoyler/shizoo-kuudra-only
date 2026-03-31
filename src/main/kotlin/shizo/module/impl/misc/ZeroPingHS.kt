package shizo.module.impl.misc

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.module.impl.Module
import shizo.utils.noControlCodes
import shizo.utils.skyblock.Island
import shizo.utils.skyblock.LocationUtils

object ZeroPingHS : Module(
    name = "Zeroping hardstone",
    description = "zphs",
    subcategory = "Mining"

) {
    private val pinglessHardstone by BooleanSetting("Pingless Hardstone", true, desc = "")
    private val pinglessOres by BooleanSetting("Pingless Ores", false, desc = "Instantly removes Ore blocks.")

    private val ORES = setOf(
        Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.EMERALD_ORE, Blocks.DIAMOND_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE
    )

    init {
        AttackBlockCallback.EVENT.register { _, world, _, pos, _ ->
            val player = mc.player ?: return@register InteractionResult.PASS

            if (!this.enabled || !world.isClientSide) return@register InteractionResult.PASS
           // CrystalHollows("Crystal Hollows")
            if (!LocationUtils.isCurrentArea(Island.CrystalHollows)) return@register InteractionResult.PASS

            val heldItemName = player.mainHandItem.displayName.string.noControlCodes

            if (!heldItemName.contains("Drill") && !heldItemName.contains("Gauntlet") /* add shit here just in case laster on idk*/) {
                return@register InteractionResult.PASS
            }

            val state = world.getBlockState(pos)
            val isStone = state.block == Blocks.STONE
            val isOre = ORES.contains(state.block)
            if (isStone && !pinglessHardstone) return@register InteractionResult.PASS
            if (isOre && !pinglessOres) return@register InteractionResult.PASS
            if (!isStone && !isOre) return@register InteractionResult.PASS

            world.removeBlock(pos, false)
            return@register InteractionResult.SUCCESS
        }
    }
}