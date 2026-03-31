package shizo.events


import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import shizo.events.core.Event

class BlockUpdateEvent(val pos: BlockPos, val old: BlockState, val updated: BlockState) : Event()