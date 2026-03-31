package shizo.utils

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import shizo.Shizo.mc
import shizo.mixin.accessors.KeyMappingAccessor
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import shizo.events.TickEvent
import shizo.events.core.on
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import shizo.utils.handlers.schedule
import shizo.utils.network.hypixelapi.HypixelData
import java.util.Base64
import java.util.UUID
import kotlin.text.contains

fun playSoundSettings(soundSettings: Triple<String, Float, Float>) {
    val (soundName, volume, pitch) = soundSettings
    val soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(soundName)) ?: return
    playSoundAtPlayer(soundEvent, volume, pitch)
}



fun getPos() : Triple<Double, Double, Double> {
    val p = mc.player?: return Triple(0.0, 0.0, 0.0)
    return Triple(p.x, p.y, p.z)
}

fun playSoundAtPlayer(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) = mc.execute {
    mc.soundManager.playDelayed(SimpleSoundInstance.forUI(event, pitch, volume), 0)
}

fun setTitle(title: String) {
    mc.gui.setTimes(0, 20, 5)
    mc.gui.setTitle(Component.literal(title))
}

fun alert(title: String, playSound: Boolean = true) {
    setTitle(title)
    if (playSound) playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value())
}
// had to make this cause the other one I could not modify the time and it botehred me
fun alert(title: String, playSound: Boolean = true, stay: Int = 20) {
    mc.gui.setTimes(0, stay, 5)
    mc.gui.setTitle(Component.literal(title))

    if (playSound) playSoundAtPlayer(SoundEvents.NOTE_BLOCK_PLING.value())
}

val ItemStack.cleanName: String
    get() = this.hoverName.string

val heldItem: ItemStack?
    get() = mc.player?.weaponItem

fun isHoldingByName(vararg names: String): Boolean {
    val currentName = heldItem?.cleanName ?: return false
    return names.any { target ->
        currentName.contains(target, ignoreCase = true) // contains and also ignoring upper or lower case
    }
}

// might move this to item utils cause its cleaner tbh
const val ID = "id"
const val UUID_STRING = "uuid"

inline val ItemStack.customData: CompoundTag
    get() =
        getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

inline val ItemStack.itemId: String
    get() =
        customData.getString(ID).orElse("")

inline val CompoundTag.itemId: String
    get() =
        getString(ID).orElse("")

inline val ItemStack.itemUUID: String
    get() =
        customData.getString(UUID_STRING).orElse("")

inline val ItemStack.lore: List<Component>
    get() =
        getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines()

inline val ItemStack.loreString: List<String>
    get() =
        lore.map { it.string }

fun leftClick() {
    val attackKey = mc.options.keyAttack as KeyMappingAccessor
    attackKey.setClickCount(attackKey.clickCount + 1)
}

fun rightClick() {
    val useKey = mc.options.keyUse as KeyMappingAccessor
    useKey.setClickCount(useKey.clickCount + 1)
}

fun doJump() {

    if (!onGround()) return

    val jumpKey = mc.options.keyJump
    val jumpAccessor = jumpKey as KeyMappingAccessor
    val hardwareKey = jumpAccessor.boundKey
    KeyMapping.set(hardwareKey, true)
    schedule(2) {
        KeyMapping.set(hardwareKey, false)
    }
}


fun setSneaking(sneak : Boolean) {
        mc.options.keyShift.setDown(sneak)
}

fun sendUseItemClicks(
    rotations: MutableList<Float>
) {
    require(rotations.size % 2 == 0) { "Rotations must have equal length" }
    val connection = mc.connection ?: return

    var i = 0
    var sequence = 0
    while (i < rotations.size) {
        //modMessage("Sending Click yRot: ${rotations[i]}, xRot: ${rotations[i + 1]}, sequence: $sequence")
        val yRot = rotations[i]
        val xRot = rotations[i + 1]
        connection.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yRot, xRot))
        i += 2
        sequence++
    }
}

fun sendUseItemClicksSeq(
    rotations: MutableList<Float>
) {
    require(rotations.size % 2 == 0)
    val player = mc.player ?: return
    val gameMode = mc.gameMode ?: return

    var i = 0
    while (i < rotations.size) {
        player.yRot = rotations[i]
        player.xRot = rotations[i + 1]
        gameMode.useItem(player, InteractionHand.MAIN_HAND) // correct sequence
        i += 2
    }
}

fun ItemStack.isEtherwarpItem(): CompoundTag? =
    customData.takeIf { it.getInt("ethermerge").orElse(0) == 1 || it.itemId == "ETHERWARP_CONDUIT" }

fun getItemSlotFromName(itemName : String): Int {
    val player = mc.player ?: return -1
    val inv = player.inventory

    for (slot in 0 until 8){
        val stack = inv.getItem(slot)
        if (stack.isEmpty) continue

        val name = stack.hoverName.string
        if (name.contains(itemName, ignoreCase = true)) {
            return slot
        }
    }
    return -1
}

fun isPlayerInBox(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): Boolean {
    val p = mc.player ?: return false
    val xRange = if (x1 < x2) x1..x2 else x2..x1
    val yRange = if (y1 < y2) y1..y2 else y2..y1
    val zRange = if (z1 < z2) z1..z2 else z2..z1

    return p.x.toInt() in xRange && p.y.toInt() in yRange && p.z.toInt() in zRange
}

var recentlySwapped = false

fun swapToItem(itemName : String) {
    val itemSlot = getItemSlotFromName(itemName)
    val player = mc.player ?: return
    //why is this so aids
    val holdingName = player.mainHandItem.hoverName.string
    if (itemSlot == -1 || holdingName.contains(itemName, ignoreCase = true)) return
    if (recentlySwapped){
        modMessage("dont swap so quick joy (prevented 0tick swap)")
        return
    }
    recentlySwapped = true
    player.inventory.selectedSlot = itemSlot
}

fun swapFromNameAR(name: String, action: () -> Unit = {}): SwapState {
    val player = mc.player ?: return SwapState.UNKNOWN
    val itemSlot = getItemSlotFromName(name)
    val holdingName = player.mainHandItem.hoverName.string

    if (holdingName.contains(name, ignoreCase = true)) {
        action()
        return SwapState.ALREADY_HELD
    } else {
        if(itemSlot == -1) {
            modMessage("can't find $name")
            return SwapState.UNKNOWN
        }
        if (recentlySwapped) {
            modMessage("yo somethings wrong $name")
            return SwapState.TOO_FAST
        }

        recentlySwapped = true
        player.inventory.selectedSlot = itemSlot

        action()

        return SwapState.SWAPPED
    }
}

fun swapToSlot(slot: Int): SwapState {
    val player = mc.player ?: return SwapState.UNKNOWN
    val itemSlot = player.inventory.selectedSlot

    if (itemSlot == slot) {
        return SwapState.ALREADY_HELD
    } else {
        if(itemSlot == -1) {
            modMessage("can swap to undefined slot")
            return SwapState.UNKNOWN
        }
        if (recentlySwapped) {
            modMessage("yo somethings wrong")
            return SwapState.TOO_FAST
        }
        recentlySwapped = true
        player.inventory.selectedSlot = slot
        return SwapState.SWAPPED
    }
}

fun getItemSlot(query: String, ignoreCase: Boolean = true): Int {
    val player = mc.player ?: return -1
    for (i in 0 until 9) {
        val stack = player.inventory.getItem(i)
        if (stack.isEmpty) continue
        if (stack.itemId.equals(query, ignoreCase)) return i
        if (stack.hoverName.string.contains(query, ignoreCase)) return i
        if (stack.item.descriptionId.contains(query, ignoreCase)) return i
    }

    return -1
}

// this checks if u are holding it too
val Inventory.selectedSlotIndex: Int
    get() = try {
        val f = this.javaClass.getDeclaredField("selected")
        f.isAccessible = true
        f.getInt(this)
    } catch (e: Exception) {
        0
    }

fun swapToItemJ(query: String) {
    val player = mc.player ?: return
    val currentStack = player.mainHandItem

    if (!currentStack.isEmpty && (
                currentStack.itemId.equals(query, true) ||
                        currentStack.hoverName.string.contains(query, true) ||
                        currentStack.item.descriptionId.contains(query, true)
                )) {
        return
    }

    val slot = getItemSlot(query)

    if (slot != -1 && slot != player.inventory.selectedSlotIndex) {
        if (recentlySwapped){
            modMessage("dont swap so quick joy (prevented 0tick swap)")
            return
        }
        recentlySwapped = true
        player.inventory.setSelectedSlot(slot)
    }
}

enum class SwapState{
    SWAPPED, ALREADY_HELD, TOO_FAST, UNKNOWN
}

object ItemSwapTacker {
    init {
        on<TickEvent.End>{
            recentlySwapped = false
        }
    }
}


val ItemStack.texture: String?
    get() =
        get(DataComponents.PROFILE)?.partialProfile()?.properties?.get("textures")?.firstOrNull()?.value

inline val HypixelData.ItemData.magicalPower: Int
    get() =
        getSkyblockRarity(lore)?.mp?.let { if (id == "HEGEMONY_ARTIFACT") it * 2 else it } ?: 0

fun getSkyblockRarity(lore: List<String>): ItemRarity? {
    for (i in lore.indices.reversed()) {
        val rarity = rarityRegex.find(lore[i])?.groups?.get(1)?.value ?: continue
        return ItemRarity.entries.find { it.loreName == rarity }
    }
    return null
}
private val rarityRegex = Regex("(${ItemRarity.entries.joinToString("|") { it.loreName }}) ?([A-Z ]+)?")


enum class ItemRarity(
    val loreName: String,
    val colorCode: String,
    val color: Color,
    val mp: Int,
) {
    COMMON("COMMON", "§f", Colors.WHITE, 3),
    UNCOMMON("UNCOMMON", "§2", Colors.MINECRAFT_GREEN, 5),
    RARE("RARE", "§9", Colors.MINECRAFT_BLUE, 8),
    EPIC("EPIC", "§5", Colors.MINECRAFT_DARK_PURPLE, 12),
    LEGENDARY("LEGENDARY", "§6", Colors.MINECRAFT_GOLD, 16),
    MYTHIC("MYTHIC", "§d", Colors.MINECRAFT_LIGHT_PURPLE, 22),
    DIVINE("DIVINE", "§b", Colors.MINECRAFT_AQUA, 0),
    SPECIAL("SPECIAL", "§c", Colors.MINECRAFT_RED, 3),
    VERY_SPECIAL("VERY SPECIAL", "§c", Colors.MINECRAFT_RED, 5);
}


fun createSkullStack(textureHash: String): ItemStack {
    val stack = ItemStack(Items.PLAYER_HEAD)

    val property = Property(
        "textures",
        Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$textureHash\"}}}".toByteArray())
    )
    val multimap = ImmutableMultimap.builder<String, Property>().put("textures", property).build()
    val gameProfile = GameProfile(UUID.randomUUID(), "_", PropertyMap(multimap))

    stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gameProfile))
    return stack
}


fun ItemStack.hasGlint(): Boolean =
    componentsPatch.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)?.isPresent == true

fun rotate(yaw: Float, pitch: Float) {
    mc.player?.yRot = yaw
    mc.player?.xRot = pitch
}

val keys = listOf(
    mc.options.keyUp,
    mc.options.keyLeft,
    mc.options.keyDown,
    mc.options.keyRight,
    mc.options.keyJump,
    mc.options.keyShift,
)

fun handleKeys () {
    val window = mc.window
    for (key in keys) {
        val actualKey = (key as KeyMappingAccessor).boundKey
        KeyMapping.set(actualKey, InputConstants.isKeyDown(window,actualKey.value))
    }
}

fun stopMovement () {
    for (key in keys) {
        val actualKey = (key as KeyMappingAccessor).boundKey
        KeyMapping.set(actualKey, false)
    }
}

fun onGround() : Boolean {
    return mc.player?.onGround() ?: false
}

fun pressKey(keyCode: Int, delay: Int = 1) {
    val key = InputConstants.Type.KEYSYM.getOrCreate(keyCode)

    KeyMapping.set(key, true)

    schedule(delay) {
        KeyMapping.set(key, false)
    }
}