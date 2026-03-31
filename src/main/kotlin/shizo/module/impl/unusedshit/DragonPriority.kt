package shizo.module.impl.unusedshit


import shizo.utils.devMessage
import shizo.utils.equalsOneOf
import shizo.utils.modMessage
import shizo.utils.skyblock.dungeon.Blessing
import shizo.utils.skyblock.dungeon.DungeonClass
import shizo.utils.skyblock.dungeon.DungeonUtils

object DragonPriority {

    private val defaultOrder = listOf(WitherDragonsEnum.Red, WitherDragonsEnum.Orange, WitherDragonsEnum.Blue, WitherDragonsEnum.Purple, WitherDragonsEnum.Green)
    private val dragonList = listOf(WitherDragonsEnum.Orange, WitherDragonsEnum.Green, WitherDragonsEnum.Red, WitherDragonsEnum.Blue, WitherDragonsEnum.Purple)

    fun findPriority(spawningDragons: MutableList<WitherDragonsEnum>): WitherDragonsEnum =
        if (!WitherDragons.dragonPriorityToggle) spawningDragons.minBy { defaultOrder.indexOf(it) }
        else sortPriority(spawningDragons)

    private fun sortPriority(spawningDragons: MutableList<WitherDragonsEnum>): WitherDragonsEnum {
        val totalPower = Blessing.POWER.current * (if (WitherDragons.paulBuff) 1.25 else 1.0) + (if (Blessing.TIME.current > 0) 2.5 else 0.0)
        val playerClass = DungeonUtils.currentDungeonPlayer.clazz.apply { if (this == DungeonClass.Unknown) modMessage("§cFailed to get dungeon class.") }

        val priorityList =
            if (totalPower >= WitherDragons.normalPower || (spawningDragons.any { it == WitherDragonsEnum.Purple } && totalPower >= WitherDragons.easyPower))
                if (playerClass.equalsOneOf(DungeonClass.Berserk, DungeonClass.Mage)) dragonList else dragonList.reversed()
            else defaultOrder

        spawningDragons.sortBy { priorityList.indexOf(it) }

        if (totalPower >= WitherDragons.easyPower) {
            if (WitherDragons.soloDebuff == 1 && playerClass == DungeonClass.Tank && (spawningDragons.any { it == WitherDragonsEnum.Purple } || WitherDragons.soloDebuffOnAll))
                spawningDragons.sortByDescending { priorityList.indexOf(it) }
            else if (playerClass == DungeonClass.Healer && (spawningDragons.any { it == WitherDragonsEnum.Purple } || WitherDragons.soloDebuffOnAll))
                spawningDragons.sortByDescending { priorityList.indexOf(it) }
        }

        devMessage("§7Priority: §6$totalPower §7Class: §${playerClass.colorCode}${playerClass.name} §7Dragons: §a${spawningDragons.joinToString(", ") { it.name }} §7-> §c${priorityList.joinToString(", ") { it.name.first().toString() }}")

        return spawningDragons[0]
    }
}

