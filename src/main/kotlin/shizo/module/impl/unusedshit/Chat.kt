package shizo.module.impl.unusedshit

import net.minecraft.client.GuiMessage
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.events.GuiEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.modMessage
// ty noam :D
import shizo.interfaces.IChatComponent

object ChatTweaks : Module(
    name = "Chat Tweaks",
    description = "copy chat."
) {
    private val ctrlClickToCopy by BooleanSetting(
        "Ctrl Click to Copy",
        true,
        "Ctrl + Left Click a message to copy it to your clipboard."
    )

    init {
        on<GuiEvent.MouseClick> {
            if (!enabled || !ctrlClickToCopy) return@on
            if (mc.screen !is ChatScreen) return@on

            if (click.button() != 0) return@on
            val isCtrlPressed = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            if (!isCtrlPressed) return@on

            val message = getHoveredMsg().takeUnless { it.isBlank() } ?: return@on

            mc.keyboardHandler.clipboard = message
            modMessage("§aMessage copied to clipboard!")

            cancel()
        }
    }

    private fun getHoveredMsg(): String {
        val chatHud = (mc.gui.chat as? IChatComponent) ?: return ""

        val x = chatHud.mouseXtoChatX
        val y = chatHud.mouseYtoChatY
        val i = chatHud.getLineIndex(x, y)

        if (i < 0 || i >= chatHud.visibleMessages.size) return ""

        val builder = StringBuilder()
        val lines = ArrayList<GuiMessage.Line>()
        for (j in i.toInt() + 1 until chatHud.visibleMessages.size) {
            val line = chatHud.visibleMessages[j]
            if (line.endOfEntry()) break
            lines.add(0, line)
        }

        for (j in i.toInt() downTo 0) {
            val line = chatHud.visibleMessages[j]
            lines.add(line)
            if (line.endOfEntry()) break
        }

        for (line in lines) {
            line.content().accept { _, _, codePoint ->
                builder.appendCodePoint(codePoint)
                true
            }
        }

        return builder.toString().replace(Regex("§[0-9a-fk-or]"), "")
    }
}