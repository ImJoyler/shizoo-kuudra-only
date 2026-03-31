package shizo.events

import shizo.events.core.Event

abstract class WorldEvent : Event() {
    class Load : WorldEvent()

    class Unload : WorldEvent()
}