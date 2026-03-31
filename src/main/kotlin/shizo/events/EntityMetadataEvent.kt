package shizo.events

import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.Entity
import shizo.events.core.CancellableEvent

class EntityMetadataEvent(val entity: Entity, packet: Packet<*>) : CancellableEvent()