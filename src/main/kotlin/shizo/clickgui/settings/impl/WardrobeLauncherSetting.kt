package shizo.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import shizo.Shizo.mc
import shizo.clickgui.settings.RenderableSetting
import shizo.clickgui.settings.Saving
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.HoverHandler
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.rendering.NVGRenderer
import shizo.utils.clickgui.rendering.joyshit.GUIRenderer.drawNeonAnimatedGlow
import net.minecraft.client.input.MouseButtonEvent

class WardrobeLauncherSetting(
    name: String = "Open Pet Wardrobe",
    description: String = "Opens the 3D Pet Studio"
) : RenderableSetting<Unit>(name, description), Saving {

    override val default = Unit
    override var value = Unit
    private val hoverHandler = HoverHandler(150)

    override fun getHeight(): Float = 40f

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val h = getHeight()
        val accent = ClickGUIModule.clickGUIColor

        val btnX = x + 10f
        val btnY = y + 4f
        val btnW = width - 20f
        val btnH = h - 8f

        hoverHandler.handle(btnX, btnY, btnW, btnH, true)
        val hoverPercent = hoverHandler.percent() / 100f

        val bgAlpha = 0.15f + (0.25f * hoverPercent)
        if (hoverPercent > 0.1f) {
            drawNeonAnimatedGlow(btnX, btnY, btnW, btnH, accent, 0.4f * hoverPercent, 8f)
        }

        NVGRenderer.rect(btnX, btnY, btnW, btnH, Color(accent.red, accent.green, accent.blue, bgAlpha).rgba, 6f)
        NVGRenderer.hollowRect(btnX, btnY, btnW, btnH, 1.5f, accent.rgba, 6f)

        val title = "✦ Pet Wardrobe Studio ✦"
        val textW = NVGRenderer.textWidth(title, 16f, NVGRenderer.defaultFont)
        NVGRenderer.text(title, btnX + (btnW / 2f) - (textW / 2f), btnY + (btnH / 2f) - 6f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        return h
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0) return false
        val btnX = lastX + 10f
        val btnY = lastY + 4f
        val btnW = width - 20f
        val btnH = getHeight() - 8f

        if (isAreaHovered(btnX, btnY, btnW, btnH, true)) {
            mc.setScreen(PetWardrobeScreen(mc.screen))
            return true
        }
        return false
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + 10f, lastY + 4f, width - 20f, getHeight() - 8f, true)

    override fun write(gson: Gson): JsonElement = JsonObject()
    override fun read(element: JsonElement, gson: Gson) {}
}