package BallCore.Sigils

import org.bukkit.NamespacedKey
import BallCore.CustomItems.ItemGroup
import BallCore.CustomItems.CustomItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import scala.util.chaining._
import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.CustomItem
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import BallCore.Sigils.Sigil.persistenceKeyPlayer
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import scala.util.Try
import org.bukkit.plugin.Plugin
import BallCore.DataStructures.ShutdownCallbacks
import org.bukkit.entity.Damageable
import BallCore.Hearts.HeartNetworkManager
import BallCore.UI
import net.kyori.adventure.text.format.NamedTextColor
import java.{util => ju}
import scala.collection.JavaConverters._
import net.kyori.adventure.text.format.TextDecoration
import java.util.concurrent.TimeUnit
import org.bukkit.inventory.ShapelessRecipe

object Sigil:
    enum CustomModelData(val num: Int):
        case Empty extends CustomModelData(1)
        case Active extends CustomModelData(2)

    val group = ItemGroup(NamespacedKey("ballcore", "sigils"), ItemStack(Material.ENDER_PEARL))
    val itemStack = CustomItemStack.make(NamespacedKey("ballcore", "sigil"), Material.PAPER, "&rSigil")
    itemStack.setItemMeta(itemStack.getItemMeta().tap(_.setCustomModelData(CustomModelData.Empty.num)))

    val persistenceKeyPlayer = NamespacedKey("ballcore", "sigil_bound_player_uuid")

    def register()(using registry: ItemRegistry, p: Plugin, cb: ShutdownCallbacks, hmn: HeartNetworkManager, bm: BanishmentManager, cem: CustomEntityManager): Unit =
        registry.register(Sigil())
        registry.register(SlimeEgg())
        given da: DamageActor = DamageActor()
        da.startListener()
        val behaviours = SlimeBehaviours()
        p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => behaviours.doSlimeLooks(), 0L, 600L, TimeUnit.MILLISECONDS)
        p.getServer().getPluginManager().registerEvents(DamageListener(), p)
        p.getServer().getPluginManager().registerEvents(SigilListener(), p)

        val sigilRecipe = ShapelessRecipe(NamespacedKey("ballcore", "unbound_sigil"), Sigil.itemStack)
        sigilRecipe.addIngredient(Material.HONEYCOMB)
        sigilRecipe.addIngredient(Material.REDSTONE_BLOCK)
        sigilRecipe.addIngredient(Material.COAL_BLOCK)
        p.getServer().addRecipe(sigilRecipe)

class Sigil extends CustomItem:
    import UI.ChatElements._

    def group = Sigil.group
    def template = Sigil.itemStack

    def isEmpty(is: ItemStack): Boolean =
        is.getItemMeta().getCustomModelData() == Sigil.CustomModelData.Empty.num
    def isActive(is: ItemStack): Boolean =
        is.getItemMeta().getCustomModelData() == Sigil.CustomModelData.Active.num
    def itemMetaForBound(from: ItemStack, toPlayer: Player): ItemMeta =
        val im = from.getItemMeta()
        im.setCustomModelData(Sigil.CustomModelData.Active.num)
        im.displayName(txt"Bound Sigil".color(NamedTextColor.YELLOW).not(TextDecoration.ITALIC))
        im.lore(List(
            txt"Bound to ${txt(toPlayer.getName()).color(NamedTextColor.AQUA)}".color(NamedTextColor.WHITE).not(TextDecoration.ITALIC),
            txt"Use on a Sigil Slime to banish the player from its beacon".color(NamedTextColor.WHITE).not(TextDecoration.ITALIC),
        ).asJava)
        val pdc = im.getPersistentDataContainer()
        pdc.set(Sigil.persistenceKeyPlayer, PersistentDataType.STRING, toPlayer.getUniqueId().toString())
        im
    def boundPlayerIn(from: ItemStack): Option[UUID] =
        val pdc = from.getItemMeta().getPersistentDataContainer()
        val uuid = pdc.getOrDefault(Sigil.persistenceKeyPlayer, PersistentDataType.STRING, "")
        Try(UUID.fromString(uuid)).toOption