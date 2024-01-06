// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import BallCore.CustomItems.*
import BallCore.Groups.{GroupManager, Permissions, nullUUID}
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Storage.SQLManager
import BallCore.UI.Elements.*
import org.bukkit.entity.Player
import org.bukkit.event.block.{BlockBreakEvent, BlockPlaceEvent}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.{Material, NamespacedKey}

import java.util.UUID
import BallCore.Sigils.BattleManager
import io.sentry.Sentry
import BallCore.NoodleEditor.EssenceManager
import BallCore.NoodleEditor.Essence
import BallCore.Advancements.PlaceCivHeart

object HeartBlock:
    val itemStack: CustomItemStack = CustomItemStack.make(
        NamespacedKey("ballcore", "civilization_heart"),
        Material.WHITE_CONCRETE,
        trans"items.civilization-heart",
        trans"items.civilization-heart.lore.0",
        trans"items.civilization-heart.lore.1",
        trans"items.civilization-heart.lore.2",
        trans"items.civilization-heart.lore.3",
        trans"items.civilization-heart.lore.4",
    )
// val tickHandler = RainbowTickHandler(Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.RED_CONCRETE, Material.PINK_CONCRETE)

class HeartBlock()(using
    hn: CivBeaconManager,
    editor: PolygonEditor,
    gm: GroupManager,
    bm: BlockManager,
    sql: SQLManager,
    battleManager: BattleManager,
    essence: EssenceManager,
    ir: ItemRegistry,
) extends CustomItem,
      Listeners.BlockPlaced,
      Listeners.BlockRemoved,
      Listeners.BlockClicked:

    def group: ItemGroup = Beacons.group

    def template: CustomItemStack = HeartBlock.itemStack

    private def playerHeartCoords(p: Player): Option[(Long, Long, Long)] =
        sql.useBlocking(sql.withS(hn.getBeaconLocationFor(p.getUniqueId)))

    private def onBlockClickedNonEssence(event: PlayerInteractEvent): Unit =
        sql
            .useBlocking(
                sql.withS(hn.heartAt(event.getClickedBlock.getLocation()))
            )
            .map(_._2)
            .flatMap(beacon =>
                sql.useBlocking(sql.withS(hn.getGroup(beacon)))
                    .map(group => (beacon, group))
            ) match
            case Some((beacon, group)) =>
                sql.useBlocking {
                    sql.withS(
                        sql.withTX(
                            gm.checkE(
                                event.getPlayer.getUniqueId,
                                group,
                                nullUUID,
                                Permissions.ManageClaims,
                            ).value
                        )
                    )
                } match
                    case Left(err) =>
                        event.getPlayer
                            .sendServerMessage(
                                trans"notifications.cannot-edit-claims-error".args(err.explain().toComponent)
                            )
                    case Right(_) =>
                        if sql.useBlocking(
                                sql.withS(
                                    battleManager.isInvolvedInBattle(beacon)
                                )
                            )
                        then
                            event.getPlayer
                                .sendServerMessage(
                                    trans"notifications.cannot-edit-claims-war"
                                )
                        else
                            editor.create(
                                event.getPlayer,
                                event.getClickedBlock.getWorld,
                                beacon,
                            )
            case None =>
                event.getPlayer.sendServerMessage(
                    trans"notifications.cannot-edit-claims-no-group"
                )

    private def onBlockClickedEssence(event: PlayerInteractEvent): Unit =
        val owner = sql
            .useBlocking(
                sql.withS(hn.heartAt(event.getClickedBlock.getLocation()))
            )
            .map(_._1)
            .get
        val location = event.getClickedBlock.getLocation
        val amount =
            sql.useBlocking(sql.withS(essence.addEssence(owner, location)))
        event.getItem.setAmount(event.getItem.getAmount - 1)
        event.getPlayer.sendServerMessage(
            trans"notifications.heart-essence".args(amount.toComponent)
        )

    def onBlockClicked(event: PlayerInteractEvent): Unit =
        ir.lookup(event.getItem()) match
            case Some(_: Essence) =>
                onBlockClickedEssence(event)
            case _ =>
                onBlockClickedNonEssence(event)

    def onBlockPlace(event: BlockPlaceEvent): Unit =
        playerHeartCoords(event.getPlayer) match
            case None =>
            case Some((x, y, z)) =>
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-already-placed".args(
                            x.toComponent, y.toComponent, z.toComponent
                        )
                    )
                event.setCancelled(true)
                return

        sql.useBlocking(
            sql.withS(
                bm.store(
                    event.getBlock,
                    "owner",
                    event.getPlayer.getUniqueId,
                )
            )
        )
        sql.useBlocking(
            sql.withS(
                hn.placeHeart(
                    event.getBlock.getLocation(),
                    event.getPlayer.getUniqueId,
                )
            )
        ) match
            case Right((_, x)) if x == 1 =>
                PlaceCivHeart.grant(event.getPlayer, "placed_heart")
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-placed.new-beacon"
                    )
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-placed.adaptation"
                    )
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-placed.bind-group"
                    )
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-placed.bind-group-command"
                    )
            case Right((_, x)) =>
                PlaceCivHeart.grant(event.getPlayer, "placed_heart")
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-placed.existing-beacon".args((x-1).toComponent)
                    )
            case Left(_) =>
                ()

    def onBlockRemoved(event: BlockBreakEvent): Unit =
        sql.useBlocking(
            sql.withS(sql.withTX(for {
                owner <- bm.retrieve[UUID](event.getBlock, "owner")
                res <- hn.removeHeart(event.getBlock.getLocation, owner.get)
            } yield res))
                .redeem(
                    { case e =>
                        Left(e)
                    },
                    { case it =>
                        Right(it)
                    },
                )
        ) match
            case Left(err) =>
                event.getPlayer
                    .sendServerMessage(
                        trans"notifications.heart-removed.error"
                    )
                Sentry.captureException(err)
                event.setCancelled(true)
            case Right(it) =>
                it match
                    case Some(_) =>
                        event.getPlayer
                            .sendServerMessage(
                                trans"notifications.heart-removed.beacon-disconnected"
                            )
                    case None =>
                        event.getPlayer.sendServerMessage(
                            trans"notifications.heart-removed.beacon-deleted"
                        )
