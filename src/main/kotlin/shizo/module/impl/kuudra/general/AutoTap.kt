package shizo.module.impl.kuudra.general

import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.StringSetting
import shizo.events.ChatPacketEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.sendCommand

object AutoTap : Module(
    name = "Auto Tap",
    description = "Automatically runs /gfs commands based on chat triggers",
    subcategory = "General"
) {
    private val kuudraToggle by BooleanSetting("Kuudra Auto Tap", true, "Taps to take out items after build")
    private val kuudraTrigger by StringSetting("Kuudra Trigger", "[NPC] Elle: Phew! The Ballista is finally ready!",64 ,"Chat message to look for").withDependency { kuudraToggle }
    private val kuudraItem by StringSetting("Kuudra Item", "toxic_arrow_poison", 64, "Item to take out").withDependency { kuudraToggle }
    private val kuudraAmount by NumberSetting("Kuudra Amount", 32, 0, 64, 1, "Amount to take out").withDependency { kuudraToggle }

    private val stormToggle by BooleanSetting("Storm Auto Tap", true, "Taps to take out items for Storm")
    private val stormTrigger by StringSetting("Storm Trigger", "You are arleady currently picking up some supplies!", 64,"Chat message to look for").withDependency { stormToggle }
    private val stormItem by StringSetting("Storm Item", "twilight_arrow_poison", 64,"Item to take out").withDependency { stormToggle }
    private val stormAmount by NumberSetting("Storm Amount", 32, 0, 64, 1, "Amount to take out").withDependency { stormToggle }

    private val customToggle by BooleanSetting("Custom Auto Tap 1", false, "Your own custom chat trigger")
    private val customTrigger by StringSetting("Trigger 1", "Chat message goes here...", 64, "Chat message to look for").withDependency { customToggle }
    private val customItem by StringSetting("Item 1", "item_id", 64,"Item to take out").withDependency { customToggle }
    private val customAmount by NumberSetting("Amount 1", 32, 0, 64, 1, "Amount to take out").withDependency { customToggle }


    private val customToggle1 by BooleanSetting("Custom Auto Tap 2", false, "Your own custom chat trigger")
    private val customTrigger1 by StringSetting(" Trigger 2", "Chat message goes here...", 64, "Chat message to look for").withDependency { customToggle }
    private val customItem1 by StringSetting("Item 2", "item_id", 64,"Item to take out").withDependency { customToggle }
    private val customAmount1 by NumberSetting("Amount 2", 32, 0, 64, 1, "Amount to take out").withDependency { customToggle }

    private val customToggle2 by BooleanSetting("Custom Auto Tap 3", false, "Your own custom chat trigger")
    private val customTrigger2 by StringSetting("Trigger 3", "Chat message goes here...", 64, "Chat message to look for").withDependency { customToggle }
    private val customItem2 by StringSetting("Custom Item 3", "item_id", 64,"Item to take out").withDependency { customToggle }
    private val customAmount2 by NumberSetting("Custom Amount 3", 32, 0, 64, 1, "Amount to take out").withDependency { customToggle }

    init {
        on<ChatPacketEvent> {
            val msg = value

            if (kuudraToggle && msg.contains(kuudraTrigger)) {
                sendCommand("gfs $kuudraItem ${kuudraAmount.toInt()}")
            }

            if (stormToggle && msg.contains(stormTrigger)) {
                sendCommand("gfs $stormItem ${stormAmount.toInt()}")
            }

            if (customToggle1 && msg.contains(customTrigger1)) {
                sendCommand("gfs $customItem1 ${customAmount1.toInt()}")
            }
            if (customToggle2 && msg.contains(customTrigger2)) {
                sendCommand("gfs $customItem2 ${customAmount2.toInt()}")
            }
            if (customToggle && msg.contains(customTrigger))
            {
                sendCommand("gfs $customItem ${customAmount.toInt()}")
            }
        }
    }
}