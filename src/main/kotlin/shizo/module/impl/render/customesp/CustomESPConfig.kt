package shizo.module.impl.render.customesp
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shizo.Shizo
import shizo.Shizo.mc
import shizo.utils.Color
import java.io.File
import java.io.IOException
import java.lang.reflect.Type

object CustomESPConfig {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, ColorSerializer())
        .setPrettyPrinting()
        .create()

    private val configFile = File(mc.gameDirectory, "config/shizo/custom-esp.json").apply {
        try {
            parentFile?.mkdirs()
            createNewFile()
        } catch (_: Exception) {
            println("Error creating Custom ESP config file.")
        }
    }

    fun loadConfig() {
        try {
            if (configFile.exists()) {
                with(configFile.bufferedReader().use { it.readText() }) {
                    if (isNotEmpty()) {
                        val loadedRules: MutableList<ESPRule>? = gson.fromJson(
                            this,
                            object : TypeToken<MutableList<ESPRule>>() {}.type
                        )

                        if (loadedRules != null) {
                            CustomESP.rules.clear()
                            CustomESP.rules.addAll(loadedRules)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CustomESP.rules.clear()
        }
    }

    fun saveConfig() {
        Shizo.scope.launch(Dispatchers.IO) {
            try {
                configFile.bufferedWriter().use {
                    it.write(gson.toJson(CustomESP.rules))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private class ColorSerializer : JsonSerializer<Color>, JsonDeserializer<Color> {
        override fun serialize(src: Color, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src.rgba)
        }

        override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Color {
            return try {
                Color(json.asInt)
            } catch (e: Exception) {
                shizo.utils.Colors.WHITE
            }
        }
    }
}