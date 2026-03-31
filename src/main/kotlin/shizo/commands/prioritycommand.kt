package shizo.commands

import com.github.stivais.commodore.Commodore
import shizo.module.impl.kuudra.phaseone.prio.PriorityConfig
import shizo.utils.modMessage
import shizo.utils.skyblock.kuudra.KuudraUtils.Crate
import java.util.Stack

val crateCommand = Commodore("pre") {
    val undoStack = Stack<MutableMap<Crate, MutableMap<Crate, PriorityConfig.Instruction>>>()
    val redoStack = Stack<MutableMap<Crate, MutableMap<Crate, PriorityConfig.Instruction>>>()

    fun saveHistory() {
        val snapshot = PriorityConfig.customLogic.mapValues { it.value.toMutableMap() }.toMutableMap()
        undoStack.push(snapshot)
        redoStack.clear()
    }

    runs {
        modMessage("§b§l--- Crate Helper Command Usage ---")
        modMessage("§e/pre set <preset> <missing> <task> <pile> §7- Link a callout to a pile.")
        modMessage("§e/pre remove <preset> <missing> §7- Delete a specific target.")
        modMessage("§e/pre list §7- Show all saved crate instructions.")
        modMessage("§e/pre undo §7- Revert your last change.")
        modMessage("§e/pre redo §7- Reapply a reverted change.")
        modMessage("§c/pre reset §7- Wipes everything and resets to defaults.")
    }

    literal("reset").runs {
        PriorityConfig.reset()
        modMessage("§c§lAll custom logic has been wiped and reset to defaults.")
    }

    literal("set").runs { preset: String, missing: String, task: String, pile: String ->
        val presetCrate = runCatching { Crate.valueOf(preset.uppercase()) }.getOrNull()
        val missingCrate = runCatching { Crate.valueOf(missing.uppercase()) }.getOrNull()
        val pileCrate = runCatching { Crate.valueOf(pile.uppercase()) }.getOrNull()

        if (presetCrate == null || missingCrate == null || pileCrate == null) {
            return@runs modMessage("§cInvalid crate name! Use: X, XC, TRI, SHOP, EQUALS, SLASH, or SQUARE.")
        }

        saveHistory()

        val instruction = PriorityConfig.Instruction(
            task = task.uppercase().replace("_", " "), // Allow underscores for multi-word tasks
            destinationPile = pileCrate
        )

        PriorityConfig.customLogic.getOrPut(presetCrate) { mutableMapOf() }[missingCrate] = instruction
        PriorityConfig.saveConfig()
        modMessage("§aSet §6${missingCrate.name} §afor §6${presetCrate.name} §ato go to pile §e${pileCrate.name}. §7(Undo available)")
    }

    literal("remove").runs { preset: String, missing: String ->
        val presetCrate = runCatching { Crate.valueOf(preset.uppercase()) }.getOrNull()
        val missingCrate = runCatching { Crate.valueOf(missing.uppercase()) }.getOrNull()

        if (presetCrate != null && missingCrate != null) {
            if (PriorityConfig.customLogic[presetCrate]?.containsKey(missingCrate) == true) {
                saveHistory()
                PriorityConfig.customLogic[presetCrate]?.remove(missingCrate)
                PriorityConfig.saveConfig()
                modMessage("§cRemoved §6${missingCrate.name} §cfrom §6${presetCrate.name}.")
            } else {
                modMessage("§eNo instruction found for ${presetCrate.name} -> ${missingCrate.name}.")
            }
        } else {
            modMessage("§cInvalid crate names.")
        }
    }

    literal("undo").runs {
        if (undoStack.isNotEmpty()) {
            val current = PriorityConfig.customLogic.mapValues { it.value.toMutableMap() }.toMutableMap()
            redoStack.push(current)

            PriorityConfig.customLogic = undoStack.pop()
            PriorityConfig.saveConfig()
            modMessage("§eUndo successful.")
        } else {
            modMessage("§cNothing to undo!")
        }
    }

    literal("redo").runs {
        if (redoStack.isNotEmpty()) {
            val current = PriorityConfig.customLogic.mapValues { it.value.toMutableMap() }.toMutableMap()
            undoStack.push(current)

            PriorityConfig.customLogic = redoStack.pop()
            PriorityConfig.saveConfig()
            modMessage("§eRedo successful.")
        } else {
            modMessage("§cNothing to redo!")
        }
    }

    literal("list").runs {
        if (PriorityConfig.customLogic.isEmpty()) return@runs modMessage("§cNo logic loaded.")
        modMessage("§b§l--- Saved Kuudra Logic ---")
        PriorityConfig.customLogic.forEach { (pre, moves) ->
            modMessage("§6${pre.name}:")
            moves.forEach { (missing, inst) ->
                modMessage("  §f${missing.name} -> §b${inst.task} §7(Pile: ${inst.destinationPile.name})")
            }
        }
    }
}