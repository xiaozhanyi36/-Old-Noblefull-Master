// Destiny made by ChengFeng
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.injection.access.IItemStack
import net.ccbluex.liquidbounce.utils.ClientUtils
import net.ccbluex.liquidbounce.utils.InventoryUtils
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.item.ArmorPiece
import net.ccbluex.liquidbounce.utils.item.ItemUtils
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.enchantment.Enchantment
import net.minecraft.init.Blocks
import net.minecraft.item.*
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import java.util.stream.Collectors
import java.util.stream.IntStream

@ModuleInfo(name = "InvManager", category = ModuleCategory.PLAYER)
class InvManager : Module() {

    /**
     * OPTIONS
     */

    private val modeValue = ListValue("Mode", arrayOf("Open", "Fake", "Basic"), "Basic")
    private val potionValue = ListValue("Potion", arrayOf("All", "Positive"), "All")

    private val delayValue = IntegerValue("Delay", 50, 0, 300)
    private val fakeDelayValue = IntegerValue("FakeDelay", 0, 0, 1000).displayable { modeValue.get() == "Fake" }
    private val itemDelayValue = IntegerValue("ItemDelay", 0, 0, 5000)

    private val noMoveValue = BoolValue("NoMove", false)
    private val noCombatValue = BoolValue("NoCombat", false)
    private val hotbarValue = BoolValue("NoHotbar", true)

    private val nbtGoalValue =
        ListValue("NBTGoal", ItemUtils.EnumNBTPriorityType.values().map { it.toString() }.toTypedArray(), "NONE")
    private val nbtItemNotGarbage = BoolValue("NBTItemNotGarbage", true).displayable { !nbtGoalValue.equals("NONE") }
    private val nbtArmorPriority =
        FloatValue("NBTArmorPriority", 0f, 0f, 5f).displayable { !nbtGoalValue.equals("NONE") }
    private val nbtWeaponPriority =
        FloatValue("NBTWeaponPriority", 0f, 0f, 5f).displayable { !nbtGoalValue.equals("NONE") }

    private val items = arrayOf(
        "None",
        "Ignore",
        "Sword",
        "Bow",
        "Pickaxe",
        "Axe",
        "Food",
        "Block",
        "Water",
        "Gapple",
        "Pearl",
        "Potion"
    )
    private val sortSlot1Value = ListValue("SortSlot-1", items, "Sword")
    private val sortSlot2Value = ListValue("SortSlot-2", items, "Gapple")
    private val sortSlot3Value = ListValue("SortSlot-3", items, "Potion")
    private val sortSlot4Value = ListValue("SortSlot-4", items, "Pickaxe")
    private val sortSlot5Value = ListValue("SortSlot-5", items, "Axe")
    private val sortSlot6Value = ListValue("SortSlot-6", items, "None")
    private val sortSlot7Value = ListValue("SortSlot-7", items, "Block")
    private val sortSlot8Value = ListValue("SortSlot-8", items, "Pearl")
    private val sortSlot9Value = ListValue("SortSlot-9", items, "Food")

    private val openInventory: Boolean
        get() = mc.currentScreen !is GuiInventory && modeValue.get() == "Fake"

    private var invOpened = false
        set(value) {
            if (value != field) {
                if (value) {
                    InventoryUtils.openPacket()
                } else {
                    InventoryUtils.closePacket()
                }
            }
            field = value
        }

    private val goal: ItemUtils.EnumNBTPriorityType
        get() = ItemUtils.EnumNBTPriorityType.valueOf(nbtGoalValue.get())

    private var delay = 0L
    private val simDelayTimer = MSTimer()

    override fun onDisable() {
        invOpened = false
    }

    private fun checkOpen(): Boolean {
        if (!invOpened && openInventory) {
            invOpened = true
            simDelayTimer.reset()
            return true
        }
        return !simDelayTimer.hasTimePassed(fakeDelayValue.get().toLong())
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (noMoveValue.get() && MovementUtils.isMoving() ||
            mc.thePlayer.openContainer != null && mc.thePlayer.openContainer.windowId != 0 ||
            (LiquidBounce.combatManager.inCombat && noCombatValue.get())
        ) {
            invOpened = false
            return
        }

        if (!InventoryUtils.CLICK_TIMER.hasTimePassed(delay) || mc.currentScreen !is GuiInventory && modeValue.get() == "Open") {
            return
        }


        // Find best armor
        val bestArmor = findBestArmor()

        // Swap armor
        for (i in 0..3) {
            val armorPiece = bestArmor[i] ?: continue
            val armorSlot = 3 - i
            val oldArmor: ItemStack? = mc.thePlayer.inventory.armorItemInSlot(armorSlot)
            if (oldArmor == null || oldArmor.item !is ItemArmor || ItemUtils.compareArmor(
                    ArmorPiece(oldArmor, -1),
                    armorPiece,
                    nbtArmorPriority.get(),
                    goal
                ) < 0
            ) {
                if (oldArmor != null && move(8 - armorSlot, true)) {
                    return
                }
                if (mc.thePlayer.inventory.armorItemInSlot(armorSlot) == null && move(armorPiece.slot, false)) {
                    return
                }
            }
        }


        val garbageItems = items(9, if (hotbarValue.get()) 45 else 36)
            .filter { !isUseful(it.value, it.key) }
            .keys
            .toMutableList()

        val garbageItem = garbageItems.firstOrNull()
        if (garbageItem != null) {
            // Drop all useless items
            if (checkOpen()) {
                return
            }

            mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, garbageItem, 4, 4, mc.thePlayer)

            delay = delayValue.get().toLong()

            return
        }


        for (index in 0..8) {
            val bestItem = findBetterItem(index, mc.thePlayer.inventory.getStackInSlot(index)) ?: continue

            if (bestItem != index) {
                if (checkOpen()) {
                    return
                }

                mc.playerController.windowClick(
                    0,
                    if (bestItem < 9) bestItem + 36 else bestItem,
                    index,
                    2,
                    mc.thePlayer
                )

                delay = delayValue.get().toLong()
                return
            }
        }

        invOpened = false
    }

    /**
     * Checks if the item is useful
     *
     * @param slot Slot id of the item. If the item isn't in the inventory -1
     * @return Returns true when the item is useful
     */
    fun isUseful(itemStack: ItemStack, slot: Int): Boolean {
        return try {
            val item = itemStack.item

            if (item is ItemSword || item is ItemTool) {
                if (slot >= 36 && findBetterItem(
                        slot - 36,
                        mc.thePlayer.inventory.getStackInSlot(slot - 36)
                    ) == slot - 36
                ) {
                    return true
                }

                for (i in 0..8) {
                    if (type(i).equals("sword", true) && item is ItemSword ||
                        type(i).equals("pickaxe", true) && item is ItemPickaxe ||
                        type(i).equals("axe", true) && item is ItemAxe
                    ) {
                        if (findBetterItem(i, mc.thePlayer.inventory.getStackInSlot(i)) == null) {
                            return true
                        }
                    }
                }

                val damage = (itemStack.attributeModifiers["generic.attackDamage"].firstOrNull()?.amount
                    ?: 0.0) + ItemUtils.getWeaponEnchantFactor(itemStack, nbtWeaponPriority.get(), goal)

                items(0, 45).none { (_, stack) ->
                    stack != itemStack && stack.javaClass == itemStack.javaClass && damage <= (stack.attributeModifiers["generic.attackDamage"].firstOrNull()?.amount
                        ?: 0.0) + ItemUtils.getWeaponEnchantFactor(stack, nbtWeaponPriority.get(), goal)
                }
            } else if (item is ItemBow) {
                val currPower = ItemUtils.getEnchantment(itemStack, Enchantment.power)

                items().none { (_, stack) ->
                    itemStack != stack && stack.item is ItemBow &&
                            currPower <= ItemUtils.getEnchantment(stack, Enchantment.power)
                }
            } else if (item is ItemArmor) {
                val currArmor = ArmorPiece(itemStack, slot)

                items().none { (slot, stack) ->
                    if (stack != itemStack && stack.item is ItemArmor) {
                        val armor = ArmorPiece(stack, slot)

                        if (armor.armorType != currArmor.armorType) {
                            false
                        } else {
                            ItemUtils.compareArmor(currArmor, armor, nbtArmorPriority.get(), goal) <= 0
                        }
                    } else {
                        false
                    }
                }
            } else if (itemStack.unlocalizedName == "item.compass") {
                items(0, 45).none { (_, stack) -> itemStack != stack && stack.unlocalizedName == "item.compass" }
            } else {
                (nbtItemNotGarbage.get() && ItemUtils.hasNBTGoal(itemStack, goal)) ||
                        item is ItemFood || itemStack.unlocalizedName == "item.arrow" ||
                        (item is ItemBlock && !InventoryUtils.isBlockListBlock(item)) ||
                        item is ItemBed || (item is ItemPotion && (potionValue.get() != "Positive" || InventoryUtils.isPositivePotion(
                    item,
                    itemStack
                ))) ||
                        item is ItemEnderPearl || item is ItemBucket
            }
        } catch (ex: Exception) {
            ClientUtils.logError("(InventoryCleaner) Failed to check item: ${itemStack.unlocalizedName}.", ex)
            true
        }
    }

    private fun findBestArmor(): Array<ArmorPiece?> {
        val armorPieces = IntStream.range(0, 36)
            .filter { i: Int ->
                val itemStack = mc.thePlayer.inventory.getStackInSlot(i)
                (itemStack != null && itemStack.item is ItemArmor &&
                        (i < 9 || System.currentTimeMillis() - (itemStack as IItemStack).itemDelay >= itemDelayValue.get()))
            }
            .mapToObj { i: Int -> ArmorPiece(mc.thePlayer.inventory.getStackInSlot(i), i) }
            .collect(Collectors.groupingBy { obj: ArmorPiece -> obj.armorType })

        val bestArmor = arrayOfNulls<ArmorPiece>(4)
        for ((key, value) in armorPieces) {
            bestArmor[key!!] = value.also {
                it.sortWith { armorPiece, armorPiece2 ->
                    ItemUtils.compareArmor(
                        armorPiece,
                        armorPiece2,
                        nbtArmorPriority.get(),
                        goal
                    )
                }
            }.lastOrNull()
        }

        return bestArmor
    }

    private fun findBetterItem(targetSlot: Int, slotStack: ItemStack?): Int? {
        val type = type(targetSlot)

        when (type.lowercase()) {
            "sword", "pickaxe", "axe" -> {
                val currentType: Class<out Item> = when {
                    type.equals("Sword", ignoreCase = true) -> ItemSword::class.java
                    type.equals("Pickaxe", ignoreCase = true) -> ItemPickaxe::class.java
                    type.equals("Axe", ignoreCase = true) -> ItemAxe::class.java
                    else -> return null
                }

                var bestWeapon = if (slotStack?.item?.javaClass == currentType) {
                    targetSlot
                } else {
                    -1
                }

                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, itemStack ->
                    if (itemStack != null && itemStack.item.javaClass == currentType && !type(index).equals(
                            type,
                            ignoreCase = true
                        )
                    ) {
                        if (bestWeapon == -1) {
                            bestWeapon = index
                        } else {
                            val currDamage = (itemStack.attributeModifiers["generic.attackDamage"].firstOrNull()?.amount
                                ?: 0.0) + ItemUtils.getWeaponEnchantFactor(itemStack, nbtWeaponPriority.get(), goal)

                            val bestStack = mc.thePlayer.inventory.getStackInSlot(bestWeapon) ?: return@forEachIndexed
                            val bestDamage = (bestStack.attributeModifiers["generic.attackDamage"].firstOrNull()?.amount
                                ?: 0.0) + ItemUtils.getWeaponEnchantFactor(bestStack, nbtWeaponPriority.get(), goal)

                            if (bestDamage < currDamage) {
                                bestWeapon = index
                            }
                        }
                    }
                }

                return if (bestWeapon != -1 || bestWeapon == targetSlot) bestWeapon else null
            }

            "bow" -> {
                var bestBow = if (slotStack?.item is ItemBow) targetSlot else -1
                var bestPower = if (bestBow != -1) {
                    ItemUtils.getEnchantment(slotStack!!, Enchantment.power)
                } else {
                    0
                }

                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, itemStack ->
                    if (itemStack?.item is ItemBow && !type(index).equals(type, ignoreCase = true)) {
                        if (bestBow == -1) {
                            bestBow = index
                        } else {
                            val power = ItemUtils.getEnchantment(itemStack, Enchantment.power)

                            if (ItemUtils.getEnchantment(itemStack, Enchantment.power) > bestPower) {
                                bestBow = index
                                bestPower = power
                            }
                        }
                    }
                }

                return if (bestBow != -1) bestBow else null
            }

            "food" -> {
                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, stack ->
                    val item = stack?.item

                    if (item is ItemFood && item !is ItemAppleGold && !type(index).equals("Food", ignoreCase = true)) {
                        val replaceCurr = slotStack == null || slotStack.item !is ItemFood

                        return if (replaceCurr) index else null
                    }
                }
            }

            "block" -> {
                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, stack ->
                    val item = stack?.item

                    if (item is ItemBlock && !InventoryUtils.isBlockListBlock(item) &&
                        !type(index).equals("Block", ignoreCase = true)
                    ) {
                        val replaceCurr = slotStack == null || slotStack.item !is ItemBlock

                        return if (replaceCurr) index else null
                    }
                }
            }

            "water" -> {
                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, stack ->
                    val item = stack?.item

                    if (item is ItemBucket && item.isFull == Blocks.flowing_water && !type(index).equals(
                            "Water",
                            ignoreCase = true
                        )
                    ) {
                        val replaceCurr =
                            slotStack == null || slotStack.item !is ItemBucket || (slotStack.item as ItemBucket).isFull != Blocks.flowing_water

                        return if (replaceCurr) index else null
                    }
                }
            }

            "gapple" -> {
                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, stack ->
                    val item = stack?.item

                    if (item is ItemAppleGold && !type(index).equals("Gapple", ignoreCase = true)) {
                        val replaceCurr = slotStack == null || slotStack.item !is ItemAppleGold

                        return if (replaceCurr) index else null
                    }
                }
            }

            "pearl" -> {
                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, stack ->
                    val item = stack?.item

                    if (item is ItemEnderPearl && !type(index).equals("Pearl", ignoreCase = true)) {
                        val replaceCurr = slotStack == null || slotStack.item !is ItemEnderPearl

                        return if (replaceCurr) index else null
                    }
                }
            }

            "potion" -> {
                mc.thePlayer.inventory.mainInventory.forEachIndexed { index, stack ->
                    val item = stack?.item

                    if ((item is ItemPotion && ItemPotion.isSplash(stack.itemDamage)) &&
                        !type(index).equals("Potion", ignoreCase = true)
                    ) {
                        val replaceCurr =
                            slotStack == null || slotStack.item !is ItemPotion || !ItemPotion.isSplash(slotStack.itemDamage)

                        return if (replaceCurr) index else null
                    }
                }
            }
        }

        return null
    }

    /**
     * Get items in inventory
     */
    private fun items(start: Int = 0, end: Int = 45): Map<Int, ItemStack> {
        val items = mutableMapOf<Int, ItemStack>()

        for (i in end - 1 downTo start) {
            val itemStack = mc.thePlayer.inventoryContainer.getSlot(i).stack ?: continue
            itemStack.item ?: continue

            if (i in 36..44 && type(i).equals("Ignore", ignoreCase = true)) {
                continue
            }

            if (System.currentTimeMillis() - (itemStack as IItemStack).itemDelay >= itemDelayValue.get()) {
                items[i] = itemStack
            }
        }

        return items
    }

    /**
     * Shift+Left clicks the specified item
     *
     * @param item        Slot of the item to click
     * @param isArmorSlot
     * @return True if it is unable to move the item
     */
    private fun move(item: Int, isArmorSlot: Boolean): Boolean {
        if (item == -1) {
            return false
        } else if (!isArmorSlot && item < 9 && hotbarValue.get() && mc.currentScreen !is GuiInventory) {
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(item))
            mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.inventoryContainer.getSlot(item).stack))
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
            delay = delayValue.get().toLong()
            return true
        } else {
            if (checkOpen()) {
                return true // make sure to return
            }
            if (isArmorSlot) {
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, item, 0, 4, mc.thePlayer)
            }
            mc.playerController.windowClick(
                mc.thePlayer.inventoryContainer.windowId,
                if (isArmorSlot) item else if (item < 9) item + 36 else item,
                0,
                1,
                mc.thePlayer
            )
            delay = delayValue.get().toLong()
            return true
        }
    }

    /**
     * Get type of [targetSlot]
     */
    private fun type(targetSlot: Int) = when (targetSlot) {
        0 -> sortSlot1Value.get()
        1 -> sortSlot2Value.get()
        2 -> sortSlot3Value.get()
        3 -> sortSlot4Value.get()
        4 -> sortSlot5Value.get()
        5 -> sortSlot6Value.get()
        6 -> sortSlot7Value.get()
        7 -> sortSlot8Value.get()
        8 -> sortSlot9Value.get()
        else -> ""
    }
}