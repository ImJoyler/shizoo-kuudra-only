package shizo.module.impl.floor7.terminals.termGUI

import shizo.module.impl.unusedshit.TerminalSolver
import shizo.utils.equalsOneOf

object PanesGui : TermGui() {

    override fun renderTerminal(slotCount: Int) {
        renderBackground(slotCount, 5, 2)

        for (index in 9..<slotCount) {
            if ((index % 9).equalsOneOf(0, 1, 7, 8) || index !in currentSolution) continue
            renderSlot(index, TerminalSolver.panesColor)
        }
    }
}