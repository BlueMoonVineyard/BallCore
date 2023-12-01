package BallCore.SpawnInventory

import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.ItemGroup
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import BallCore.CustomItems.CustomItemStack
import scala.util.chaining._
import BallCore.CustomItems.Listeners
import org.bukkit.event.player.PlayerInteractEvent
import BallCore.CustomItems.ItemRegistry

object SpawnBook:
	val group = ItemGroup(NamespacedKey("ballcore", "spawnbook"), ItemStack(Material.BOOK))
	val is = ItemStack(Material.PAPER)
	is.setItemMeta(is.getItemMeta.tap(_.setCustomModelData(6)))
	val template = CustomItemStack(NamespacedKey("ballcore", "spawnbook"), is)

	def register()(using ir: ItemRegistry): Unit =
		ir.register(SpawnBook())

class SpawnBook extends CustomItem, Listeners.ItemUsed:
	override def group: ItemGroup = SpawnBook.group
	override def template: CustomItemStack = SpawnBook.template

	override def onItemUsed(event: PlayerInteractEvent): Unit =
		event.getPlayer().openBook(Book.book)