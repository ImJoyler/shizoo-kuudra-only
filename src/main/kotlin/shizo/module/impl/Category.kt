package shizo.module.impl

import net.fabricmc.loader.api.FabricLoader

@ConsistentCopyVisibility
data class Category private constructor(val name: String) {
    companion object {

        /**
         * Map containing all the categories, with the key being the name.
         */
        val categories: LinkedHashMap<String, Category> = linkedMapOf()

        @JvmField
        val DUNGEON = custom(name = "Dungeon")
        var F7 = custom(name = "Floor 7")
        val RENDER = custom(name = "Render")
        val KUUDRA = custom(name = "Kuudra")
        val PLAYER = custom(name = "Misc")
        val NOTLOAD = custom(name = "Dev")
        val CHEATS = custom(name = "Cheats")
        // DONT RAPE ME CUBEY FOR THIS I KNOW ITS STUPID
        init {
            if (!FabricLoader.getInstance().isDevelopmentEnvironment) {
                categories.remove("Dungeon")
                categories.remove("Dev")
                categories.remove("Floor 7")
            }
        }

        /**
         * Returns a category with name provided.
         *
         * If a category with the same name has already been made, it won't reallocate.
         * Otherwise, it will be added to [categories].
         */
        fun custom(name: String): Category {
            return categories.getOrPut(name) { Category(name) }
        }
    }
}