package shizo.clickgui.settings.impl

import shizo.module.impl.Module
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import shizo.Shizo.mc
import shizo.clickgui.ClickGUI
import shizo.clickgui.ClickGUI.gray38
import shizo.clickgui.HudManager
import shizo.clickgui.settings.RenderableSetting
import shizo.clickgui.settings.Saving
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Color.Companion.brighter
import shizo.utils.clickgui.HoverHandler
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.animations.LinearAnimation
import shizo.utils.clickgui.rendering.NVGRenderer
import shizo.utils.Colors
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.MouseButtonEvent

class HUDSetting(
    name: String,
    hud: HudElement,
    private val toggleable: Boolean = false,
    description: String,
    val module: Module,
) : RenderableSetting<HudElement>(name, description), Saving {

    constructor(
        name: String,
        x: Int,
        y: Int,
        scale: Float,
        toggleable: Boolean,
        description: String,
        module: Module,
        draw: GuiGraphics.(Boolean) -> Pair<Int, Int>
    ) : this(name, HudElement(x, y, scale, !toggleable, draw), toggleable, description, module)

    override val default: HudElement = hud
    override var value: HudElement = default

    val isEnabled: Boolean get() = module.enabled && value.enabled

    private val toggleAnimation = LinearAnimation<Float>(200)
    private val hoverHandler = HoverHandler(150)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val isModern = ClickGUIModule.theme.value == 1

        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val iconX = x + width - 30f
        val iconY = y + height / 2f - 12f
        hoverHandler.handle(iconX, iconY, 24f, 24f, true)

        val imageSize = 24f + (6f * hoverHandler.percent() / 100f)
        val offset = (imageSize - 24f) / 2f

        NVGRenderer.image(ClickGUI.movementImage, iconX - offset, iconY - offset, imageSize, imageSize)

        if (toggleable) {
            if (isModern) {
                val switchW = 32f
                val switchH = 16f
                val switchX = x + width - 70f
                val switchY = y + height / 2f - (switchH / 2f)

                val animProgress = toggleAnimation.get(0f, 1f, !value.enabled).coerceIn(0f, 1f)
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
                val hovered = isAreaHovered(lastX + width - 70f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)
                NVGRenderer.rect(x + width - 70f, y + height / 2f - 10f, 34f, 20f, if (hovered) gray38.brighter().rgba else gray38.rgba, 9f)

                if (!value.enabled || toggleAnimation.isAnimating()) {
                    val color = ClickGUIModule.clickGUIColor
                    NVGRenderer.rect(
                        x + width - 70f,
                        y + height / 2f - 10f,
                        toggleAnimation.get(34f, 9f, value.enabled),
                        20f,
                        if (hovered) color.brighter().rgba else color.rgba,
                        9f
                    )
                }

                NVGRenderer.hollowRect(x + width - 70f, y + height / 2f - 10f, 34f, 20f, 2f, ClickGUIModule.clickGUIColor.rgba, 9f)
                NVGRenderer.circle(x + width - toggleAnimation.get(30f, 14f, !value.enabled) - 30f, y + height / 2f, 6f, Colors.WHITE.rgba)
            }
        }
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0) return false
        val isModern = ClickGUIModule.theme.value == 1

        if (isHovered) {
            mc.setScreen(HudManager)
            return true
        }

        if (toggleable) {
            val toggleHovered = if (isModern) {
                isAreaHovered(lastX + width - 70f, lastY + getHeight() / 2f - 8f, 32f, 16f, true)
            } else {
                isAreaHovered(lastX + width - 70f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)
            }

            if (toggleHovered) {
                toggleAnimation.start()
                value.enabled = !value.enabled
                return true
            }
        }
        return false
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 30f, lastY + getHeight() / 2f - 12f, 24f, 24f, true)

    override fun write(gson: Gson): JsonElement = JsonObject().apply {
        addProperty("x", value.x)
        addProperty("y", value.y)
        addProperty("scale", value.scale)
        addProperty("enabled", value.enabled)
    }

    override fun read(element: JsonElement, gson: Gson) {
        if (element !is JsonObject) return
        value.x = element.get("x")?.asInt ?: value.x
        value.y = element.get("y")?.asInt ?: value.y
        value.scale = element.get("scale")?.asFloat ?: value.scale
        value.enabled = if (toggleable) element.get("enabled")?.asBoolean ?: value.enabled else true
    }
}