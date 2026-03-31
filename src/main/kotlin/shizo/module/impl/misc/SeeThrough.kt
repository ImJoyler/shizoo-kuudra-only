package shizo.module.impl.misc


import shizo.clickgui.settings.impl.BooleanSetting
import shizo.module.impl.Module

object SeeThrough : Module(
    name = "See Thropugh",
    description = "6 through",
    subcategory = "Mining"
) {
    val seeThroughBlocks by BooleanSetting("See Through Blocks", false, "Makes blocks transparent when suffocating inside them.")
}