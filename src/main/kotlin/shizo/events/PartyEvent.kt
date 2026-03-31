package shizo.events

import shizo.events.core.CancellableEvent


abstract class PartyEvent(val members: List<String>) : CancellableEvent() {

    class Leave(members: List<String>) : PartyEvent(members)
}