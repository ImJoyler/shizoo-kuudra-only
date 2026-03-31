package shizo.events

import net.minecraft.network.protocol.Packet
import shizo.events.core.CancellableEvent


class MainThreadPacketPre(val packet: Packet<*>) : CancellableEvent()
class MainThreadPacketPost(val packet: Packet<*>) : CancellableEvent()

abstract class PacketEvent(val packet: Packet<*>) : CancellableEvent() {
    class Receive(packet: Packet<*>) : PacketEvent(packet)
    class Send(packet: Packet<*>) : PacketEvent(packet)
}

