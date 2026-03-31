package shizo.events


import net.minecraft.world.entity.Entity
import shizo.events.core.CancellableEvent

class EntityCheckRenderEvent(val entity: Entity) : CancellableEvent()