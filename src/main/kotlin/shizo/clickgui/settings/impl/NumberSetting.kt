package shizo.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import shizo.clickgui.ClickGUI.gray38
import shizo.clickgui.Panel
import shizo.clickgui.settings.RenderableSetting
import shizo.clickgui.settings.Saving
import shizo.module.impl.render.ClickGUIModule
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.HoverHandler
import shizo.utils.clickgui.animations.LinearAnimation
import shizo.utils.clickgui.rendering.NVGRenderer
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Setting that lets you pick a number between a range.
 * @author Stivais, Aton
 */
@Suppress("UNCHECKED_CAST")
class NumberSetting<E>(
    name: String,
    override val default: E = 1.0 as E,
    min: Number,
    max: Number,
    increment: Number = 1,
    desc: String,
    private val unit: String = ""
) : RenderableSetting<E>(name, desc), Saving where E : Number, E : Comparable<E> {

    private val incrementDouble = increment.toDouble()
    private val minDouble = min.toDouble()
    private var maxDouble = max.toDouble()

    private val sliderAnim = LinearAnimation<Float>(100)
    private val handler = HoverHandler(150)

    private var displayValue = ""
    private var prevLocation = 0f
    private var valueWidth = -1f
    private var isDragging = false

    private var sliderPercentage = 0f
        set(value) {
            if (sliderPercentage != value) {
                if (!isDragging) {
                    prevLocation = sliderAnim.get(prevLocation, sliderPercentage, false)
                    sliderAnim.start()
                }
                displayValue = getDisplay()
                valueWidth = -1f
            }
            field = value
        }

    override var value: E = default
        set(value) {
            field = roundToIncrement(value).coerceIn(minDouble, maxDouble) as E
            sliderPercentage = ((field.toDouble() - minDouble) / (maxDouble - minDouble)).toFloat()
        }

    init {
        value = default
        displayValue = getDisplay()
    }

    private var valueDouble
        get() = value.toDouble()
        set(value) {
            this.value = value as E
        }

    private var valueInt
        get() = value.toInt()
        set(value) {
            this.value = value as E
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val isModern = ClickGUIModule.theme.value == 1

        handler.handle(x, y + height / 2, width, height / 2, true)

        if (listening) {
            val newPercentage = ((mouseX - (x + 6f)) / (width - 12f)).coerceIn(0f, 1f)
            valueDouble = minDouble + newPercentage * (maxDouble - minDouble)
            sliderPercentage = newPercentage
        }

        if (valueWidth < 0) {
            valueWidth = NVGRenderer.textWidth(displayValue, 16f, NVGRenderer.defaultFont)
        }

        NVGRenderer.text(name, x + 6f, y + height / 2f - 15f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val textX = if (isModern) x + width - valueWidth - 10f else x + width - valueWidth - 4f
        NVGRenderer.text(displayValue, textX, y + height / 2f - 15f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val barX = x + 6f
        val barY = y + 24f
        val barW = width - 12f
        val barH = if (isModern) 6f else 8f

        val animatedPercent = sliderAnim.get(prevLocation, sliderPercentage, false)
        val fillW = animatedPercent * barW

        if (isModern) {
            NVGRenderer.rect(barX, barY, barW, barH, Color(0, 0, 0, 0.4f).rgba, 3f)
            NVGRenderer.hollowRect(barX, barY, barW, barH, 1f, Color(255, 255, 255, 0.2f).rgba, 3f)

            if (fillW > 0) {
                NVGRenderer.rect(barX, barY, fillW, barH, ClickGUIModule.clickGUIColor.rgba, 3f)
            }

            val handleRadius = handler.anim.get(5f, 7f, !isHovered)
            NVGRenderer.circle(barX + fillW, barY + (barH / 2f), handleRadius, Colors.WHITE.rgba)

        } else {
            NVGRenderer.rect(barX, barY, barW, barH, gray38.rgba, 3f)

            if (fillW > 0) {
                NVGRenderer.rect(barX, barY, fillW, barH, ClickGUIModule.clickGUIColor.rgba, 3f)
            }

            NVGRenderer.circle(barX + fillW, barY + (barH / 2f), handler.anim.get(7f, 9f, !isHovered), Colors.WHITE.rgba)
        }

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            listening = true
            isDragging = true
            prevLocation = sliderPercentage
            sliderAnim.start()
            true
        }
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        listening = false
        if (isDragging) {
            isDragging = false
            prevLocation = sliderAnim.get(prevLocation, sliderPercentage, false)
            sliderAnim.start()
        }
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!isHovered) return false

        val amount = when (input.key) {
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_EQUAL -> incrementDouble
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_MINUS -> -incrementDouble
            else -> return false
        }

        if (valueDouble !in minDouble..maxDouble) return false
        valueDouble = (valueDouble + amount).coerceIn(minDouble, maxDouble)
        sliderPercentage = ((valueDouble - minDouble) / (maxDouble - minDouble)).toFloat()
        return true
    }

    override val isHovered: Boolean
        get() = isAreaHovered(lastX, lastY + getHeight() / 2, width, getHeight() / 2, true)

    override fun getHeight(): Float = Panel.HEIGHT + 8f

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asNumber?.let { value = it as E }
    }

    private fun roundToIncrement(x: Number): Double =
        round((x.toDouble() / incrementDouble)) * incrementDouble

    private fun getDisplay(): String =
        if (valueDouble - floor(valueDouble) == 0.0)
            "${(valueInt * 100.0).roundToInt() / 100}${unit}"
        else
            "${(valueDouble * 100.0).roundToInt() / 100.0}${unit}"
}