package shizo.utils

import net.minecraft.network.chat.ClickEvent
import shizo.Shizo.mc
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import shizo.module.impl.render.ClickGUIModule
import kotlin.div
import kotlin.text.compareTo

fun sendChatMessage(message: String) {
    mc.execute { mc.player?.connection?.sendChat(message) }
}

fun sendCommand(command: String) {
    mc.execute { mc.player?.connection?.sendCommand(command) }
}
fun modMessage(message: Any?, prefix: String = "§5Shizo §8»§r ", chatStyle: Style? = null) {
    val text = Component.literal("$prefix$message")
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui?.chat?.addMessage(text) }
}

fun modMessage(message: String, prefix: String = "§5Shizo §8|§r ", chatStyle: Style? = null) {
    val text = Component.literal(prefix).append(message)
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui?.chat?.addMessage(text) }
}


fun modMessage(component: Component, prefix: String = "§5Shizo §8»§r ", chatStyle: Style? = null) {
    val text = Component.literal(prefix).append(component)
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui?.chat?.addMessage(text) }
}

fun getChatBreak(): String =
    mc.gui?.chat?.width?.let {
        "§9§m" + "-".repeat(it / mc.font.width("-"))
    } ?: ""

fun devMessage(message: Any?) {
    if (ClickGUIModule.devMessage) {
        modMessage(message, prefix = "§9Shizo: §f")
    }
}

fun getCenteredText(text: String): String {
    val strippedText = text.noControlCodes
    if (strippedText.isEmpty()) return text
    val textWidth = mc.font.width(strippedText)
    val chatWidth = mc.gui.chat.width

    if (textWidth >= chatWidth) return text

    val spacesNeeded = ((chatWidth - textWidth) / 2 / 4).coerceAtLeast(0)
    return " ".repeat(spacesNeeded) + text
}

fun getPrefix(): String {
    return "§5Shizo §8|§r "

}
// yes. i used ai to port
// no i do not care.
/**
 * Creates a new MutableComponent displaying [text] and showing [hoverText] when it is hovered.
 * [hoverText] can include "\n" for new lines.
 */
fun createHoverableText(text: String, hoverText: String): MutableComponent {
    return Component.literal(text).withStyle { style ->
        style.withHoverEvent(
            HoverEvent.ShowText(Component.literal(hoverText))
        )
    }
}

/**
 * Creates a clickable component that runs a command, and automatically prints it to chat.
 */
fun createClickableText(text: String, hoverText: String, action: String): MutableComponent {
    val message = Component.literal(text).withStyle { style ->
        style
            .withHoverEvent(HoverEvent.ShowText(Component.literal(hoverText)))
            .withClickEvent(ClickEvent.RunCommand(action))
    }

    mc.player?.displayClientMessage(message, false)

    return message
}