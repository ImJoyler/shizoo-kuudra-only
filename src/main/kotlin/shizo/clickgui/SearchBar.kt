package shizo.clickgui

import shizo.utils.Color
import shizo.utils.clickgui.TextInputHandler
import shizo.utils.clickgui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.clickgui.rendering.joyshit.GUIRenderer.drawNeonAnimatedGlow

object SearchBar {

    var currentSearch = ""
        private set(value) {
            if (value == field || value.length > 16) return
            field = value
            searchWidth = NVGRenderer.textWidth(value, 20f, NVGRenderer.defaultFont)
        }

    private var placeHolderWidth = NVGRenderer.textWidth("Search here...", 20f, NVGRenderer.defaultFont)
    private var searchWidth = NVGRenderer.textWidth(currentSearch, 20f, NVGRenderer.defaultFont)

    private val textInputHandler = TextInputHandler(
        textProvider = { currentSearch },
        textSetter = { currentSearch = it }
    )

    fun draw(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        val accent = ClickGUIModule.clickGUIColor
        val isTyping = currentSearch.isNotEmpty()

        if (isTyping) {
            drawNeonAnimatedGlow(x, y, 350f, 40f, accent, 0.45f, 5f)
        } else {
            NVGRenderer.dropShadow(x, y, 350f, 40f, 15f, 2f, 5f)
        }

        NVGRenderer.rect(x, y, 350f, 40f, Color(0, 0, 0, 0.4f).rgba, 5f)

        val borderAlpha = if (isTyping) 0.8f else 0.3f
        NVGRenderer.hollowRect(x, y, 350f, 40f, 1.5f, Color(accent.red, accent.green, accent.blue, borderAlpha).rgba, 5f)

        val textY = y + 10f

        if (currentSearch.isEmpty()) {
            NVGRenderer.text("Search here...", x + 175f - placeHolderWidth / 2, textY, 20f, Color(170, 170, 170).rgba, NVGRenderer.defaultFont)
        }

        textInputHandler.x = (x + 175f - searchWidth / 2 - if (currentSearch.isEmpty()) placeHolderWidth / 2 + 2f else 0f).coerceAtLeast(x + 10f)
        textInputHandler.y = textY - 1
        textInputHandler.width = 330f
        textInputHandler.height = 22f
        textInputHandler.draw(mouseX, mouseY)
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return textInputHandler.mouseClicked(mouseX, mouseY, click)
    }

    fun mouseReleased() {
        textInputHandler.mouseReleased()
    }

    fun keyPressed(input: KeyEvent): Boolean {
        return textInputHandler.keyPressed(input)
    }

    fun keyTyped(input: CharacterEvent): Boolean {
        return textInputHandler.keyTyped(input)
    }
}