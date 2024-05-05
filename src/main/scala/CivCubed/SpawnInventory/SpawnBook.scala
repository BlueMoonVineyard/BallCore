package CivCubed.SpawnInventory

import CivCubed.CustomItems.CustomItem
import CivCubed.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import CivCubed.CustomItems.CustomItemStack
import scala.util.chaining._
import CivCubed.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import CivCubed.CustomItems.ItemRegistry
import CivCubed.TextComponents._

object SpawnBook:
    val group = ItemGroup(
        NamespacedKey("civcubed", "spawnbook"),
        ItemStack(Material.BOOK),
    )
    val template = CustomItemStack.make(
        NamespacedKey("civcubed", "spawnbook"),
        Material.PAPER,
        txt"Welcome to CivCubed!",
        txt"A helpful guide to getting started with CivCubed.",
        txt"It seems to be magical...",
        txt"(the spawnbook will always update to the latest version)",
    )
    template.setItemMeta(template.getItemMeta.tap(_.setCustomModelData(6)))

    def register()(using ir: ItemRegistry): Unit =
        ir.register(SpawnBook())

class SpawnBook extends CustomItem, Listeners.ItemUsed:
    override def group: ItemGroup = SpawnBook.group
    override def template: CustomItemStack = SpawnBook.template

    override def onItemUsed(event: PlayerInteractEvent): Unit =
        event.getPlayer().openBook(Book.book)
