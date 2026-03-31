package shizo.events

import com.mojang.blaze3d.platform.InputConstants
import shizo.events.core.CancellableEvent

class InputEvent(val key: InputConstants.Key) : CancellableEvent()