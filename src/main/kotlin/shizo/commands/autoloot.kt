package shizo.commands

import com.github.stivais.commodore.Commodore
import shizo.module.impl.dungeon.croesus.Croesus
import shizo.module.impl.kuudra.qolplus.vesuvius.Jewing
import shizo.utils.modMessage

val jewCommand = Commodore("jew") {

    literal("go").runs {
        Jewing.startScript()
    }

    literal("reset").runs {
        Jewing.stopScript()
    }

    literal("forcego").runs {
        Jewing.startScript()
    }

    literal("help").runs {
        modMessage("§0§l[§f§lSlava §b§lIsrael§l§0]§f")
        modMessage("§e/jew go §7- Start the auto-claim sequence.")
        modMessage("§e/jew reset §7- Stop and reset everything.")
    }
}
val croesusCommand = Commodore("/ac", "/autocroesus") {
    literal ("").runs {
        Croesus.startScript()
    }
    literal("go").runs {
        Croesus.startScript()
    }

    literal("forcego").runs {
        Croesus.startScript()
    }

    literal("reset").runs {
        Croesus.stopScript()
    }

    literal("stop").runs {
        Croesus.stopScript()
    }

    literal("api").runs {
        Croesus.refreshApi()
    }

    literal("help").runs {
        modMessage("§6§l[AutoCroesus]§f")
        modMessage("§e/ac go §7- Start the auto-claim sequence.")
        modMessage("§e/ac reset §7- Stop the macro and close menus.")
        modMessage("§e/ac api §7- Manually refresh the Lowest BIN prices from the API.")
    }
}