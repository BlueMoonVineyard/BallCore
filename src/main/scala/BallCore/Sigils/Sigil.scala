// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sigils

import BallCore.Beacons.CivBeaconManager
import BallCore.CustomItems.{
    CustomItem,
    CustomItemStack,
    ItemGroup,
    ItemRegistry,
}
import BallCore.DataStructures.ShutdownCallbacks
import BallCore.Sigils.Sigil.persistenceKeyPlayer
import BallCore.Storage.SQLManager
import BallCore.UI
import BallCore.UI.Elements.*
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.{ItemStack, ShapelessRecipe}
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}

import java.util as ju
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.chaining.*

object Sigil:
    private enum CustomModelData(val num: Int):
        case Empty extends CustomModelData(1)
        case Active extends CustomModelData(2)

    val group: ItemGroup = ItemGroup(
        NamespacedKey("ballcore", "sigils"),
        ItemStack(Material.ENDER_PEARL),
    )
    val itemStack: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "sigil"),
        Material.PAPER,
        txt"Sigil",
    )
    itemStack.setItemMeta(
        itemStack.getItemMeta.tap(
            _.setCustomModelData(CustomModelData.Empty.num)
        )
    )

    val persistenceKeyPlayer: NamespacedKey =
        NamespacedKey("ballcore", "sigil_bound_player_uuid")

    def register()(using
        registry: ItemRegistry,
        p: Plugin,
        cb: ShutdownCallbacks,
        hmn: CivBeaconManager,
        ssm: SigilSlimeManager,
        cem: CustomEntityManager,
        sql: SQLManager,
    ): Unit =
        registry.register(Sigil())
        registry.register(SlimeEgg())

        given da: DamageActor = DamageActor()

        da.startListener()
        val behaviours = SlimeBehaviours()
        p.getServer.getAsyncScheduler
            .runAtFixedRate(
                p,
                _ => behaviours.doSlimeLooks(),
                0L,
                600L,
                TimeUnit.MILLISECONDS,
            )
        p.getServer.getPluginManager.registerEvents(DamageListener(), p)
        p.getServer.getPluginManager.registerEvents(SigilListener(), p)

        val sigilRecipe = ShapelessRecipe(
            NamespacedKey("ballcore", "unbound_sigil"),
            Sigil.itemStack,
        )
        sigilRecipe.addIngredient(Material.HONEYCOMB)
        sigilRecipe.addIngredient(Material.REDSTONE_BLOCK)
        sigilRecipe.addIngredient(Material.COAL_BLOCK)
        registry.addRecipe(sigilRecipe)

class Sigil extends CustomItem:

    import UI.ChatElements.*

    def group: ItemGroup = Sigil.group

    def template: CustomItemStack = Sigil.itemStack

    def isEmpty(is: ItemStack): Boolean =
        is.getItemMeta.getCustomModelData == Sigil.CustomModelData.Empty.num

    def isActive(is: ItemStack): Boolean =
        is.getItemMeta.getCustomModelData == Sigil.CustomModelData.Active.num

    def itemMetaForBound(from: ItemStack, toPlayer: Player): ItemMeta =
        val im = from.getItemMeta
        im.setCustomModelData(Sigil.CustomModelData.Active.num)
        im.displayName(
            txt"Bound Sigil"
                .color(NamedTextColor.YELLOW)
                .not(TextDecoration.ITALIC)
        )
        im.lore(
            List(
                txt"Bound to ${txt(toPlayer.getName).color(NamedTextColor.AQUA)}"
                    .color(NamedTextColor.WHITE)
                    .not(TextDecoration.ITALIC),
                txt"Use on a Sigil Slime to banish the player from its beacon"
                    .color(NamedTextColor.WHITE)
                    .not(TextDecoration.ITALIC),
            ).asJava
        )
        val pdc = im.getPersistentDataContainer
        pdc.set(
            Sigil.persistenceKeyPlayer,
            PersistentDataType.STRING,
            toPlayer.getUniqueId.toString,
        )
        im

    def boundPlayerIn(from: ItemStack): Option[UUID] =
        val pdc = from.getItemMeta.getPersistentDataContainer
        val uuid = pdc.getOrDefault(
            Sigil.persistenceKeyPlayer,
            PersistentDataType.STRING,
            "",
        )
        Try(UUID.fromString(uuid)).toOption
