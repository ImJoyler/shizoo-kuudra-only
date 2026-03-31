package shizo.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mojang.blaze3d.platform.InputConstants
import shizo.Shizo.mc
import shizo.clickgui.ClickGUI.gray38
import shizo.clickgui.settings.RenderableSetting
import shizo.clickgui.settings.Saving
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.rendering.NVGRenderer
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

class KeybindSetting(
    name: String,
    override val default: InputConstants.Key,
    desc: String
) : RenderableSetting<InputConstants.Key>(name, desc), Saving {

    constructor(name: String, defaultKeyCode: Int, desc: String = "") : this(name, InputConstants.Type.KEYSYM.getOrCreate(defaultKeyCode), desc)

    override var value: InputConstants.Key = default
    var onPress: (() -> Unit)? = null
    private var keyNameWidth = -1f

    private var key: InputConstants.Key
        get() = value
        set(newKey) {
            if (newKey == value) return
            value = newKey
            keyNameWidth = NVGRenderer.textWidth(value.displayName.string, 16f, NVGRenderer.defaultFont)
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        if (keyNameWidth < 0) keyNameWidth = NVGRenderer.textWidth(value.displayName.string, 16f, NVGRenderer.defaultFont)
        val height = getHeight()
        val isModern = ClickGUIModule.theme.value == 1

        val rectWidth = keyNameWidth + 20f
        val rectHeight = 24f
        val rectY = y + height / 2f - (rectHeight / 2f)

        val rectX = if (isModern) (x + width - rectWidth - 15f) else (x + width - 20 - keyNameWidth)

        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        if (isModern) {
            val bg = if (isHovered) Color(0, 0, 0, 0.6f) else Color(0, 0, 0, 0.4f)
            NVGRenderer.rect(rectX, rectY, rectWidth, rectHeight, bg.rgba, 4f)
            NVGRenderer.hollowRect(rectX, rectY, rectWidth, rectHeight, 1f, Color(255, 255, 255, 0.2f).rgba, 4f)
        } else {
            NVGRenderer.rect(rectX, rectY, rectWidth, rectHeight, gray38.rgba, 5f)
            NVGRenderer.hollowRect(rectX - 1, rectY - 1, rectWidth + 2f, rectHeight + 2f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)
        }

        val textColor = if (listening) Colors.MINECRAFT_YELLOW.rgba else Colors.WHITE.rgba
        NVGRenderer.text(value.displayName.string, rectX + (rectWidth / 2f) - (keyNameWidth / 2f), rectY + (rectHeight / 2f) - 8f, 16f, textColor, NVGRenderer.defaultFont)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (listening) {
            key = InputConstants.Type.MOUSE.getOrCreate(click.button())
            listening = false
            return true
        } else if (click.button() == 0 && isHovered) {
            listening = true
            return true
        }
        return false
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!listening) return false

        when (input.key) {
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE -> key = InputConstants.UNKNOWN
            GLFW.GLFW_KEY_ENTER -> listening = false
            else -> key = InputConstants.getKey(input)
        }

        listening = false
        return true
    }

    fun onPress(block: () -> Unit): KeybindSetting {
        onPress = block
        return this
    }

    fun isDown(): Boolean =
        value != InputConstants.UNKNOWN && InputConstants.isKeyDown(mc.window, value.value)

    override val isHovered: Boolean
        get() {
            val isModern = ClickGUIModule.theme.value == 1
            val rectWidth = keyNameWidth + 20f
            val rectHeight = 24f
            val rectX = if (isModern) (lastX + width - rectWidth - 15f) else (lastX + width - 20 - keyNameWidth)
            val rectY = lastY + getHeight() / 2f - (rectHeight / 2f)

            return isAreaHovered(rectX, rectY, rectWidth, rectHeight, true)
        }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value.name)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let { value = InputConstants.getKey(it) }
    }

    override fun reset() {
        value = default
    }
}