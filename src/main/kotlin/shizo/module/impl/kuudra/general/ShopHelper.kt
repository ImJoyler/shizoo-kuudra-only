package shizo.module.impl.kuudra.general

import com.github.stivais.commodore.Commodore
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shizo.Shizo
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.ActionSetting
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.module.impl.kuudra.phaseone.prio.Priority
import shizo.utils.Colors
import shizo.utils.modMessage
import shizo.utils.renderUtils.renderUtils.drawText
import shizo.utils.renderUtils.renderUtils.drawWireFrameBox
import shizo.utils.skyblock.kuudra.KuudraUtils
import java.io.File
import java.io.IOException
import kotlin.collections.iterator

object ShopHelper : Module(
    name = "Shop Helper",
    description = "Highlights Shop and XC tentacles and dynamic safe spots."
) {
    private val drawTentacles by BooleanSetting("Draw Tentacles", true, "")
    private val espColor by ColorSetting("ESP Color", Colors.MINECRAFT_GREEN, true, desc = "").withDependency { drawTentacles }
    private val safeSpotColor by ColorSetting("Safe Spot Color", Colors.MINECRAFT_AQUA, true, desc = "")

    private val locationOptions = arrayListOf("S1", "S2", "S3", "S4", "XC1", "XC2", "XC3", "Safe Shop Default", "Safe XC Default")

    private val targetToSet = SelectorSetting(
        "Location to Set",
        "S1",
        locationOptions,
        "Select the tentacle spawn point or default safe spot."
    )

    private val setTargetPos = ActionSetting("Set Target Position", "Saves your current foot position to the selected target.") {
        val p = mc.player ?: return@ActionSetting
        val pos = p.position()

        val targetName = locationOptions[targetToSet.value]

        savedLocations[targetName] = pos
        saveConfig()
        modMessage("§aSuccessfully set §b$targetName §ato: §f${pos.x.toInt()}, ${pos.y.toInt()}, ${pos.z.toInt()}")
    }

    private val setComboSafeSpot = ActionSetting("Set Safe Spot (Current Combo)", "Saves a safe spot for the currently spawned tentacles.") {
        val p = mc.player ?: return@ActionSetting

        if (spawnedTentacleNames.isEmpty()) {
            modMessage("§cno tentacles currently active, wait for them to spawn first")
            return@ActionSetting
        }

        val isShop = spawnedTentacleNames.any { it.startsWith("S") }
        val prefix = if (isShop) "SHOP_SAFE_" else "XC_SAFE_"
        val baseComboKey = prefix + spawnedTentacleNames.sorted().joinToString("_")

        var finalKey = baseComboKey
        var index = 2
        while (savedLocations.containsKey(finalKey)) {
            finalKey = "${baseComboKey}_$index"
            index++
        }
        savedLocations[finalKey] = p.position()
        saveConfig()

        modMessage("saved safe spot for id: §b$finalKey")

        updateSafeSpot(isShop)
    }

    private val clearComboSafeSpots = ActionSetting("Clear Safe Spots (Combo)", "Deletes all safe spots for the current combo.") {
        clearCurrentCombo()
    }

    init {
        this.registerSetting(targetToSet)
        this.registerSetting(setTargetPos)
        this.registerSetting(setComboSafeSpot)
        this.registerSetting(clearComboSafeSpots)
    }

    val shopHelperCommands = Commodore("sh", "shophelper") {
        literal("clear").runs { clearCurrentCombo() }
        literal("reset").runs { clearCurrentCombo() }
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()
    var savedLocations: MutableMap<String, Vec3> = mutableMapOf()

    private val configFile = File(mc.gameDirectory, "config/shizo/shop-helper-coords.json").apply {
        try {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        } catch (_: Exception) {}
    }

    data class TrackedTentacle(val entity: MagmaCube, val name: String)
    data class SafeSpot(val name: String, val pos: Vec3)

    private val trackedEntities = mutableMapOf<Int, TrackedTentacle>()
    private val spawnedTentacleNames = mutableSetOf<String>()
    private val activeSafeSpots = mutableListOf<SafeSpot>()

    private val blacklistedCubes = mutableSetOf<Int>()
    private val debuggedCubes = mutableSetOf<Int>()
    private var phase1StartTime = 0L

    init {
        loadConfig()

        on<TickEvent.Server> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) {
                if (trackedEntities.isNotEmpty() || phase1StartTime != 0L || blacklistedCubes.isNotEmpty()) {
                    trackedEntities.clear()
                    spawnedTentacleNames.clear()
                    blacklistedCubes.clear()
                    debuggedCubes.clear()
                    activeSafeSpots.clear()
                    phase1StartTime = 0L
                }
                return@on
            }

            if (phase1StartTime == 0L) {
                phase1StartTime = System.currentTimeMillis()
            }

            val elapsed = System.currentTimeMillis() - phase1StartTime
            val level = mc.level ?: return@on
            val player = mc.player ?: return@on

            if (elapsed < 9000L) {
                val earlyCubes = level.getEntitiesOfClass(MagmaCube::class.java, player.boundingBox.inflate(120.0))
                for (cube in earlyCubes) {
                    blacklistedCubes.add(cube.id)
                }
                return@on
            }

            if (elapsed !in 9000L..15000L) return@on

            val task = Priority.lastInstruction?.task ?: return@on
            val isShop = task.contains("SHOP")
            val isXCannon = task.contains("X CANNON") || task.contains("XCANNON")

            if (!isShop && !isXCannon) return@on

            val cubes = level.getEntitiesOfClass(MagmaCube::class.java, player.boundingBox.inflate(120.0))
            for (cube in cubes) {
                if (blacklistedCubes.contains(cube.id)) continue
                if (trackedEntities.containsKey(cube.id)) continue

                if (cube.y > 77.0) continue

                val cx = cube.x
                val cz = cube.z
                var validSpawn = false

                if (isShop && cx in -95.0..-55.0 && cz in -155.0..-110.0) validSpawn = true
                else if (isXCannon && cx in -150.0..-130.0 && cz in -135.0..-120.0) validSpawn = true

                if (validSpawn) {
                    val cubePos = Vec3(cx, 76.0, cz)
                    if (!debuggedCubes.contains(cube.id)) {
                        modMessage(" Found unmapped Magma Cube at X: ${cx.toInt()}, Z: ${cz.toInt()}")
                        debuggedCubes.add(cube.id)
                    }

                    val possibleSpawns = savedLocations.filterKeys {
                        if (isShop) it.startsWith("S") && it.length == 2
                        else it.startsWith("XC") && it.length == 3
                    }

                    val closestTentacle = possibleSpawns.minByOrNull {
                        val dx = it.value.x - cx
                        val dz = it.value.z - cz
                        (dx * dx) + (dz * dz)
                    }

                    if (closestTentacle != null) {
                        val dx = closestTentacle.value.x - cx
                        val dz = closestTentacle.value.z - cz
                        val distSq2D = (dx * dx) + (dz * dz)

                        if (distSq2D < 64.0) {
                            trackedEntities[cube.id] = TrackedTentacle(cube, closestTentacle.key)
                            spawnedTentacleNames.add(closestTentacle.key)

                            modMessage("§c[!] §fSpawned: §b${closestTentacle.key}")
                            updateSafeSpot(isShop)
                        }
                    }
                }
            }

            trackedEntities.entries.removeIf { !it.value.entity.isAlive }
        }

        on<RenderEvent.Extract> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@on

            if (drawTentacles) {
                for ((_, tracked) in trackedEntities) {
                    val entity = tracked.entity
                    val pos = entity.position()
                    val aabb = AABB(pos.x - 1.5, pos.y, pos.z - 1.5, pos.x + 1.5, pos.y + 10.0, pos.z + 1.5)

                    drawWireFrameBox(aabb, espColor, depth = false)
                    drawText("§c${tracked.name}", pos.add(0.0, 10.0, 0.0), 2f, depth = false)
                }
            }
            for (spot in activeSafeSpots) {
                val aabb = AABB(spot.pos.x - 0.5, spot.pos.y, spot.pos.z - 0.5, spot.pos.x + 0.5, spot.pos.y + 1.0, spot.pos.z + 0.5)
                drawWireFrameBox(aabb, safeSpotColor, depth = false)
            }
        }

        on<WorldEvent.Load> {
            trackedEntities.clear()
            spawnedTentacleNames.clear()
            blacklistedCubes.clear()
            debuggedCubes.clear()
            activeSafeSpots.clear()
            phase1StartTime = 0L
        }
    }

    private fun updateSafeSpot(isShop: Boolean) {
        activeSafeSpots.clear()
        if (spawnedTentacleNames.isEmpty()) return

        val prefix = if (isShop) "SHOP_SAFE_" else "XC_SAFE_"
        val baseComboKey = prefix + spawnedTentacleNames.sorted().joinToString("_")

        val matchingKeys = savedLocations.keys.filter {
            it == baseComboKey || (it.startsWith("${baseComboKey}_") && it.substringAfterLast("_").toIntOrNull() != null)
        }

        if (matchingKeys.isNotEmpty()) {
            matchingKeys.forEach { key ->
                activeSafeSpots.add(SafeSpot(key, savedLocations[key]!!))
            }
        } else {
            val fallbackKey = if (isShop) "Safe Shop Default" else "Safe XC Default"
            savedLocations[fallbackKey]?.let { activeSafeSpots.add(SafeSpot(fallbackKey, it)) }
        }
    }

    private fun clearCurrentCombo() {
        if (spawnedTentacleNames.isEmpty()) return
        val isShop = spawnedTentacleNames.any { it.startsWith("S") }
        val prefix = if (isShop) "SHOP_SAFE_" else "XC_SAFE_"
        val baseComboKey = prefix + spawnedTentacleNames.sorted().joinToString("_")

        val keysToRemove = savedLocations.keys.filter {
            it == baseComboKey || (it.startsWith("${baseComboKey}_") && it.substringAfterLast("_").toIntOrNull() != null)
        }.toList()

        keysToRemove.forEach { savedLocations.remove(it) }
        saveConfig()
        modMessage("§cCleared §f${keysToRemove.size} §cspots for: §b$baseComboKey")
        updateSafeSpot(isShop)
    }

    fun saveConfig() {
        Shizo.scope.launch(Dispatchers.IO) {
            try { configFile.bufferedWriter().use { it.write(gson.toJson(savedLocations)) } } catch (e: IOException) { e.printStackTrace() }
        }
    }

    fun loadConfig() {
        try {
            if (!configFile.exists() || configFile.length() == 0L) {
                setupDefaults()
                saveConfig()
                return
            }
            val content = configFile.readText()
            val type = object : TypeToken<MutableMap<String, Vec3>>() {}.type
            savedLocations.putAll(gson.fromJson(content, type) ?: mutableMapOf())
            setupDefaults()
        } catch (e: Exception) { setupDefaults() }
    }

    private fun setupDefaults() {
        val defaults = mapOf(
            "S1" to Vec3(-68.5, 69.0, -151.5),
            "S2" to Vec3(-87.5, 69.0, -134.5),
            "S3" to Vec3(-80.0, 76.0, -130.0),
            "S4" to Vec3(-61.5, 69.0, -127.5),
            "S5" to Vec3(-90.0, 76.0, -140.0),
            "XC1" to Vec3(-141.5, 69.0, -118.5),
            "XC2" to Vec3(-146.5, 69.0, -125.5),
            "XC3" to Vec3(-144.5, 69.0, -140.5),
            "Safe Shop Default" to Vec3(-82.0, 76.0, -122.0),
            "Safe XC Default" to Vec3(-140.0, 76.0, -110.0),
            "SHOP_SAFE_S1_S5" to Vec3(-70.5, 79.0, -134.5),
            "SHOP_SAFE_S5" to Vec3(-70.5, 79.0, -134.5),
            "SHOP_SAFE_S5_2" to Vec3(-71.5, 78.0, -135.5),
            "SHOP_SAFE_S5_3" to Vec3(-85.5, 78.0, -128.5),
            "SHOP_SAFE_S1_S4_S5" to Vec3(-85.3, 78.0, -128.3),
            "SHOP_SAFE_S4_S5" to Vec3(-71.7, 78.0, -135.8),
            "SHOP_SAFE_S4_S5_2" to Vec3(-77.5, 78.0, -135.5),
            "SHOP_SAFE_S4_S5_3" to Vec3(-71.5, 77.0, -136.5),
            "SHOP_SAFE_S4_S5_4" to Vec3(-85.3, 78.0, -128.3),
            "XC_SAFE_XC2" to Vec3(-130.5, 78.0, -114.5),
            "XC_SAFE_XC2_2" to Vec3(-134.5, 78.0, -128.5)
        )

        for ((key, pos) in defaults) {
            if (!savedLocations.containsKey(key)) {
                savedLocations[key] = pos
            }
        }
    }
}