// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Sidebar

import BallCore.DataStructures.Actor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.{TextColor, TextDecoration}
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.entity.Player
import org.bukkit.event.player.{PlayerJoinEvent, PlayerQuitEvent}
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.plugin.Plugin

import scala.collection.concurrent.TrieMap

enum SidebarMsg:
    case playerJoin(who: Player)
    case playerLeave(who: Player)
    case update(line: SidebarLine, target: Player, text: Option[Component])
    case updateAll(line: SidebarLine, text: Option[Component])

enum SidebarLine:
    case filler1
    case date
    case time
    case rest
    case filler2

class SidebarActor(using lib: ScoreboardLibrary, p: Plugin)
    extends Actor[SidebarMsg],
      Listener:
    private val sidebars = TrieMap[Player, Sidebar]()

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def onJoin(ev: PlayerJoinEvent): Unit =
        send(SidebarMsg.playerJoin(ev.getPlayer))

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    def onQuit(ev: PlayerQuitEvent): Unit =
        send(SidebarMsg.playerLeave(ev.getPlayer))

    def set(line: SidebarLine, target: Player, text: Option[Component]): Unit =
        send(SidebarMsg.update(line, target, text))

    def setAll(line: SidebarLine, text: Option[Component]): Unit =
        send(SidebarMsg.updateAll(line, text))

    override def handle(m: SidebarMsg): Unit =
        m match
            case SidebarMsg.playerJoin(who) =>
                import BallCore.UI.ChatElements.*
                val sidebar = lib.createSidebar()
                val civ = txt"Civ".style(x => {
                    x.color(TextColor.fromCSSHexString("#da2117"))
                    ()
                })
                val cubed = txt"Cubed".style(x => {
                    x.color(TextColor.fromCSSHexString("#93180f"))
                    ()
                })
                sidebar.title(txt"$civ$cubed".style(x => {
                    x.decorate(TextDecoration.BOLD)
                    ()
                }))
                sidebar.addPlayer(who)
                SidebarLine.values.foreach(line =>
                    sidebar.line(line.ordinal, Component.empty())
                )
                sidebars(who) = sidebar
            case SidebarMsg.playerLeave(who) =>
                sidebars.remove(who).foreach(_.close())
            case SidebarMsg.update(line, target, text) =>
                sidebars
                    .get(target)
                    .foreach(
                        _.line(line.ordinal, text.getOrElse(Component.empty()))
                    )
            case SidebarMsg.updateAll(line, text) =>
                sidebars.foreach(
                    _._2.line(line.ordinal, text.getOrElse(Component.empty()))
                )

    override protected def handleInit(): Unit =
        p.getServer.getPluginManager.registerEvents(this, p)

    override protected def handleShutdown(): Unit = ()

    sidebars.foreach(_._2.close())
