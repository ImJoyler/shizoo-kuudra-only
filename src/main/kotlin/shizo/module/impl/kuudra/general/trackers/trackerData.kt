package shizo.module.impl.kuudra.general.trackers

import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shizo.Shizo
import java.io.File
import kotlin.collections.get

object TrackerData {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File(Shizo.mc.gameDirectory, "config/shizo/tracker-data.json").apply {
        try {
            parentFile?.mkdirs()
            if (!exists()) writeText("{}")
        } catch (_: Exception) {}
    }

    var infernalKeys = 0
    var kuudraRuns = 0

    fun load() {
        try {
            val content = configFile.readText()
            val data = gson.fromJson(content, Map::class.java) ?: return
            infernalKeys = (data["infernalKeys"] as? Double)?.toInt() ?: 0
            kuudraRuns = (data["kuudraRuns"] as? Double)?.toInt() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun save() {
        Shizo.scope.launch(Dispatchers.IO) {
            try {
                val data = mapOf(
                    "infernalKeys" to infernalKeys,
                    "kuudraRuns" to kuudraRuns
                )
                configFile.writeText(gson.toJson(data))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}