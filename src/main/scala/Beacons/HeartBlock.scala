// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Beacons

import BallCore.CustomItems.{BlockManager, CustomItem, CustomItemStack, Listeners}
import BallCore.Groups.{GroupManager, Permissions, nullUUID}
import BallCore.PolygonEditor.PolygonEditor
import BallCore.Storage.SQLManager
import BallCore.UI.Elements.*
import org.bukkit.entity.Player
import org.bukkit.event.block.{BlockBreakEvent, BlockPlaceEvent}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.{Material, NamespacedKey}

import java.util.UUID

object HeartBlock:
  val itemStack = CustomItemStack.make(
    NamespacedKey("ballcore", "civilization_heart"),
    Material.WHITE_CONCRETE,
    txt"Civilization Heart",
    txt"It beats with the power of a budding civilization..."
  )
// val tickHandler = RainbowTickHandler(Material.WHITE_CONCRETE, Material.PINK_CONCRETE, Material.RED_CONCRETE, Material.PINK_CONCRETE)

class HeartBlock()(using
                   hn: CivBeaconManager,
                   editor: PolygonEditor,
                   gm: GroupManager,
                   bm: BlockManager,
                   sql: SQLManager
) extends CustomItem,
  Listeners.BlockPlaced,
  Listeners.BlockRemoved,
  Listeners.BlockClicked:

  def group = Beacons.group

  def template = HeartBlock.itemStack

  def playerHeartCoords(p: Player): Option[(Long, Long, Long)] =
    sql.useBlocking(hn.getBeaconLocationFor(p.getUniqueId()))

  def onBlockClicked(event: PlayerInteractEvent): Unit =
    sql
      .useBlocking(hn.heartAt(event.getClickedBlock().getLocation()))
      .map(_._2)
      .flatMap(beacon =>
        sql.useBlocking(hn.getGroup(beacon)).map(group => (beacon, group))
      ) match
      case Some((beacon, group)) =>
        sql.useBlocking {
          gm.checkE(
            event.getPlayer().getUniqueId(),
            group,
            nullUUID,
            Permissions.ManageClaims
          ).value
        } match
          case Left(err) =>
            event
              .getPlayer()
              .sendServerMessage(
                txt"You cannot edit claims because ${err.explain()}"
              )
          case Right(_) =>
            editor.create(
              event.getPlayer(),
              event.getClickedBlock().getWorld(),
              beacon
            )
      case None =>
        event.getPlayer.sendServerMessage(
          txt"You need to bind this beacon to a group in order to edit its claims."
        )

  def onBlockPlace(event: BlockPlaceEvent): Unit =
    playerHeartCoords(event.getPlayer()) match
      case None =>
      case Some((x, y, z)) =>
        event
          .getPlayer()
          .sendServerMessage(
            txt"Your heart is already placed at [${x}, ${y}, ${z}]..."
          )
        event.setCancelled(true)
        return

          sql.useBlocking(
            bm.store(event.getBlock(), "owner", event.getPlayer().getUniqueId())
          )
    sql.useBlocking(
      hn.placeHeart(
        event.getBlock().getLocation(),
        event.getPlayer().getUniqueId()
      )
    ) match
      case Right((_, x)) if x == 1 =>
        event
          .getPlayer()
          .sendServerMessage(
            txt"Your heart is the first block of a new beacon!"
          )
        event
          .getPlayer()
          .sendServerMessage(
            txt"The beacon will help you adapt to the land here faster, which will get you increased resource drops over time, among other things."
          )
        event
          .getPlayer()
          .sendServerMessage(
            txt"Bind a group to this beacon, and you'll unlock the ability to claim land and allow other players to join your beacon!"
          )
      case Right((_, x)) =>
        event
          .getPlayer()
          .sendServerMessage(
            txt"You've joined your heart to a beacon with ${x - 1} other players!"
          )
      case Left(_) =>
        ()

  def onBlockRemoved(event: BlockBreakEvent): Unit =
    val owner =
      sql.useBlocking(bm.retrieve[UUID](event.getBlock(), "owner")).get
    sql.useBlocking(hn.removeHeart(event.getBlock().getLocation(), owner)) match
      case Some(_) =>
        event
          .getPlayer()
          .sendServerMessage(txt"You've disconnected from the beacon...")
      case None =>
        event.getPlayer().sendServerMessage(txt"You've deleted the beacon...")
