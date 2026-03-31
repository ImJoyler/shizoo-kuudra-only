package shizo.events

import shizo.events.core.Event
import shizo.utils.skyblock.dungeon.terminals.handler.TerminalHandler

abstract class TerminalEvent(val terminal: TerminalHandler) : Event() { // first 2 are packet based can use mixins
    class Open(terminal: TerminalHandler) : TerminalEvent(terminal)
    class Close(terminal: TerminalHandler) : TerminalEvent(terminal)
    class Solve(terminal: TerminalHandler) : TerminalEvent(terminal)
}