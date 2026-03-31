package shizo.utils.clickgui.rendering.joyshit

import shizo.utils.Color
import shizo.utils.clickgui.rendering.NVGRenderer
import kotlin.math.PI
import kotlin.math.sin

object GUIRenderer {
     fun drawGlowingText(text: String, x: Float, y: Float, size: Float, color: Color, pulse: Float) {
        val spread1 = 1.5f
        val alpha1 = 0.15f + (0.15f * pulse)
        val color1 = Color(color.red, color.green, color.blue, alpha1).rgba

        NVGRenderer.text(text, x - spread1, y, size, color1, NVGRenderer.defaultFont)
        NVGRenderer.text(text, x + spread1, y, size, color1, NVGRenderer.defaultFont)
        NVGRenderer.text(text, x, y - spread1, size, color1, NVGRenderer.defaultFont)
        NVGRenderer.text(text, x, y + spread1, size, color1, NVGRenderer.defaultFont)

        val spread2 = 0.5f
        val alpha2 = 0.3f + (0.2f * pulse)
        val color2 = Color(color.red, color.green, color.blue, alpha2).rgba

        NVGRenderer.text(text, x - spread2, y, size, color2, NVGRenderer.defaultFont)
        NVGRenderer.text(text, x + spread2, y, size, color2, NVGRenderer.defaultFont)
        NVGRenderer.text(text, x, y - spread2, size, color2, NVGRenderer.defaultFont)
        NVGRenderer.text(text, x, y + spread2, size, color2, NVGRenderer.defaultFont)

        val r = (color.red + (255 - color.red) * (pulse * 0.5f)).toInt()
        val g = (color.green + (255 - color.green) * (pulse * 0.5f)).toInt()
        val b = (color.blue + (255 - color.blue) * (pulse * 0.5f)).toInt()
        NVGRenderer.text(text, x, y, size, Color(r, g, b).rgba, NVGRenderer.defaultFont)
    }
    // ty ai for this
    // OKAY It sitll was not as cool as i wanted so i rewrote it :D
     fun drawNeonAnimatedGlow(x: Float, y: Float, w: Float, h: Float, color: Color, baseIntensity: Float, radius: Float) {
        if (baseIntensity <= 0.05f) return

        val time = (System.currentTimeMillis() % 2000L) / 2000f
        val pulse = (sin(time * PI * 2).toFloat() * 0.5f) + 0.5f
        val intensity = baseIntensity * (0.7f + (0.5f * pulse))
        val layers = 10
        for (i in 1..layers) {
            val progress = i.toFloat() / layers
            val alpha = (intensity * (1f - progress) * 0.20f).coerceIn(0f, 1f)
            val spread = i * 2f

            NVGRenderer.hollowRect(
                x - spread,
                y - spread,
                w + (spread * 2f),
                h + (spread * 2f),
                2.5f,
                Color(color.red, color.green, color.blue, alpha).rgba,
                radius + (spread * 0.5f)
            )
        }
    }
}