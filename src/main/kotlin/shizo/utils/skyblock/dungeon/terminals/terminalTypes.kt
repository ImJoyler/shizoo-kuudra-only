package shizo.utils.skyblock.dungeon.terminals

import com.github.stivais.commodore.parsers.CommandParsable
import shizo.utils.modMessage

import net.minecraft.world.item.DyeColor
import shizo.module.impl.floor7.terminals.termGUI.MelodyGui
import shizo.module.impl.floor7.terminals.termGUI.NumbersGui
import shizo.module.impl.floor7.terminals.termGUI.PanesGui
import shizo.module.impl.floor7.terminals.termGUI.RubixGui
import shizo.module.impl.floor7.terminals.termGUI.SelectAllGui
import shizo.module.impl.floor7.terminals.termGUI.StartsWithGui
import shizo.module.impl.floor7.terminals.termGUI.TermGui
import shizo.module.impl.floor7.terminals.termsim.MelodySim
import shizo.module.impl.floor7.terminals.termsim.NumbersSim
import shizo.module.impl.floor7.terminals.termsim.PanesSim
import shizo.module.impl.floor7.terminals.termsim.RubixSim
import shizo.module.impl.floor7.terminals.termsim.SelectAllSim
import shizo.module.impl.floor7.terminals.termsim.StartsWithSim
import shizo.module.impl.floor7.terminals.termsim.TermSimGUI
import shizo.utils.skyblock.dungeon.terminals.handler.MelodyHandler
import shizo.utils.skyblock.dungeon.terminals.handler.NumbersHandler
import shizo.utils.skyblock.dungeon.terminals.handler.PanesHandler
import shizo.utils.skyblock.dungeon.terminals.handler.RubixHandler
import shizo.utils.skyblock.dungeon.terminals.handler.SelectAllHandler
import shizo.utils.skyblock.dungeon.terminals.handler.StartsWithHandler
import shizo.utils.skyblock.dungeon.terminals.handler.TerminalHandler

@CommandParsable
enum class TerminalTypes(
    val termName: String,
    val regex: Regex,
    val windowSize: Int
) : Type {
    PANES("Correct all the panes!", Regex("^Correct all the panes!$"), 45) {
        override fun getSimulator() = PanesSim
        override fun getGUI() = PanesGui
    },
    RUBIX("Change all to same color!", Regex("^Change all to same color!$"), 45) {
        override fun getSimulator() = RubixSim
        override fun getGUI() = RubixGui
    },
    NUMBERS("Click in order!", Regex("^Click in order!$"), 36) {
        override fun getSimulator() = NumbersSim
        override fun getGUI() = NumbersGui
    },
    STARTS_WITH("What starts with:", Regex("^What starts with: '(\\w)'\\?$"), 45) {
        override fun getSimulator() = StartsWithSim()
        override fun getGUI() = StartsWithGui
    },
    SELECT("Select all the", Regex("^Select all the ([\\w ]+) items!$"), 54) {
        override fun getSimulator() = SelectAllSim()
        override fun getGUI() = SelectAllGui
    },
    MELODY("Click the button on time!", Regex("^Click the button on time!$"), 54) {
        override fun getSimulator() = MelodySim
        override fun getGUI() = MelodyGui
    };

    fun openHandler(guiName: String): TerminalHandler? {
        return when (this) {
            PANES -> PanesHandler()
            RUBIX -> RubixHandler()
            NUMBERS -> NumbersHandler()
            STARTS_WITH -> StartsWithHandler(regex.find(guiName)?.groupValues?.get(1) ?: run {
                modMessage("Failed to find letter, please report this!")
                return null
            })
            SELECT -> {
                SelectAllHandler(DyeColor.entries.find {
                    it.name.replace("_", " ")
                        .equals(regex.find(guiName)?.groupValues?.get(1)?.replace("SILVER", "LIGHT GRAY"), true)
                } ?: run {
                    modMessage("Failed to find letter, please report this!")
                    return null
                })
            }
            MELODY -> MelodyHandler()
        }
    }
}

private interface Type {
    fun getSimulator(): TermSimGUI
    fun getGUI(): TermGui
}