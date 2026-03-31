package shizo.module.impl.misc

import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.*
import shizo.events.GuiEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.clickSlot
import shizo.utils.modMessage
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW
import shizo.utils.createSoundSettings
import shizo.utils.playSoundSettings

object WardrobeKeybinds : Module(
    name = "Wardrobe Keybinds",
    description = "Allows you to use keybinds to navigate the wardrobe.",
    key = null
) {
    private val nextPageKeybind by KeybindSetting("Next Page", GLFW.GLFW_KEY_RIGHT, desc = "Keybind to go to the next page in the wardrobe.")
    private val previousPageKeybind by KeybindSetting("Previous Page", GLFW.GLFW_KEY_LEFT, desc = "Keybind to go to the previous page in the wardrobe.")
    private val unequipKeybind by KeybindSetting("Unequip", GLFW.GLFW_KEY_UNKNOWN, desc = "Keybind to unequip the currently equipped item in the wardrobe.")

    private val disallowUnequippingEquipped by BooleanSetting("Disable Unequip", desc = "Prevents unequipping equipped armor.")

    private val autoClose by BooleanSetting("Auto Close", false, desc = "Automatically closes the GUI after selecting a set.")

    private val dropdown by DropdownSetting("Sounds")
    private val playSounds by BooleanSetting("Play Sound", false, desc = "Plays a sound when selecting a set.").withDependency { dropdown }
    private val soundSettings = createSoundSettings("Click Sound", "entity.experience_orb.pickup") { playSounds && dropdown }

     val advanced by DropdownSetting("Show Settings")
     val wardrobe1 by KeybindSetting("Wardrobe 1", GLFW.GLFW_KEY_1, desc = "Keybind to equip the first wardrobe slot.").withDependency { advanced }
     val wardrobe2 by KeybindSetting("Wardrobe 2", GLFW.GLFW_KEY_2, desc = "Keybind to equip the second wardrobe slot.").withDependency { advanced }
     val wardrobe3 by KeybindSetting("Wardrobe 3", GLFW.GLFW_KEY_3, desc = "Keybind to equip the third wardrobe slot.").withDependency { advanced }
     val wardrobe4 by KeybindSetting("Wardrobe 4", GLFW.GLFW_KEY_4, desc = "Keybind to equip the fourth wardrobe slot.").withDependency { advanced }
     val wardrobe5 by KeybindSetting("Wardrobe 5", GLFW.GLFW_KEY_5, desc = "Keybind to equip the fifth wardrobe slot.").withDependency { advanced }
     val wardrobe6 by KeybindSetting("Wardrobe 6", GLFW.GLFW_KEY_6, desc = "Keybind to equip the sixth wardrobe slot.").withDependency { advanced }
     val wardrobe7 by KeybindSetting("Wardrobe 7", GLFW.GLFW_KEY_7, desc = "Keybind to equip the seventh wardrobe slot.").withDependency { advanced }
     val wardrobe8 by KeybindSetting("Wardrobe 8", GLFW.GLFW_KEY_8, desc = "Keybind to equip the eighth wardrobe slot.").withDependency { advanced }
     val wardrobe9 by KeybindSetting("Wardrobe 9", GLFW.GLFW_KEY_9, desc = "Keybind to equip the ninth wardrobe slot.").withDependency { advanced }

    private val wardrobeRegex = Regex("Wardrobe \\((\\d)/(\\d)\\)")
    private val equippedRegex = Regex("Slot (\\d): Equipped")

    init {
        on<GuiEvent.MouseClick> {
            if (screen is AbstractContainerScreen<*> && onClick(screen, click.button()))
                cancel()
        }

        on<GuiEvent.ContainerKeyPress> {
            val container = screen as? AbstractContainerScreen<*> ?: return@on

            val keyCode = input.key

            if (onClick(container, keyCode)) {
                cancel()
            }
        }
    }

     fun onClick(screen: AbstractContainerScreen<*>, keyCode: Int): Boolean {
        val (current, total) = wardrobeRegex.find(screen.title?.string ?: "")?.destructured?.let {
            it.component1().toIntOrNull() to it.component2().toIntOrNull()
        } ?: return false

        if (current == null || total == null) return false

        val equippedIndex = screen.menu.slots.find { equippedRegex.matches(it.item.hoverName.string) }?.index

        val index = when (keyCode) {
            nextPageKeybind.value -> if (current < total) 53 else return false
            previousPageKeybind.value -> if (current > 1) 45 else return false
            unequipKeybind.value -> equippedIndex ?: return false
            else -> {
                val keyIndex = arrayOf(wardrobe1, wardrobe2, wardrobe3, wardrobe4, wardrobe5, wardrobe6, wardrobe7, wardrobe8, wardrobe9)
                    .indexOfFirst { it.value == keyCode }.takeIf { it != -1 } ?: return false

                if (equippedIndex == keyIndex + 36 && disallowUnequippingEquipped) return modMessage("§cArmor already equipped.").let { false }
                keyIndex + 36
            }
        }
        // idk let's try this??????? this "true" is weird
        if (disallowUnequippingEquipped && screen.menu.slots[index].item?.isEmpty == true) return true

        mc.player?.clickSlot(screen.menu.containerId, index)

        if (index in 36..44) {
            if (playSounds) playSoundSettings(soundSettings())
            if (autoClose) mc.player?.closeContainer()
        }

        return true
    }
}