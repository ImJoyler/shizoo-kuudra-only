package shizo.utils

import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AsyncActions {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun runLater(delayMs: Long, action: () -> Unit) {
        scheduler.schedule({
            Minecraft.getInstance().execute {
                action()
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }
}