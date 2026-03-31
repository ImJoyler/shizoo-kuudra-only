package shizo.module.impl.misc

import net.minecraft.world.item.Items
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.module.impl.Module
import shizo.utils.sendCommand
import shizo.utils.skyblock.LocationUtils
import org.lwjgl.glfw.GLFW

object CommandKeybinds : Module(
    name = "Command Keybinds",
    description = "Various keybinds for common skyblock commands.",
    key = null
) {
    private val pets by KeybindSetting("Pets", GLFW.GLFW_KEY_UNKNOWN, desc = "Opens the pets menu.").onPress {
        if (!enabled || !LocationUtils.isInSkyblock) return@onPress
        sendCommand("pets")
    }
    private val storage by KeybindSetting("Storage", GLFW.GLFW_KEY_UNKNOWN, desc = "Opens the storage menu.").onPress {
        if (!enabled || !LocationUtils.isInSkyblock) return@onPress
        sendCommand("storage")
    }
    private val wardrobe by KeybindSetting(
        "Wardrobe",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Opens the wardrobe menu."
    ).onPress {
        openWardrobe()
    }
    fun openWardrobe() {
        if (!enabled || !LocationUtils.isInSkyblock) return
        sendCommand("wardrobe")
    }
    private val equipment by KeybindSetting(
        "Equipment",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Opens the equipment menu."
    ).onPress {
        if (!enabled || !LocationUtils.isInSkyblock) return@onPress
        sendCommand("equipment")
    }
    private val dhub by KeybindSetting(
        "Dungeon Hub",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Warps to the dungeon hub."
    ).onPress {
        if (!enabled || !LocationUtils.isInSkyblock) return@onPress
        sendCommand("warp dungeon_hub")
    }

    private val pearl by KeybindSetting(
        "Pearl refill",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Refills ender pearls from sack."
    ).onPress {
        if (!enabled || !LocationUtils.isInSkyblock) return@onPress
        val inventory = mc.player?.inventory ?: return@onPress

        var pearlCount = 0

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty && stack.item == Items.ENDER_PEARL) {
                pearlCount += stack.count
            }
        }

        val targetStack = 16
        val remainder = pearlCount % targetStack

        if (pearlCount > 0 && remainder == 0) return@onPress

        val needed = targetStack - remainder

        sendCommand("gfs ender_pearl $needed")
    }
}
