// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Reinforcements

import scala.util.chaining._
import scala.math._
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Location
import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI
import me.filoghost.holographicdisplays.api.hologram.Hologram
import org.bukkit.entity.Player
import scala.collection.mutable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.bukkit.block.Block

class HologramManager(using p: JavaPlugin, ec: ExecutionContext):
    private val api = HolographicDisplaysAPI.get(p)
    private val holos = mutable.Map[(Int,Int,Int,UUID), (Hologram, () => Unit)]()

    private def getHologram(at: Location): Hologram =
        val key = (at.getBlockX(), at.getBlockY(), at.getBlockZ(), at.getWorld().getUID())
        holos.get(key) match
            case None =>
                val isCancelled = AtomicBoolean(false)
                val neu = api.createHologram(at)
                holos(key) = (neu, () => isCancelled.set(true))
                Future {
                    if !isCancelled.get() then
                        holos.remove(key)
                        neu.delete()
                }
                neu
            case Some((holo, canc)) =>
                canc()
                val isCancelled = AtomicBoolean(false)
                holos(key) = (holo, () => isCancelled.set(true))
                Future {
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

    def display(at: Block, to: Player, text: List[String]): Unit =
        val loc = displayLocation(at.getLocation(), to)
        val holo = getHologram(at.getLocation())
        holo.setPosition(loc)
        holo.getLines().clear()
        text.map(holo.getLines().appendText)
