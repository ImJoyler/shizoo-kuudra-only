package shizo.events

import shizo.events.core.CancellableEvent
import shizo.utils.skyblock.dungeon.tiles.Room

class RoomEnterEvent(val room: Room?) : CancellableEvent()