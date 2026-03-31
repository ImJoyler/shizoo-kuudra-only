//package shizo.commands
//
//
//import com.github.stivais.commodore.Commodore
//import com.github.stivais.commodore.utils.GreedyString
//import net.minecraft.world.phys.AABB
//import net.minecraft.world.phys.BlockHitResult
//import net.minecraft.world.phys.HitResult
//import net.minecraft.world.phys.Vec3
//import shizo.Shizo.mc
//import shizo.module.impl.cheats.autop3.AutoP3
//import shizo.module.impl.cheats.autop3.Ring
//import shizo.module.impl.cheats.autop3.RingsManager
//import shizo.module.impl.cheats.autop3.RingsManager.format
//import shizo.module.impl.cheats.autop3.actions.*
//import shizo.module.impl.cheats.autop3.arguments.*
//import shizo.utils.createClickableText
//import shizo.utils.modMessage
//import shizo.utils.skyblock.dungeon.DungeonUtils
//import kotlin.math.round
//
//
//
//val ringTypes: List<String> = listOf("velo", "walk", "look", "stop", "bonzo", "boom", "hclip", "block", "edge", "lavaclip", "jump", "align", "command", "blink")
//val removedRings: MutableList<MutableList<Ring>> = mutableListOf()
//
//val autoP3Commands = Commodore("p3") {
//    literal("help").runs {
//        modMessage("""
//            List of AutoP3 commands:
//              §7/p3 create §7<§bnamee§7> §8: §rcreates a route
//              §7/p3 add §7<§btype§7> [§bdepth§7] [§bargs..§7] §8: §radds a ring (§7/p3 add §rfor info)
//              §7/p3 em §8: §rmakes rings inactiv
//              §7/p3 bem §8: §rtoggles blink edit mode
//              §7/p3 toggle §8: §rtoggles the module
//              §7/p3 remove §7<§brange§7>§r §8: §rremoves rings in range (default value - 2)
//              §7/p3 undo §8: §rremoves last placed ring
//              §7/p3 redo §8: §radds back removed rings
//              §7/p3 clearroute §8: §rclears current route
//              §7/p3 clear §8: §rclears ALL routes
//              §7/p3 load §7[§broute§7]§r §8: §rloads selected route/routes
//              §7/p3 save §8: §rsaves current route
//              §7/p3 help §8: §rshows this message
//        """.trimIndent())
//    }
//
//    literal("create").runs { name: String ->
//        RingsManager.createRoute(name)
//    }
//
//    literal("em").runs {
//        RingsManager.ringEditMode = !RingsManager.ringEditMode
//        modMessage("EditMode ${if (RingsManager.ringEditMode) "§aenabled" else "§cdisabled"}")
//    }
//
//    literal("bem").runs {
//        RingsManager.blinkEditMode = !RingsManager.blinkEditMode
//        modMessage("Blink edit ${if (RingsManager.blinkEditMode) "§aenabled" else "§cdisabled"}")
//    }
//
//    literal("toggle").runs {
//        AutoP3.toggle()
//    }
//
//    literal("remove", "rm").runs { range: Double? ->
//        if (!check) return@runs
//        val player = mc.player ?: return@runs
//        val originalRings = RingsManager.currentRoute.first().rings.toList()
//
//        val r = range ?: 2.0
//
//        val playerBox = AABB(player.x - r, player.y - r, player.z - r, player.x + r, player.y + r, player.z + r)
//
//        val allRings = originalRings.filter { ring ->
//            !playerBox.intersects(ring.boundingBox())
//        }
//
//        RingsManager.currentRoute.first().rings = allRings.toMutableList()
//
//        val rmRings = originalRings - allRings.toSet()
//        if (rmRings.isEmpty()) return@runs modMessage("Nothing to remove")
//
//        removedRings.add(rmRings.toMutableList())
//        modMessage("Removed ${rmRings.joinToString(", ") { it.format() }}")
//        RingsManager.loadSaveRoute()
//    }
//
//    literal("undo").runs {
//        if (!check) return@runs
//        if (RingsManager.currentRoute.isEmpty()) return@runs modMessage("Nothing to undo")
//
//        val lastRing = RingsManager.currentRoute.first().rings.removeLast()
//        removedRings.add(mutableListOf(lastRing))
//        modMessage("Undone ${lastRing.format()}")
//        RingsManager.loadSaveRoute()
//    }
//
//    literal("redo").runs {
//        if (!check) return@runs
//        if (removedRings.isEmpty()) return@runs modMessage("Nothing to redo")
//
//        val lastRemoved = removedRings.removeLast()
//        RingsManager.currentRoute.first().rings.addAll(lastRemoved)
//        modMessage("Redone ${lastRemoved.joinToString(", ") { it.format() }}")
//        RingsManager.loadSaveRoute()
//    }
//
//    literal("clearroute").runs {
//        if (!check) return@runs
//        createClickableText(
//            text = "§8»§r Are you sure you want to clear §nCURRENT§r route?",
//            hoverText = "Click to clear §nCURRENT§r route!",
//            action = "/p3 clearrouteconfirm"
//        )
//    }
//
//    literal("clearrouteconfirm").runs {
//        if (!check) return@runs
//        val originalRings = RingsManager.currentRoute.first().rings.toMutableList()
//        RingsManager.currentRoute.first().rings.clear()
//        removedRings.add(originalRings)
//        modMessage("Removed ${originalRings.size} rings. Do §7/p3 redo§r to revert")
//        RingsManager.loadSaveRoute()
//    }
//
//    literal("clear").runs {
//        createClickableText(
//            text = "§8»§r Are you sure you want to clear §nALL§r routes? It's irreversible!",
//            hoverText = "Click to clear §nALL§r routes!",
//            action = "/p3 clearconfirm"
//        )
//    }
//
//    literal("clearconfirm").runs {
//        modMessage("Cleared all routes")
//        RingsManager.clearAll()
//        RingsManager.loadRoute()
//    }
//
//    literal("save").runs {
//        if (!check) return@runs
//        modMessage("Saved ${AutoP3.selectedRoute}")
//        RingsManager.saveRoute()
//    }
//
//    literal("list").runs {
//        modMessage(RingsManager.allRoutes.joinToString("\n") { "${it.name} (${it.rings.size} rings)" })
//    }
//
//    literal("load").runs { routes: GreedyString? ->
//        val route = (routes?.toString() ?: AutoP3.selectedRoute)
//        RingsManager.loadRoute(route, true)
//    }
//
//    literal("add") {
//        runs {
//            modMessage("""
//            Usage: §7/p3 add §7<§btype§7> [§bargs..§7]
//                List of types: §7${ringTypes.joinToString()}
//                  §7- walk §8: §rmakes the player walk
//                  §7- look §8: §rturns player's head
//                  §7- stop §8: §rsets player's velocity to 0
//                  §7- fullstop §8: §rfully stops the player
//                  §7- bonzo §8: §ruses bonzo staff
//                  §7- boom §8: §ruses boom tnt
//                  §7- edge §8: §rjumps from block's edge
//                  §7- lavaclip §8: §rlava clips with a specified depth
//                  §7- jump §8: §rmakes the player jump
//                  §7- align §8: §raligns the player with the centre of the ring
//                  §7- command §8: §rexecutes a specified command
//                  §7- swap §8: §rswaps to a specified item
//                  §7- blink §8: §rteleports you
//                List of args: §bl_, w_, r_, h_, delay_, look, walk, term, stop, fullstop, exact, block, centre, ground, leap_, distance_
//                  §7- §blook §8: §rturns player's head
//                  §7- §bwalk §8: §rmakes the player walk
//                  §7- §bterm §8: §ractivates the node when terminal opens
//                  §7- §bleft §8: §ractivates the node when leftclick
//                  §7- §bstop §8: §rsets player's velocity to 0
//                  §7- §bfullstop §8: §rfully stops the player
//                  §7- §bblock §8: §rlooks at a block instead of yaw and pitch
//                  §7- §bcentre §8: §rexecutes the ring when the player is in the centre
//                  §7- §bground §8: §rexecutes the ring when the player is on the ground
//                  §7- §bleap_ §8: §rexecutes the ring when N people leapt to the player
//                  §7- §bdistance_ §8: §rsets the distance for lavaclip ring
//        """.trimIndent())
//        }
//
//        runs { type: String, arguments: GreedyString? -> // schizophrenia starts here
//            if (!check) return@runs
//            val player = mc.player ?: return@runs
//            val camera = mc.gameRenderer.mainCamera.position
//
//            val args = arguments?.toString()?.split(" ") ?: emptyList()
//
//            // 1.21 Camera Position Rounding
//            var x = Math.round(player.x * 2) / 2.0
//            var y = Math.round(player.y * 2) / 2.0
//            var z = Math.round(player.z * 2) / 2.0
//
//            // 1.21 Rotations
//            val yaw = ((player.yRot % 360) + 360) % 360
//            val pitch = mc.gameRenderer.mainCamera.xRot
//
//            var length = 1.0f
//            var width = 1.0f
//            var height = 1.0f
//            var radius: Float? = null
//            var delay: Int? = null
//            var distance: Double? = null
//
//            var stringHolder: String? = null
//
//            val ringArgs = mutableListOf<RingArgument>().apply {
//                args.forEach { arg ->
//                    Regex("^(\\w+?)(\\d*\\.?\\d*)\$").find(arg)?.destructured?.let { (flag, value) ->
//                        when(flag.lowercase()) {
//                            "l", "length"   -> length = value.toFloatOrNull() ?: return@runs invalidUsage("length", "l")
//                            "w", "width"    -> width = value.toFloatOrNull() ?: return@runs invalidUsage("width", "w")
//                            "h", "height"   -> height = value.toFloatOrNull() ?: return@runs invalidUsage("height", "h")
//                            "r", "radius"   -> radius = value.toFloatOrNull() ?: return@runs invalidUsage("radius", "r")
//                            "delay"         -> delay = value.toIntOrNull() ?: return@runs invalidUsage("delay")
//                            "distance"      -> distance = value.toDoubleOrNull() ?: return@runs invalidUsage("distance")
//
//                            "leap"          -> add(LeapArgument(value.toIntOrNull() ?: return@runs invalidUsage("leap") ))
//
//                            "block"         -> {
//                                val hit = player.pick(40.0, 1.0f, false)
//                                add(BlockArgument(hit.location))
//                            }
//
//                            "stop"          -> add(StopArgument())
//                            "fullstop"      -> add(StopArgument(true))
//                            "term"          -> add(TermArgument)
//                            "left"          -> add(LeftClickArgument)
////                            "centre", "center" -> add(CentreArgument)
//                            "ground", "onground" -> add(GroundArgument)
//                            "look", "rotate" -> add(LookArgument)
//                            "exact" -> {
//                                x = player.x
//                                y = player.y
//                                z = player.z
//                            }
//                            else -> {}
//                        }
//                    }
//
//                    stringHolder = when (type.lowercase()) {
//                        "command", "cmd" -> Regex(""""([^"]*)"""").find(arguments.toString())?.destructured?.component1()
//                        "swap" -> Regex(""""([^"]*)"""").find(arguments.toString())?.destructured?.component1()
//                        else -> null
//                    }
//                }
//            }.takeIf { it.isNotEmpty() }
//
//            val action = when(type.lowercase()) {
//                "align"     -> AlignRing()
//                "blink"     -> BlinkRing()
//                "edge"      -> EdgeRing
////                "hclip"     -> HClipRing
//                "jump"      -> JumpRing
//                "swap"      -> SwapRing(stringHolder ?: return@runs invalidUsageString("swap"))
//                "command", "cmd" -> CommandRing(stringHolder ?: return@runs invalidUsageString("command", "cmd"))
//                "look", "rotate" -> LookRing
//                "stop"      -> StopRing()
//                "fullstop"  -> StopRing(true)
//                "bonzo"     -> UseItemRing("bonzo's staff")
//                "walk"      -> WalkRing
//                "opposite" -> OppositeRing
//                "boom"      -> {
//                    val hit = mc.hitResult
//                    if (hit != null && hit.type == HitResult.Type.BLOCK) {
//                        val blockHit = hit as BlockHitResult
//                        BoomRing(blockHit.location)
//                    } else {
//                        return@runs modMessage("Look at a block")
//                    }
//                }
//                else -> return@runs modMessage("Unknown ring type")
//            }
//
//            val isFacingX = (yaw in 45.0..135.0) || (yaw in 225.0..315.0)
//
//            val (finalLength, finalWidth) = radius?.let { it to it } ?: run {
//                if (isFacingX) width to length
//                else length to width
//            }
//
//            if ((action is BlinkRing || action is BoomRing) && (finalLength > 1.0f || finalWidth > 1.0f)) return@runs modMessage("Ring is too big")
//
//            val ring = Ring(action, Vec3(x, y, z), yaw, pitch, ringArgs, finalLength, finalWidth, height, delay)
//            if (!RingsManager.addRing(ring)) return@runs modMessage("Route §7${AutoP3.selectedRoute}§r doesn't exist. Do §7/p3 create §7<§bname§7>")
//            modMessage("Added ${ring.format()}")
//            RingsManager.loadSaveRoute()
//        } // schizophrenia ends here
//    }
//}
//
//fun invalidUsage(name: String, vararg aliases: String) {
//    modMessage("Usage: §7/p3 add §7<§btype§7> ${name}<§bnum§7> [§bother args..§7]" + if (aliases.isNotEmpty()) " §rAliases: ${aliases.joinToString(", ") { it }}" else "")
//}
//
//fun invalidUsageString(name: String, vararg aliases: String) {
//    modMessage("Usage: §7/p3 add §7$name \"§bvalue§7\" [§bargs..§7]" + if (aliases.isNotEmpty()) " §rAliases: ${aliases.joinToString(", ") { it }}" else "")
//}
//
//val check get(): Boolean {
//    if (RingsManager.currentRoute.size > 1) {
//        modMessage("Can't edit when multiple routes loaded. Loaded routes: §7${RingsManager.currentRoute.joinToString(", ") { it.name }}")
//        return false
//    }
//    if (AutoP3.inBossOnly && !DungeonUtils.isFloor(7)) {
//        return false
//    }
//    return true
//}