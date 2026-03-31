package shizo.module.impl.cheats

import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.ListSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.itemId
import shizo.utils.leftClick
import shizo.utils.modMessage
import shizo.utils.rightClick

object AutoClicker : Module(
    name = "Auto Clicker",
    description = "Auto clicker with options for left-click, right-click, or both.",
) {
    // should be fixed
    private val whitelistOnly by BooleanSetting(
        "Whitelist Only", true,
        desc = "Only click when holding whitelisted items (e.g. Terminator LCM)."
    )

    private val cps by NumberSetting(
        "Clicks Per Second", 5.0f, 3.0, 20.0, .5,
        desc = "Speed of clicks when Whitelist Only is active."
    ).withDependency { whitelistOnly }

    private val enableLeftClick by BooleanSetting("Enable Left Click", true, "Enable Left click").withDependency { !whitelistOnly }
    private val leftCps by NumberSetting("Left CPS", 5.0f, 3.0, 22.0, .5, " How fast").withDependency { !whitelistOnly && enableLeftClick }
    private val leftClickKeybind = KeybindSetting("Left Click Key", GLFW.GLFW_KEY_UNKNOWN).withDependency { !whitelistOnly && enableLeftClick }

    private val enableRightClick by BooleanSetting("Enable Right Click", true, " Enable right click").withDependency { !whitelistOnly }
    private val rightCps by NumberSetting("Right CPS", 5.0f, 3.0, 22.0, .5, " How fast").withDependency { !whitelistOnly && enableRightClick }
    private val rightClickKeybind = KeybindSetting("Right Click Key", GLFW.GLFW_KEY_UNKNOWN).withDependency { !whitelistOnly && enableRightClick }

    public val clickList = ListSetting("Click List", mutableListOf("TERMINATOR"))

    private var nextLeftClick = 0L
    private var nextRightClick = 0L

    fun addToList(id: String): Boolean {
        if (clickList.value.contains(id)) return false
        clickList.value.add(id)
        return true
    }

    fun removeFromList(id: String): Boolean {
        return clickList.value.remove(id)
    }

    fun getList(): List<String> {
        return clickList.value
    }
    init {
        this.registerSetting(leftClickKeybind)
        this.registerSetting(rightClickKeybind)
        this.registerSetting(clickList)

        on<TickEvent.Start> {
            if (mc.screen != null) return@on
            if (mc.player == null) return@on

            val player = mc.player!!
            if (player.isUsingItem) return@on

            val now = System.currentTimeMillis()
            val heldItemId = player.mainHandItem?.itemId

            if (whitelistOnly) {
                // i need to learn my left and rights
                if (heldItemId != null && heldItemId in clickList.value && mc.options.keyAttack.isDown) {
                    if (now >= nextLeftClick) {
                        leftClick()
                        val delay = (1000 / cps).toLong()
                        nextLeftClick = now + delay + ((Math.random() - 0.5) * (delay * 0.2)).toLong()
                    }
                }
            } else {
                if (player.isUsingItem) return@on

                if (enableLeftClick && leftClickKeybind.value.isPressed()) {
                    if (now >= nextLeftClick) {

                        leftClick()
                        val delay = (1000 / leftCps).toLong()
                        nextLeftClick = now + delay + ((Math.random() - 0.5) * (delay * 0.2)).toLong()
                    }
                }

                if (enableRightClick && rightClickKeybind.value.isPressed()) {
                    if (now >= nextRightClick) {
                        rightClick()
                        val delay = (1000 / rightCps).toLong()
                        nextRightClick = now + delay + ((Math.random() - 0.5) * (delay * 0.2)).toLong()
                    }
                }
            }
        }
    }

    private fun InputConstants.Key.isPressed(): Boolean {
        val value = this.value
        val window = mc.window
        return if (value > 7) InputConstants.isKeyDown(window, value)
        else GLFW.glfwGetMouseButton(window.handle(), value) == GLFW.GLFW_PRESS
    }
}