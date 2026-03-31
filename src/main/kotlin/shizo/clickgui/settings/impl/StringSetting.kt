package shizo.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import shizo.clickgui.ClickGUI.gray38
import shizo.clickgui.Panel
import shizo.clickgui.settings.RenderableSetting
import shizo.clickgui.settings.Saving
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.TextInputHandler
import shizo.utils.clickgui.rendering.NVGRenderer

class StringSetting(
    name: String,
    override val default: String = "",
    private var length: Int = 32,
    desc: String
) : RenderableSetting<String>(name, desc), Saving {

    override var value: String = default
        set(value) {
            field = if (value.length <= length) value else return
        }

    private val textInputHandler = TextInputHandler(
        textProvider = { value },
        textSetter = { value = it }
    )

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)

        val rectStartX = x + 6f
        val isModern = ClickGUIModule.theme.value == 1

        NVGRenderer.text(name, rectStartX, y + 5f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val boxY = y + getHeight() - 35f
        val boxW = width - 12f
        val boxH = 30f

        if (isModern) {
            NVGRenderer.rect(rectStartX, boxY, boxW, boxH, Color(0, 0, 0, 0.4f).rgba, 3f)
            NVGRenderer.hollowRect(rectStartX, boxY, boxW, boxH, 1f, Color(255, 255, 255, 0.2f).rgba, 3f)
        } else {
            NVGRenderer.rect(rectStartX, boxY, boxW, boxH, gray38.rgba, 4f)
            NVGRenderer.hollowRect(rectStartX, boxY, boxW, boxH, 2f, ClickGUIModule.clickGUIColor.rgba, 4f)
        }

        textInputHandler.x = rectStartX + 4f
        textInputHandler.y = y + getHeight() - 30f
        textInputHandler.width = width - 16f
        textInputHandler.draw(mouseX, mouseY)

        return getHeight()
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() == 0) textInputHandler.mouseClicked(mouseX, mouseY, click)
        else false
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        textInputHandler.mouseReleased()
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        return textInputHandler.keyPressed(input)
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        return textInputHandler.keyTyped(input)
    }

    override fun getHeight(): Float = Panel.HEIGHT + 28f

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let { value = it }
    }
}