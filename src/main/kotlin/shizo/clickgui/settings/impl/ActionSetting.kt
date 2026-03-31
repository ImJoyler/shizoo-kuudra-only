package shizo.clickgui.settings.impl

import shizo.clickgui.ClickGUI.gray38
import shizo.clickgui.settings.RenderableSetting
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Color.Companion.darker
import shizo.utils.Colors
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class ActionSetting(
    name: String,
    desc: String,
    override val default: () -> Unit = {}
) : RenderableSetting<() -> Unit>(name, desc) {

    override var value: () -> Unit = default
    var action: () -> Unit by this::value

    private val textWidth by lazy { NVGRenderer.textWidth(name, 16f, NVGRenderer.defaultFont) }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val isModern = ClickGUIModule.theme.value == 1

        if (isModern) {
            NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

            val btnWidth = 60f
            val btnX = x + width - btnWidth - 15f
            val btnY = y + height / 2f - 12f

            val bg = if (isHovered) Color(0, 0, 0, 0.6f) else Color(0, 0, 0, 0.4f)
            NVGRenderer.rect(btnX, btnY, btnWidth, 24f, bg.rgba, 3f)
            NVGRenderer.hollowRect(btnX, btnY, btnWidth, 24f, 1f, Color(255, 255, 255, 0.2f).rgba, 3f)

            val runTextWidth = NVGRenderer.textWidth("Click", 16f, NVGRenderer.defaultFont)
            NVGRenderer.text("Click", btnX + (btnWidth / 2f) - (runTextWidth / 2f), btnY + 4f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        } else {
            NVGRenderer.rect(x + 4f, y + height / 2f - 13f, width - 8f, 26f, gray38.rgba, 6f)
            NVGRenderer.hollowRect(x + 4f, y + height / 2f - 13f, width - 8f, 26f, 2f, ClickGUIModule.clickGUIColor.rgba, 6f)
            NVGRenderer.text(name, x + width / 2f - textWidth / 2, y + height / 2f - 8f, 16f, if (isHovered) Colors.WHITE.darker().rgba else Colors.WHITE.rgba, NVGRenderer.defaultFont)
        }

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            action()
            true
        }
    }

    override val isHovered: Boolean get() {
        val isModern = ClickGUIModule.theme.value == 1
        return if (isModern) {
            isAreaHovered(lastX + width - 75f, lastY + getHeight() / 2f - 12f, 60f, 24f, true)
        } else {
            isAreaHovered(lastX + 4f, lastY + getHeight() / 2f - 13f, width - 8f, 26f, true)
        }
    }
}