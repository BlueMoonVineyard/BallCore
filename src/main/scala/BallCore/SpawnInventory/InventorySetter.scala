package BallCore.SpawnInventory

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.Beacons.HeartBlock

object InventorySetter:
    def giveSpawnInventory(to: Player): Unit =
        val inventory = to.getInventory()
        inventory.setItem(0, SpawnBook.template)
        inventory.setItem(1, ItemStack(Material.TORCH, 32))
        inventory.setItem(2, ItemStack(Material.BREAD, 64))
        inventory.setItem(3, ItemStack(Material.WHITE_BED, 1))
        inventory.setItem(4, ItemStack(Material.MINECART, 1))
        inventory.setItem(5, ItemStack(Material.OAK_BOAT, 1))
        inventory.setItem(8, HeartBlock.itemStack)
