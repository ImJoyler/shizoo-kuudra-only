package shizo.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import shizo.clickgui.settings.RenderableSetting
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent
import shizo.clickgui.settings.Saving
import shizo.utils.Color.Companion.brighter
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.animations.LinearAnimation

class BooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String,
) : RenderableSetting<Boolean>(name, desc), Saving {

    override var value: Boolean = default
    var enabled: Boolean by this::value

    private val toggleAnimation = LinearAnimation<Float>(200)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val isModern = ClickGUIModule.theme.value == 1

        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        if (isModern) {
            val switchW = 32f
            val switchH = 16f
            val switchX = x + width - 40f
            val switchY = y + height / 2f - (switchH / 2f)

            val animProgress = toggleAnimation.get(0f, 1f, !enabled).coerceIn(0f, 1f)
            val accent = ClickGUIModule.clickGUIColor

            val bgR = (accent.red * animProgress).toInt().coerceIn(0, 255)
            val bgG = (accent.green * animProgress).toInt().coerceIn(0, 255)
            val bgB = (accent.blue * animProgress).toInt().coerceIn(0, 255)
            val bgA = (0.35f + (0.35f * animProgress)).coerceIn(0f, 1f)

            NVGRenderer.rect(switchX, switchY, switchW, switchH, Color(bgR, bgG, bgB, bgA).rgba, switchH / 2f)

            val borderR = (255 + (accent.red - 255) * animProgress).toInt().coerceIn(0, 255)
            val borderG = (255 + (accent.green - 255) * animProgress).toInt().coerceIn(0, 255)
            val borderB = (255 + (accent.blue - 255) * animProgress).toInt().coerceIn(0, 255)
            val borderA = (0.15f + (0.65f * animProgress)).coerceIn(0f, 1f)

            NVGRenderer.hollowRect(switchX, switchY, switchW, switchH, 1.2f, Color(borderR, borderG, borderB, borderA).rgba, switchH / 2f)

            val knobX = switchX + (switchH / 2f) + ((switchW - switchH) * animProgress)
            val knobY = switchY + (switchH / 2f)
            val knobRadius = 5.5f

            val blurRadius = (8f * animProgress).coerceAtLeast(0f)
            if (blurRadius > 0.05f) {
                NVGRenderer.dropShadow(knobX - knobRadius, knobY - knobRadius, knobRadius * 2, knobRadius * 2, blurRadius, 1f, knobRadius)
            }

            NVGRenderer.circle(knobX, knobY, knobRadius, Colors.WHITE.rgba)

        } else {
            NVGRenderer.rect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, if (isHovered) Colors.gray38.brighter().rgba else Colors.gray38.rgba, 9f)

            if (enabled || toggleAnimation.isAnimating()) {
                val color = ClickGUIModule.clickGUIColor
                NVGRenderer.rect(
                    x + width - 40f,
                    y + height / 2f - 10f,
                    toggleAnimation.get(34f, 9f, enabled),
                    20f,
                    if (isHovered) color.brighter().rgba else color.rgba,
                    9f
                )
            }

            NVGRenderer.hollowRect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, 2f, ClickGUIModule.clickGUIColor.rgba, 9f)
            NVGRenderer.circle(x + width - toggleAnimation.get(30f, 14f, !enabled), y + height / 2f, 6f, Colors.WHITE.rgba)
        }

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            toggleAnimation.start()
            enabled = !enabled
            true
        }
    }

    override val isHovered: Boolean get() {
        val isModern = ClickGUIModule.theme.value == 1
        return if (isModern) {
            isAreaHovered(lastX + width - 40f, lastY + getHeight() / 2f - 8f, 32f, 16f, true)
        } else {
            isAreaHovered(lastX + width - 43f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)
        }
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(enabled)

    override fun read(element: JsonElement, gson: Gson) {
        enabled = element.asBoolean
    }
}