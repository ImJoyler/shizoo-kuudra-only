package shizo.module.impl.kuudra.phasethree

import shizo.clickgui.settings.impl.BooleanSetting
import shizo.module.impl.Module

object AutoStun: Module(
    name = "Auto Stun",
    description = "Auto stun ll?",
    subcategory = "Phase three"
){
    private val AutoMount by BooleanSetting("Auto mounts cannon", false, "after cannon close mounts cannon")
    private val AutoRotate by BooleanSetting("Auto rotate", false, "after rotate mounts cannon")
    private val AutoTP by BooleanSetting("TP and Ability", false, "autostunsn")

    //autommount after You purchased human Cannonball! if not in gui you interact with the thing if u are in range + fov until you see in chat You arleady mounted the cannon or Player mounted the cannon
    // important to mountu  need to attack

    //after u dismount you rotate to block head
    // on Clientbound entity tp or pos tp u insta tp and swap to drioll and right click
}