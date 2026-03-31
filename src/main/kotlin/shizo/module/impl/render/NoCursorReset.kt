package shizo.module.impl.render
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module

object NoCursorReset : Module(
    name = "No Cursor Reset",
    description = "Prevents the cursor from being reset when opening a GUI."
) {
    private var clock = System.currentTimeMillis()
    private var wasNotNull = false

    init {
        on<TickEvent.End> {
            if (mc.screen != null) {
                wasNotNull = true
                clock = System.currentTimeMillis()
            } else if (wasNotNull && mc.screen == null) {
                wasNotNull = false
                clock = System.currentTimeMillis()
            }
        }
    }

    @JvmStatic
    fun shouldHookMouse(): Boolean =
        System.currentTimeMillis() - clock < 150 && enabled
}