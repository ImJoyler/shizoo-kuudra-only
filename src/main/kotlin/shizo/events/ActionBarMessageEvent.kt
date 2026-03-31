package shizo.events

import shizo.events.core.CancellableEvent
import shizo.utils.noControlCodes


class ActionBarMessageEvent(val component: net.minecraft.network.chat.Component) : CancellableEvent() {
    val formattedText: String get() = component.string
    val unformattedText: String get() = component.string.noControlCodes
    var message: String = formattedText
}