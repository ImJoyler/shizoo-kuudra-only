package shizo.events

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import shizo.events.core.CancellableEvent

class BlockInteractEvent(val pos: BlockPos) : CancellableEvent()
class EntityInteractEvent(val pos: Vec3, val entity: Entity) : CancellableEvent()