package shizo.module.impl.misc

import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.StringSetting
import shizo.module.impl.Module
import shizo.utils.sendChatMessage
import shizo.utils.sendCommand

object CustomShortcuts : Module(
    name = "Custom KeyShortcuts",
    description = "Very scuffy."
) {

    private val cmd1 by StringSetting("Command 1", "/skibidi", desc = "Command or message to send.")
    private val key1 by KeybindSetting("Key 1", GLFW.GLFW_KEY_UNKNOWN, desc = "Triggers Command 1.")
        .onPress { runShortcut(cmd1) }

    private val cmd2 by StringSetting("Command 2", "/wohoo", desc = "Command or message to send.")
    private val key2 by KeybindSetting("Key 2", GLFW.GLFW_KEY_UNKNOWN, desc = "Triggers Command 2.")
        .onPress { runShortcut(cmd2) }

    private val cmd3 by StringSetting("Command 3", "/pc kairo is a loser", desc = "Command or message to send.")
    private val key3 by KeybindSetting("Key 3", GLFW.GLFW_KEY_UNKNOWN, desc = "Triggers Command 3.")
        .onPress { runShortcut(cmd3) }

    private val cmd4 by StringSetting("Command 4", "/pc Ming hays a small iwllu", desc = "Command or message to send.")
    private val key4 by KeybindSetting("Key 4", GLFW.GLFW_KEY_UNKNOWN, desc = "Triggers Command 4.")
        .onPress { runShortcut(cmd4) }

    private val cmd5 by StringSetting("Command 5", "I LOVE IMJOYLESS AND CUBEY THEY ARE THE GREATEST", desc = "Command or message to send.")
    private val key5 by KeybindSetting("Key 5", GLFW.GLFW_KEY_UNKNOWN, desc = "Triggers Command 5.")
        .onPress { runShortcut(cmd5) }


    fun runShortcut(input: String) {
        if (input.isBlank()) return
        if (input.startsWith("/")) {
            sendCommand(input.substring(1))
        }
        else (sendChatMessage((input)))
    }
}