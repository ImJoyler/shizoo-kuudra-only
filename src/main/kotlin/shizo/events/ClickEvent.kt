package shizo.events

import com.mojang.blaze3d.platform.InputConstants
import shizo.events.core.CancellableEvent

class ClickEvent(
    val button: Int,
    val action: Int,
    val modifiers: Int = 0
) : CancellableEvent()

// for button 0 left 1 is right 2 is middle
// for action 1 is press 0 is realease
