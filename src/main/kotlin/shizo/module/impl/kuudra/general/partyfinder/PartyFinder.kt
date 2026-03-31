package shizo.module.impl.kuudra.general.partyfinder

import com.github.stivais.commodore.Commodore
import kotlinx.coroutines.launch
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import shizo.Shizo.scope
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.ActionSetting
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.DropdownSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.ChatPacketEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.network.hypixelapi.RequestUtils
import shizo.utils.createSoundSettings
import shizo.utils.handlers.schedule
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.playSoundSettings
import shizo.utils.sendCommand
import shizo.module.impl.kuudra.general.partyfinder.PfUtils.fetchAndDisplayKuudraStats
import shizo.module.impl.kuudra.general.partyfinder.PfUtils.findAccessories
import shizo.module.impl.kuudra.general.partyfinder.PfUtils.findItems

object KuudraPF : Module(
    name = "Kuudra Party Finder",
    description = "Provides stats when a player joins your Kuudra party. Includes autokick.",
) {
    private val statsDisplay by BooleanSetting("Stats display", true, desc = "Displays stats of players who join your party")
    private val sendKickLine by BooleanSetting("Send Kick Line", true, desc = "Sends a line in party chat to kick a player.").withDependency { statsDisplay }

    private val autoRunsReply by BooleanSetting("Auto .runs Reply", true, desc = "Replies with T5 runs when someone types .runs in party or DM.")

    private val sounds by BooleanSetting("Custom Sounds", false, desc = "Plays the selected sound when someone joins.")
    private val soundSettings = createSoundSettings("Party join Sound", "entity.experience_orb.pickup") { sounds }

    private val autoKickGroup by DropdownSetting("Auto Kick Settings")
    private val autoKickToggle by BooleanSetting("Auto Kick", desc = "Automatically kicks players who don't meet requirements.").withDependency { autoKickGroup }

    private val informKicked by BooleanSetting("Inform Kicked", desc = "Informs the player why they were kicked.").withDependency { autoKickGroup && autoKickToggle }
    private val apiOffKick by BooleanSetting("Api Off Kick", true, desc = "Kicks if the player's API is off.").withDependency { autoKickGroup && autoKickToggle }
    private val magicalPowerReq by NumberSetting("Magical Power", 1000, 0, 2000, 50, desc = "Min MP.").withDependency { autoKickGroup && autoKickToggle }
    private val t5RunsReq by NumberSetting("T5 Runs", 50, 0, 5000, 10, desc = "Min T5 runs").withDependency { autoKickGroup && autoKickToggle }
    private val requireGdrag by BooleanSetting("Require GDrag", false, desc = "this will never happen but who cares.").withDependency { autoKickGroup && autoKickToggle }
    private val requireKuudraHeart by BooleanSetting("Require Kuudra's Heart", false, desc = "Kicks if they don't have a Kuudra's Heart.").withDependency { autoKickGroup && autoKickToggle }
    private val requireTux by BooleanSetting("Require Hab 5 Tux", false, desc = "Hab 5 tux.").withDependency { autoKickGroup && autoKickToggle }

    private val kickCache by BooleanSetting("Kick Cache", true, desc = "Caches kicked players to automatically kick when they attempt to rejoin.").withDependency { autoKickGroup && autoKickToggle }
    private val action by ActionSetting("Clear Cache", desc = "Clears the kick list cache.") { kickedList.clear() }.withDependency { autoKickGroup && autoKickToggle && kickCache }

    private val pfRegex = Regex("^Party Finder > (?:\\[.{1,7}])? ?(.{1,16}) joined the (?:dungeon )?group! \\(.*\\)$")
    private val runsCommandRegex = Regex("^(Party|From) > (?:\\[.{1,7}] )?(.{1,16}): \\.runs$")
    private val kickedList = mutableSetOf<String>()

    init {
        on<ChatPacketEvent> {
            val message = value.noControlCodes

            if (autoRunsReply) {
                val commandMatch = runsCommandRegex.find(message)
                if (commandMatch != null) {
                    val channel = commandMatch.groupValues[1]
                    val senderName = commandMatch.groupValues[2]

                    scope.launch {
                        val profile = RequestUtils.getProfile(senderName)
                        val member = profile.getOrNull()?.memberData

                        if (member != null) {
                            val t5Runs = member.kuudraCompletions["infernal"] ?: 0

                            val replyPrefix = if (channel == "Party") "pc" else "msg $senderName"
                            sendCommand("$replyPrefix $senderName: $t5Runs")
                        }
                    }
                }
            }

            if (!statsDisplay && !autoKickToggle) return@on
            val (name) = pfRegex.find(value)?.destructured ?: return@on
            if (name == mc.player?.name?.string) return@on

            scope.launch {
                if (sounds) playSoundSettings(soundSettings())

                val profile = RequestUtils.getProfile(name)

                if (statsDisplay) fetchAndDisplayKuudraStats(profile)

                if (sendKickLine) modMessage(Component.literal("§c[✖] Kick $name").withStyle {
                    it.withClickEvent(ClickEvent.RunCommand("/party kick $name"))
                })

                if (autoKickToggle) {
                    if (kickCache && name in kickedList) {
                        sendCommand("party kick $name")
                        modMessage("Kicked $name since they have been kicked previously.")
                        return@launch
                    }

                    val kickedReasons = mutableListOf<String>()
                    val currentProfile = profile.getOrElse { return@launch modMessage(it.message) }.memberData ?: return@launch modMessage("Could not find member data for $name")

                    if (currentProfile.inventoryApi) {
                        val mp = currentProfile.magicalPower
                        if (mp < magicalPowerReq) kickedReasons.add("Did not meet MP req: $mp/$magicalPowerReq")

                        val t5Runs = currentProfile.kuudraCompletions["infernal"] ?: 0
                        if (t5Runs < t5RunsReq) kickedReasons.add("Not enough T5 runs: $t5Runs/$t5RunsReq")

                        val hasGdrag = currentProfile.pets.pets.any { it.type.equals("GOLDEN_DRAGON", true) }
                        if (requireGdrag && !hasGdrag) kickedReasons.add("No Golden Dragon")

                        if (requireKuudraHeart) {
                            val kuudraHearts = findAccessories(currentProfile, "KUUDRAS_HEART", "KUUDRAS_LUNG", "KUUDRAS_KIDNEY", "KUUDRAS_STOMACH", "KUUDRAS_JAW_BONE", "KUUDRAS_MANDIBLE", "KUUDRAS_HEART_OF_FIRE", "KUUDRAS_GELATINOUS_EYE")
                            if (kuudraHearts.isEmpty()) kickedReasons.add("No Kuudra's Heart")
                        }
                        if (requireTux) {
                            val tuxPieces = findItems(currentProfile, "ELEGANT_TUXEDO_BOOTS", "ELEGANT_TUXEDO_LEGGINGS", "ELEGANT_TUXEDO_CHESTPLATE")

                            val hasHab5 = tuxPieces.any { piece ->
                                piece.lore.any { it.noControlCodes.contains("Habanero Tactics V", ignoreCase = true) }
                            }

                            if (tuxPieces.size < 3) {
                                kickedReasons.add("Missing Elegant Tuxedo pieces (${tuxPieces.size}/3)")
                            } else if (!hasHab5) {
                                kickedReasons.add("No Habanero Tactics 5 on Tuxedo")
                            }
                        }
                    } else if (apiOffKick) {
                        kickedReasons.add("Inventory API is off")
                    }

                    if (kickedReasons.isNotEmpty()) {
                        if (informKicked) {
                            schedule(6) { sendCommand("party kick $name") }
                            sendCommand("pc Kicked $name for: ${kickedReasons.joinToString(", ")}")
                        } else {
                            sendCommand("party kick $name")
                        }

                        kickedList.add(name)
                        modMessage("Kicking $name for: \n${kickedReasons.joinToString("\n")}")
                    }
                }
            }
        }
    }

    fun hi(name: String) {
        scope.launch {
            val profile = RequestUtils.getProfile(name)
            fetchAndDisplayKuudraStats(profile)
        }
    }

    val pfCommand = Commodore("check", "pf","kuudra") {
        runs { name: String ->
            hi(name)
        }
        runs {
            modMessage("§/check <IGN>")
            modMessage("§/pf <IGN>")
            modMessage("§/kuudra <IGN>")
        }
    }
}