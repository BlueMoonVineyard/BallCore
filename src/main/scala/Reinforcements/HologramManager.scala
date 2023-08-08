// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import scala.util.chaining._
import scala.math._
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Location
import org.bukkit.entity.Player
import scala.collection.concurrent
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import org.bukkit.block.Block
import BallCore.DataStructures.Delay
import BallCore.Folia.LocationExecutionContext
import org.bukkit.entity.TextDisplay
import org.bukkit.entity.Display.Billboard
import BallCore.Folia.FireAndForget
import net.kyori.adventure.text.Component

class Hologram(val createAt: Location)(using JavaPlugin):
    implicit var ctx: ExecutionContext = LocationExecutionContext(createAt)

    // constants copied from holographicdisplays
    val lineHeight = 0.23
    val spaceBetweenLines = 0.02
    var lines = List[TextDisplay]()
    var position = createAt

    def relocate(to: Location): Unit =
        position = to
        ctx = LocationExecutionContext(position)
        FireAndForget {
            var currentY = position.getY()
            lines.zipWithIndex.foreach { (elem, idx) =>
                currentY -= lineHeight
                if idx > 0 then
                    currentY -= spaceBetweenLines
                elem.teleport(position.clone().tap(_.setY(currentY)))
            }
        }
    def clearLines(): Unit =
        FireAndForget {
            lines.foreach { line => line.remove() }
            lines = List()
        }
    def appendLine(text: Component): Unit =
        FireAndForget {
            position.getWorld().spawn(position, classOf[TextDisplay], { ent =>
                ent.text(text)
                ent.setBillboard(Billboard.CENTER)
                lines = lines ::: List(ent)
            })
            relocate(position)
        }
    def delete(): Unit =
        clearLines()

class HologramManager(using p: JavaPlugin):
    private val holos = concurrent.TrieMap[(Int,Int,Int,UUID), (Hologram, () => Unit)]()

    private def getHologram(at: Location): Hologram =
        given ctx: ExecutionContext = LocationExecutionContext(at)
        val key = (at.getBlockX(), at.getBlockY(), at.getBlockZ(), at.getWorld().getUID())
        holos.get(key) match
            case None =>
                val isCancelled = AtomicBoolean(false)
                val neu = Hologram(at)
                holos(key) = (neu, () => isCancelled.set(true))
                Delay.by(5.seconds).andThen { _ =>
                    if !isCancelled.get() then
                        holos.remove(key)
                        neu.delete()
                }
                neu
            case Some((holo, canc)) =>
                canc()
                val isCancelled = AtomicBoolean(false)
                holos(key) = (holo, () => isCancelled.set(true))
                Delay.by(5.seconds).andThen { _ =>
                    if !isCancelled.get() then
                        holos.remove(key)
                        holo.delete()
                }
                holo

    /// a good place to display the hologram to players
    private def displayLocation(at: Location, to: Player) =
        val base = at.clone().tap(_.add(0.5, 1.0, 0.5))
        val vec = to.getEyeLocation().toVector()
            .tap(_.subtract(base.toVector()))
            .tap(_.normalize())
            .tap(_.multiply(0.55 * sqrt(2)))
        base.tap(_.add(vec))

    def display(at: Block, to: Player, text: List[Component]): Unit =
        val loc = displayLocation(at.getLocation(), to)
        val holo = getHologram(at.getLocation())
        holo.relocate(loc)
        holo.clearLines()
        text.foreach(holo.appendLine)

    def clear(at: Block): Unit =
        val loc = at.getLocation()
        val key = (loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getUID())
        holos.get(key).foreach { (holo, cb) =>
            cb()
            holos.remove(key)
            holo.delete()
        }
