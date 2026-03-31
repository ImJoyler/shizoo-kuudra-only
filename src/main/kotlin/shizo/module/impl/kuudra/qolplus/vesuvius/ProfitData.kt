package shizo.module.impl.kuudra.qolplus.vesuvius

import com.google.gson.GsonBuilder
import shizo.Shizo
import java.io.File

object ProfitData {

    var sessionProfit = 0.0
    var sessionChests = 0
    var sessionPaid = 0
    var sessionFree = 0

    var sessionStartTime = -1L

    var lifetimeProfit = 0.0
    var lifetimeChests = 0
    var lifetimePaid = 0
    var lifetimeFree = 0

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File(Shizo.mc.gameDirectory, "config/shizo/kuudra_profit.json")

    fun markSessionStart() {
        if (sessionStartTime == -1L) {
            sessionStartTime = System.currentTimeMillis()
        }
    }

    fun getSessionProfitPerHour(): Double {
        if (sessionStartTime == -1L) return 0.0

        val timeAliveMillis = System.currentTimeMillis() - sessionStartTime
        val hoursAlive = timeAliveMillis / 3600000.0

        if (hoursAlive <= 0.001) return 0.0
        return sessionProfit / hoursAlive
    }

    fun addProfit(amount: Double, isPaid: Boolean) {
        sessionProfit += amount
        sessionChests++
        lifetimeProfit += amount
        lifetimeChests++
        if (isPaid) {
            sessionPaid++
            lifetimePaid++
        } else {
            sessionFree++
            lifetimeFree++
        }
        save()
    }

    fun save() {
        try {
            configFile.parentFile?.mkdirs()
            val data = mapOf(
                "profit" to lifetimeProfit,
                "chests" to lifetimeChests,
                "paid" to lifetimePaid,
                "free" to lifetimeFree
            )
            configFile.writeText(gson.toJson(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load() {
        if (!configFile.exists()) return
        try {
            val text = configFile.readText()
            val map = gson.fromJson(text, Map::class.java) as Map<String, Double>
            lifetimeProfit = map["profit"] ?: 0.0
            lifetimeChests = (map["chests"] ?: 0.0).toInt()
            lifetimePaid = (map["paid"] ?: 0.0).toInt()
            lifetimeFree = (map["free"] ?: 0.0).toInt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun resetSession() {
        sessionProfit = 0.0
        sessionChests = 0
        sessionPaid = 0
        sessionFree = 0
        sessionStartTime = System.currentTimeMillis()
    }
}