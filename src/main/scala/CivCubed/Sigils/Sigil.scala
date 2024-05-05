// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package CivCubed.Sigils

import org.bukkit.NamespacedKey
import CivCubed.CustomItems.ItemGroup
import CivCubed.CustomItems.CustomItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import scala.util.chaining._
import CivCubed.CustomItems.ItemRegistry
import CivCubed.CustomItems.CustomItem
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import CivCubed.Sigils.Sigil.persistenceKeyPlayer
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import scala.util.Try
import org.bukkit.plugin.Plugin
import CivCubed.DataStructures.ShutdownCallbacks
import CivCubed.Beacons.CivBeaconManager
import CivCubed.UI
import net.kyori.adventure.text.format.NamedTextColor
import java.{util => ju}
import scala.jdk.CollectionConverters._
import net.kyori.adventure.text.format.TextDecoration
// import java.util.concurrent.TimeUnit
import org.bukkit.inventory.ShapelessRecipe
import CivCubed.UI.Elements._
import CivCubed.Storage.SQLManager
import CivCubed.DataStructures.Clock

object Sigil:
    enum CustomModelData(val num: Int):
        case Empty extends CustomModelData(1)
        case Active extends CustomModelData(2)

    val group = ItemGroup(
        NamespacedKey("civcubed", "sigils"),
        ItemStack(Material.ENDER_PEARL),
    )
    val itemStack = CustomItemStack.make(
        NamespacedKey("civcubed", "sigil"),
        Material.PAPER,
        txt"Sigil",
    )
    itemStack.setItemMeta(
        itemStack
            .getItemMeta()
            .tap(_.setCustomModelData(CustomModelData.Empty.num))
    )

    val persistenceKeyPlayer =
        NamespacedKey("civcubed", "sigil_bound_player_uuid")

    def register()(using
        registry: ItemRegistry,
        p: Plugin,
        cb: ShutdownCallbacks,
        hmn: CivBeaconManager,
        ssm: SigilSlimeManager,
        spm: SlimePillarManager,
        cem: CustomEntityManager,
        sql: SQLManager,
        bm: BattleManager,
        clock: Clock,
    ): Unit =
        registry.register(Sigil())
        registry.register(SlimeEgg())
        given da: DamageActor = DamageActor()
        da.startListener()
        val behaviours = SlimeBehaviours()
        p.getServer()
            .getGlobalRegionScheduler()
            .runAtFixedRate(p, _ => behaviours.doSlimeLooks(), 1L, 13L)
        p.getServer().getPluginManager().registerEvents(behaviours, p)
        p.getServer().getPluginManager().registerEvents(DamageListener(), p)
        p.getServer().getPluginManager().registerEvents(SigilListener(), p)
        p.getServer()
            .getPluginManager()
            .registerEvents(new SlimePillarSlapDetector(), p)
        val flinger = SlimePillarFlinger()
        p.getServer()
            .getGlobalRegionScheduler()
            .runAtFixedRate(p, _ => flinger.doFlings(), 1L, 1L)
        p.getServer()
            .getGlobalRegionScheduler()
            .runAtFixedRate(p, _ => flinger.doLooks(), 1L, 40L)

        val sigilRecipe = ShapelessRecipe(
            NamespacedKey("civcubed", "unbound_sigil"),
            Sigil.itemStack,
        )
        sigilRecipe.addIngredient(Material.HONEYCOMB)
        sigilRecipe.addIngredient(Material.REDSTONE_BLOCK)
        sigilRecipe.addIngredient(Material.COAL_BLOCK)
        registry.addRecipe(sigilRecipe)

class Sigil extends CustomItem:
    import UI.ChatElements._

    def group = Sigil.group
    def template = Sigil.itemStack

    def isEmpty(is: ItemStack): Boolean =
        is.getItemMeta().getCustomModelData() == Sigil.CustomModelData.Empty.num
    def isActive(is: ItemStack): Boolean =
        is.getItemMeta()
            .getCustomModelData() == Sigil.CustomModelData.Active.num
    def itemMetaForBound(from: ItemStack, toPlayer: Player): ItemMeta =
        val im = from.getItemMeta()
        im.setCustomModelData(Sigil.CustomModelData.Active.num)
        im.displayName(
            txt"Bound Sigil"
                .color(NamedTextColor.YELLOW)
                .not(TextDecoration.ITALIC)
        )
        im.lore(
            List(
                txt"Bound to ${txt(toPlayer.getName()).color(NamedTextColor.AQUA)}"
                    .color(NamedTextColor.WHITE)
                    .not(TextDecoration.ITALIC),
                txt"Use on a Sigil Slime to banish the player from its beacon"
                    .color(NamedTextColor.WHITE)
                    .not(TextDecoration.ITALIC),
            ).asJava
        )
        val pdc = im.getPersistentDataContainer()
        pdc.set(
            Sigil.persistenceKeyPlayer,
            PersistentDataType.STRING,
            toPlayer.getUniqueId().toString(),
        )
        im
    def boundPlayerIn(from: ItemStack): Option[UUID] =
        val pdc = from.getItemMeta().getPersistentDataContainer()
        val uuid = pdc.getOrDefault(
            Sigil.persistenceKeyPlayer,
            PersistentDataType.STRING,
            "",
        )
        Try(UUID.fromString(uuid)).toOption
