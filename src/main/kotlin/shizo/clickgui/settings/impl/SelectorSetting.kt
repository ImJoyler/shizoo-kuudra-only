package shizo.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import shizo.module.impl.render.ClickGUIModule
import shizo.clickgui.ClickGUI
import shizo.clickgui.ClickGUI.gray38
import shizo.clickgui.Panel
import shizo.clickgui.settings.RenderableSetting
import shizo.clickgui.settings.Saving
import shizo.utils.Color
import shizo.utils.Color.Companion.brighter
import shizo.utils.Colors
import shizo.utils.clickgui.HoverHandler
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.animations.EaseInOutAnimation
import shizo.utils.clickgui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class SelectorSetting(
    name: String,
    default: String,
    var options: List<String>,
    desc: String
) : RenderableSetting<Int>(name, desc), Saving {

    var onValueChange: ((Int) -> Unit)? = null

    fun onValueChange(action: (Int) -> Unit): SelectorSetting {
        this.onValueChange = action
        return this
    }

    private var _elementWidths: List<Float>? = null

    private val elementWidths: List<Float>
        get() {
            if (_elementWidths == null) {
                _elementWidths = options.map { NVGRenderer.textWidth(it, 16f, NVGRenderer.defaultFont) }
            }
            return _elementWidths!!
        }

    var items: List<String>
        get() = options
        set(value) {
            options = value
            _elementWidths = value.map { NVGRenderer.textWidth(it, 16f, NVGRenderer.defaultFont) }
            if (index >= options.size) index = 0
        }

    override val default: Int = optionIndex(default)

    override var value: Int
        get() = index
        set(value) {
            val oldVal = index
            index = value
            if (oldVal != index) onValueChange?.invoke(index)
        }

    private var index: Int = optionIndex(default)
        set(value) {
            field = if (value > options.size - 1) 0 else if (value < 0) options.size - 1 else value
        }

    var selected: String
        get() = options[index]
        set(value) {
            val oldVal = index
            index = optionIndex(value)
            if (oldVal != index) onValueChange?.invoke(index)
        }

    private val settingAnim = EaseInOutAnimation(200)
    private val hover = HoverHandler(150)
    private val defaultHeight = Panel.HEIGHT
    private var extended = false

    private val color: Color get() = gray38.brighter(1 + hover.percent() / 500f)

    private fun isSettingHovered(index: Int, isModern: Boolean, boxW: Float, rightEdge: Float): Boolean {
        return if (isModern) {
            isAreaHovered(rightEdge - boxW, lastY + 32f + (26f * index), boxW, 26f, true)
        } else {
            isAreaHovered(lastX, lastY + 38f + 32f * index, width, 32f, true)
        }
    }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)

        if (ClickGUIModule.theme.value == 0) width = Panel.WIDTH
        if (options.isEmpty()) return defaultHeight

        val isModern = ClickGUIModule.theme.value == 1
        val currentWidth = elementWidths.getOrElse(index) { 10f }

        if (isModern) {
            NVGRenderer.text(name, x + 6f, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

            val maxTextWidth = elementWidths.maxOrNull() ?: 50f
            val boxW = maxTextWidth + 40f
            val rightEdge = x + width - 15f
            val boxX = rightEdge - boxW
            val boxY = y + 8f
            val boxH = 24f

            val boxColor = if (isHovered) Color(0, 0, 0, 0.6f) else Color(0, 0, 0, 0.4f)

            if (extended || settingAnim.isAnimating()) {
                val listH = options.size * 26f
                val animListH = settingAnim.get(0f, listH, !extended)

                NVGRenderer.rect(boxX, boxY, boxW, boxH + animListH, Color(15, 15, 15, 0.95f).rgba, 5f)
                NVGRenderer.hollowRect(boxX, boxY, boxW, boxH + animListH, 1f, ClickGUIModule.clickGUIColor.rgba, 5f)

                NVGRenderer.rect(boxX + 2f, boxY + boxH, boxW - 4f, 1f, Color(255, 255, 255, 0.1f).rgba)

                NVGRenderer.pushScissor(boxX, boxY + boxH + 1f, boxW, animListH)
                for (i in options.indices) {
                    val optionY = boxY + boxH + 1f + (26f * i)
                    val optionColor = if (i == index) ClickGUIModule.clickGUIColor.rgba else Colors.WHITE.rgba

                    if (isSettingHovered(i, true, boxW, rightEdge)) {
                        NVGRenderer.rect(boxX + 2f, optionY, boxW - 4f, 26f, Color(255, 255, 255, 0.1f).rgba, 3f)
                    }

                    NVGRenderer.text(options[i], boxX + 10f, optionY + 6f, 16f, optionColor, NVGRenderer.defaultFont)
                }
                NVGRenderer.popScissor()
            } else {
                NVGRenderer.rect(boxX, boxY, boxW, boxH, boxColor.rgba, 5f)
                NVGRenderer.hollowRect(boxX, boxY, boxW, boxH, 1f, Color(255, 255, 255, 0.2f).rgba, 5f)
            }

            NVGRenderer.text(selected, boxX + 10f, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

            val chevronX = rightEdge - 20f
            val chevronY = y + defaultHeight / 2f - 6f

            NVGRenderer.push()
            NVGRenderer.translate(chevronX + 6f, chevronY + 6f) // Translate to center of chevron
            NVGRenderer.rotate(settingAnim.get(0f, Math.PI.toFloat(), !extended))
            NVGRenderer.translate(-(chevronX + 6f), -(chevronY + 6f))
            NVGRenderer.image(ClickGUI.chevronImage, chevronX, chevronY, 12f, 12f)
            NVGRenderer.pop()

            return getHeight()
        } else {
            hover.handle(x + width - 20f - currentWidth, y + defaultHeight / 2f - 10f, currentWidth + 12f, 22f, true)
            NVGRenderer.rect(x + width - 20f - currentWidth, y + defaultHeight / 2f - 10f, currentWidth + 12f, 20f, color.rgba, 5f)
            NVGRenderer.hollowRect(x + width - 20f - currentWidth, y + defaultHeight / 2f - 10f, currentWidth + 12f, 20f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 5f)

            NVGRenderer.text(name, x + 6f, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
            NVGRenderer.text(selected, x + width - 14f - currentWidth, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

            if (!extended && !settingAnim.isAnimating()) return defaultHeight

            val displayHeight = getHeight()
            if (settingAnim.isAnimating()) NVGRenderer.pushScissor(x, y, width, displayHeight)

            NVGRenderer.rect(x + 6, y + 37f, width - 12f, options.size * 32f, gray38.rgba, 5f)

            for (i in options.indices) {
                val optionY = y + 38 + 32 * i
                if (i != options.size - 1) NVGRenderer.line(x + 18f, optionY + 32, x + width - 12f, optionY + 32, 1.5f, Colors.MINECRAFT_DARK_GRAY.rgba)
                NVGRenderer.text(options[i], x + width / 2f - elementWidths[i] / 2, optionY + 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
                if (isSettingHovered(i, false, 0f, 0f)) NVGRenderer.hollowRect(x + 6, optionY, width - 12f, 32f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)
            }
            if (settingAnim.isAnimating()) NVGRenderer.popScissor()

            return displayHeight
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() == 0) {
            if (isHovered) {
                settingAnim.start()
                extended = !extended
                return true
            }
            if (!extended) return false

            val isModern = ClickGUIModule.theme.value == 1
            val maxTextWidth = elementWidths.maxOrNull() ?: 50f
            val boxW = maxTextWidth + 40f
            val rightEdge = lastX + width - 15f

            for (index in options.indices) {
                if (isSettingHovered(index, isModern, boxW, rightEdge)) {
                    settingAnim.start()
                    selected = options[index]
                    extended = false
                    return true
                }
            }
        } else if (click.button() == 1) {
            if (isHovered) {
                value = if (index + 1 >= options.size) 0 else index + 1
                return true
            }
        }
        return false
    }

    private fun optionIndex(string: String): Int =
        options.map { it.lowercase() }.indexOf(string.lowercase()).coerceIn(0, options.size - 1)

    override val isHovered: Boolean get() {
        val isModern = ClickGUIModule.theme.value == 1
        return if (isModern) {
            val maxTextWidth = elementWidths.maxOrNull() ?: 50f
            val boxW = maxTextWidth + 40f
            val rightEdge = lastX + width - 15f
            isAreaHovered(rightEdge - boxW, lastY + 8f, boxW, 24f, true)
        } else {
            isAreaHovered(lastX, lastY, width, defaultHeight, true)
        }
    }

    override fun getHeight(): Float {
        val isModern = ClickGUIModule.theme.value == 1
        return if (isModern) {
            settingAnim.get(defaultHeight, defaultHeight + (options.size * 26f), !extended)
        } else {
            settingAnim.get(defaultHeight, options.size * 32f + 44f, !extended)
        }
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(selected)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let { selected = it }
    }
}