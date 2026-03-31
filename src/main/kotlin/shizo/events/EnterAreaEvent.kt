package shizo.events

import shizo.events.core.CancellableEvent
import shizo.utils.skyblock.Island

class EnterAreaEvent(val Island: Island?) : CancellableEvent()