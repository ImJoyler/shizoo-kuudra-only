package shizo.utils.clickgui.animations

import kotlin.compareTo
import kotlin.div
import kotlin.math.pow
import kotlin.times

class EaseInOutAnimation(duration: Long) : Animation<Float>(duration) {
    override fun get(start: Float, end: Float, reverse: Boolean): Float {
        if (!isAnimating()) return if (reverse) start else end
        return if (reverse) end + (start - end) * easeInOutCubic() else start + (end - start) * easeInOutCubic()
    }

    private fun easeInOutCubic(): Float {
        val x = getPercent() / 100f
        return if (x < 0.5) {
            4 * x * x * x
        } else {
            1 - (-2 * x + 2).pow(3) / 2
        }
    }
}