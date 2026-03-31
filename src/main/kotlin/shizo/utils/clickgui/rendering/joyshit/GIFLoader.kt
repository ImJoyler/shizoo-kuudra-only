package shizo.utils.clickgui.rendering.joyshit

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.resources.ResourceLocation

object GifLoader {
    private val cachedGifs = mutableMapOf<ResourceLocation, GifData?>()
    private val frames = mutableMapOf<ResourceLocation, Int>()

    private val frameStartTimes = mutableMapOf<ResourceLocation, Long>()
    private val unloadTimers = mutableListOf<Pair<ResourceLocation, Int>>()

    init {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!client.isPaused) {
                val iterator = unloadTimers.iterator()
                while (iterator.hasNext()) {
                    val pair = iterator.next()
                    val timer = pair.second
                    if (timer > 1) {
                        unloadTimers[unloadTimers.indexOf(pair)] = Pair(pair.first, timer - 1)
                    } else {
                        frames.remove(pair.first)
                        frameStartTimes.remove(pair.first) // Clean up memory!
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun getFrame(location: ResourceLocation): ResourceLocation? {
        if (!location.path.endsWith(".gif")) return location

        val mc = Minecraft.getInstance()
        val gifData = loadGif(location) ?: return null

        var frame = frames.getOrDefault(location, 0)

        if (!mc.isPaused) {
            val currentTime = System.currentTimeMillis()
            val startTime = frameStartTimes.getOrDefault(location, currentTime)

            if (!frameStartTimes.containsKey(location)) {
                frameStartTimes[location] = currentTime
            }

            val delayMs = gifData.getDelay(frame).toLong()

            if (currentTime - startTime >= delayMs) {
                frame = (frame + 1) % gifData.frames.size
                frames[location] = frame
                frameStartTimes[location] = currentTime
            }

            val existingTimer = unloadTimers.indexOfFirst { it.first == location }
            if (existingTimer != -1) unloadTimers[existingTimer] = Pair(location, 2)
            else unloadTimers.add(Pair(location, 2))
        }

        return gifData.getImage(frame)
    }

    private fun loadGif(location: ResourceLocation): GifData? {
        return cachedGifs.getOrPut(location) {
            try {
                val mc = Minecraft.getInstance()
                val resource = mc.resourceManager.getResourceOrThrow(location)
                val gif = GifDecoder.read(resource.open())

                val extractedFrames = Array(gif.frameCount) { i ->
                    val frameId = ResourceLocation.fromNamespaceAndPath(location.namespace, "${location.path}_frame$i")
                    val bufferedImage = gif.getFrame(i)

                    val width = bufferedImage.width
                    val height = bufferedImage.height
                    val nativeImage = NativeImage(NativeImage.Format.RGBA, width, height, false)

                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            nativeImage.setPixel(x, y, bufferedImage.getRGB(x, y))
                        }
                    }

                    mc.textureManager.register(frameId, DynamicTexture({ frameId.toString() }, nativeImage))

                    val rawDelay = gif.getDelay(i)
                    val safeDelay = if (rawDelay <= 10) 100 else rawDelay

                    Pair(frameId, safeDelay)
                }
                GifData(extractedFrames)
            } catch (e: Exception) {
                println("[Shizo] WARNING: Missing or corrupted GIF at $location")
                null
            }
        }
    }

    data class GifData(val frames: Array<Pair<ResourceLocation, Int>>) {
        fun getImage(frame: Int) = frames[frame].first
        fun getDelay(frame: Int) = frames[frame].second

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as GifData
            return frames.contentEquals(other.frames)
        }

        override fun hashCode(): Int {
            return frames.contentHashCode()
        }
    }
}