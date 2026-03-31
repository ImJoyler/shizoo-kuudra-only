package shizo.module.impl.kuudra.phaseone.prio

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shizo.Shizo
import shizo.utils.skyblock.kuudra.KuudraUtils.Crate
import java.io.File
import java.io.IOException
import kotlin.collections.iterator

object PriorityConfig {
    data class Instruction(val task: String, val destinationPile: Crate)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File(Shizo.mc.gameDirectory, "config/Shizo/crate-helper-config.json").apply {
        try {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        } catch (_: Exception) {}
    }

    var customLogic: MutableMap<Crate, MutableMap<Crate, Instruction>> = mutableMapOf()

    fun init() {
        loadConfig()
    }

    fun reset() {
        customLogic.clear()
        setupDefaults()
        saveConfig()
    }

    fun saveConfig() {
        Shizo.scope.launch(Dispatchers.IO) {
            try {
                configFile.bufferedWriter().use { it.write(gson.toJson(customLogic)) }
            } catch (e: IOException) { e.printStackTrace() }
        }
    }

    private fun loadConfig() {
        try {
            setupDefaults()

            if (!configFile.exists() || configFile.length() == 0L) {
                saveConfig()
                return
            }

            val content = configFile.readText()
            val type = object : TypeToken<MutableMap<Crate, MutableMap<Crate, Instruction>>>() {}.type
            val loadedConfig: MutableMap<Crate, MutableMap<Crate, Instruction>> = gson.fromJson(content, type) ?: mutableMapOf()

            for ((preset, instructions) in loadedConfig) {
                if (customLogic.containsKey(preset)) {
                    customLogic[preset]?.putAll(instructions)
                } else {
                    customLogic[preset] = instructions
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDefaults() {
        customLogic[Crate.SLASH] = mutableMapOf(
            Crate.SLASH to Instruction("GO SHOP", Crate.SHOP),
            Crate.X to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.TRI to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.EQUALS to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SHOP to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SQUARE to Instruction("GO XCANNON", Crate.XC),
            Crate.XC to Instruction("GO SQUARE", Crate.SQUARE)
        )

        customLogic[Crate.X] = mutableMapOf(
            Crate.X to Instruction("GO SHOP", Crate.SHOP),
            Crate.TRI to Instruction("GO X CANNON", Crate.XC),
            Crate.SLASH to Instruction("GO X CANNON", Crate.XC),
            Crate.EQUALS to Instruction("GO X CANNON", Crate.XC),
            Crate.SHOP to Instruction("GO X CANNON", Crate.XC),
            Crate.SQUARE to Instruction("GO XCANNON", Crate.XC),
            Crate.XC to Instruction("GO SQUARE", Crate.SQUARE)
        )

        customLogic[Crate.TRI] = mutableMapOf(
            Crate.TRI to Instruction("GO SHOP", Crate.SHOP),
            Crate.X to Instruction("GO X CANNON", Crate.XC),
            Crate.SLASH to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.EQUALS to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SHOP to Instruction("GO X CANNON", Crate.XC),
            Crate.SQUARE to Instruction("GO SHOP", Crate.SHOP),
            Crate.XC to Instruction("GO SHOP", Crate.SHOP)
        )

        customLogic[Crate.EQUALS] = mutableMapOf(
            Crate.EQUALS to Instruction("GO SHOP", Crate.SHOP),
            Crate.X to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.TRI to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SLASH to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SHOP to Instruction("GO SQUARE", Crate.SQUARE),
            Crate.SQUARE to Instruction("GO SHOP", Crate.SHOP),
            Crate.XC to Instruction("GO SHOP", Crate.SHOP)
        )
    }
}