    package shizo.commands

    import com.github.stivais.commodore.Commodore
    import com.github.stivais.commodore.utils.GreedyString
    import shizo.Shizo
    import shizo.Shizo.mc
    import shizo.module.impl.cheats.AutoClicker
    import shizo.utils.itemId
    import shizo.utils.modMessage

    val autoClickCommand = Commodore("autoclicker") {
        runs {
            modMessage("§b§lAuto Clicker Commands:")
            modMessage("§9/autoclicker add [id] §f- Add held item or specified ID")
            modMessage("§9/autoclicker remove [id] §f- Remove held item or specified ID")
            modMessage("§9/autoclicker list §f- View whitelisted items")
            modMessage("§9/autoclicker reset §f- Clear the whitelist")
        }
        literal("add").runs { item: GreedyString? ->
            val heldItem = mc.player?.mainHandItem
            val idToCheck = item?.string?.uppercase()

            val finalId = if (idToCheck != null) {
            idToCheck
        } else {
            if (heldItem == null || heldItem.isEmpty) {
                return@runs modMessage("§cPlease hold an item or type an ID.")
            }
            val heldId = heldItem.itemId
            if (heldId == null) {
                return@runs modMessage("§cThe item you are holding has no valid ID.")
            }
            heldId
        }
            if (AutoClicker.addToList(finalId)) {
                modMessage("§aAdded §f$finalId §ato Auto Clicker list.")
                Shizo.moduleConfig.save()
            } else {
                modMessage("§c$finalId is already in the list.")
            }
        }
        literal("remove").runs { item: GreedyString? ->
            val idToCheck = item?.string?.uppercase()

            val finalId = if (idToCheck != null) {
                idToCheck
            } else {
                // Try to remove held item if no arg provided
                val heldId = mc.player?.mainHandItem?.itemId
                if (heldId == null) {
                    return@runs modMessage("§cPlease hold an item or type an ID to remove.")
                }
                heldId
            }

            if (AutoClicker.removeFromList(finalId)) {
                modMessage("§bRemoved §f$finalId §bfrom Auto Clicker list.")
                Shizo.moduleConfig.save()
            } else {
                modMessage("§c$finalId not found in list.")
            }
        }
        literal("reset").runs {
            val list = AutoClicker.clickList.value
            if (list.isEmpty()) {
                modMessage("§cThe list is already empty.")
            } else {
                val count = list.size
                list.clear()
                Shizo.moduleConfig.save()
                modMessage("§aReset Auto Clicker whitelist (Removed $count items).")
            }
        }
        literal("list").runs {
            val list = AutoClicker.getList()
            if (list.isEmpty()) {
                modMessage("§cAuto Clicker list is empty.")
            } else {
                modMessage("§aAuto Clicker Items: §f${list.joinToString(", ")}")
            }
        }
    }