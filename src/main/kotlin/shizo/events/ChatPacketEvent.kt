package shizo.events

import shizo.events.core.Event
import net.minecraft.network.chat.Component
import shizo.events.core.CancellableEvent

class ChatPacketEvent(val value: String, val component: Component) : Event()
class MessageSentEvent(val message: String) : CancellableEvent()
