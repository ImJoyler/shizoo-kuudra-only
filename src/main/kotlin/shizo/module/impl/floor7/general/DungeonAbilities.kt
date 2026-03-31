package shizo.module.impl.floor7.general

import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.DropdownSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.events.ChatPacketEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.handlers.schedule
import shizo.utils.modMessage
import shizo.utils.skyblock.dungeon.DungeonClass
import shizo.utils.skyblock.dungeon.DungeonUtils

object DungeonAbilities : Module(
    name = "Dungeon Abilities",
    description = "Automatically uses your ability in dungeons.",
) {
    private val autoUlt by BooleanSetting(
        "Auto Ult",
        false,
        desc = "Automatically uses your ultimate ability whenever needed."
    )
    private val classFolder by DropdownSetting("Classes", false, "Chose which class to use this with")
    private val useArcher by BooleanSetting("Archer", default = false, "Archer").withDependency { classFolder  }
    private val useMage by BooleanSetting("Mage", false, "Mage").withDependency { classFolder  }
    private val useBerserk by BooleanSetting("Berserk", false, "Bers").withDependency { classFolder  }
    private val useTank by BooleanSetting("Tank", true, "Tank").withDependency { classFolder  }
    private val useHealer by BooleanSetting("Healer", true, "Healer").withDependency { classFolder  }

    private val abilityKeybind by KeybindSetting(
        "Ability Keybind",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Keybind to use your ability."
    ).onPress {
        if (!DungeonUtils.inDungeons || !enabled || !isCorrectClass()) return@onPress
        dropItem(dropAll = true)
    }

    init {
            on<ChatPacketEvent> {
                if (!autoUlt) {return@on}

                if (!isCorrectClass()) {return@on}

                val delay = when (value) {
                    "⚠ Maxor is enraged! ⚠", "[BOSS] Goldor: You have done it, you destroyed the factory…" -> 1
                    "[BOSS] Sadan: My giants! Unleashed!" -> 25
                    else -> return@on
                }
                dropItem(delay = delay)
                modMessage("§aUsing ult!")
            }
    }
    private fun isCorrectClass(): Boolean {
        if (!DungeonUtils.inDungeons) return false
        val clazz = DungeonUtils.currentDungeonPlayer.clazz
        return when (clazz) {
            DungeonClass.Archer  -> useArcher
            DungeonClass.Mage    -> useMage
            DungeonClass.Berserk -> useBerserk
            DungeonClass.Tank    -> useTank
            DungeonClass.Healer  -> useHealer
            else                 -> false
        }
    }
    private fun dropItem(dropAll: Boolean = false, delay: Int = 1) {
        schedule(delay) {
            mc.player?.drop(dropAll)
        }
    }
}