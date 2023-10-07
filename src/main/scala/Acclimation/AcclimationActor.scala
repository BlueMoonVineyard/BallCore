// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Acclimation

import BallCore.DataStructures.Actor
import BallCore.Beacons.CivBeaconManager
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import BallCore.DataStructures.ShutdownCallbacks
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import BallCore.Storage.SQLManager

enum AcclimationMessage:
	// ticks every 6 hours
	case tick

// 28 lerps from a to b if boosted
// 56 lerps from a to b if not boosted
// 120 lerps from a to b if antiboosted
object BoostFactors:
	val boosted: Double = 1.0/28.0
	val nonBoosted: Double = 1.0/56.0
	val antiBoosted: Double = 1.0/120.0

def lerp(a: Double, b: Double, f: Double): Double =
	a * (1.0 - f) + (b * f)

object AcclimationActor:
	def register()(using s: Storage, p: Plugin, hnm: CivBeaconManager, sm: ShutdownCallbacks, sql: SQLManager): Unit =
		AcclimationActor().startListener()
		p.getServer().getPluginManager().registerEvents(AcclimationNoter(), p)

class AcclimationNoter()(using s: Storage, sql: SQLManager) extends Listener:
	@EventHandler()
	def onPlayerQuit(event: PlayerQuitEvent): Unit =
		val player = event.getPlayer()
		val loc = player.getLocation()
		sql.useFireAndForget(s.setLastSeenLocation(player.getUniqueId(), loc))

class AcclimationActor(using s: Storage, p: Plugin, hnm: CivBeaconManager, sql: SQLManager) extends Actor[AcclimationMessage]:
	private def sixHoursMillis = 6 * 60 * 60 * 1000
	private def millisToNextSixthHour(): Long =
		val nextHour = LocalDateTime.now().plusHours(6).truncatedTo(ChronoUnit.HOURS)
		LocalDateTime.now().until(nextHour, ChronoUnit.MILLIS)

	protected def handleInit(): Unit =
		val _ = p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(AcclimationMessage.tick), millisToNextSixthHour(), sixHoursMillis, TimeUnit.MILLISECONDS)
	protected def handleShutdown(): Unit =
		()
	def handle(m: AcclimationMessage): Unit =
		m match
			case AcclimationMessage.tick =>
				Bukkit.getOfflinePlayers().foreach { x =>
					val uuid = x.getUniqueId()
					val location =
						if x.getPlayer() != null then
							val player = x.getPlayer()
							val loc = player.getLocation()
							sql.useFireAndForget(s.setLastSeenLocation(player.getUniqueId(), loc))
							x.getPlayer().getLocation()
						else
							sql.useBlocking(s.getLastSeenLocation(uuid))

					val (lat, long) = Information.latLong(location.getX().toFloat, location.getZ().toFloat)
					val temp = Information.temperature(location.getX().toInt, location.getY().toInt, location.getZ().toInt)
					val elevation = Information.elevation(location.getY().toInt)

					val adjustFactor =
						sql.useBlocking(hnm.getBeaconFor(uuid)) match
							case Some(beacon) if sql.useBlocking(hnm.beaconContaining(location)) == Some(beacon) =>
								BoostFactors.boosted
							case Some(beacon) =>
								BoostFactors.antiBoosted
							case None =>
								BoostFactors.nonBoosted

					sql.useFireAndForget (for {
						latitude <- s.getLatitude(uuid)
						_ <- s.setLatitude(uuid, lerp(latitude, lat, adjustFactor))
						longitude <- s.getLongitude(uuid)
						_ <- s.setLongitude(uuid, lerp(longitude, long, adjustFactor))
						temperature <- s.getTemperature(uuid)
						_ <- s.setTemperature(uuid, lerp(temperature, temp, adjustFactor))
						currentElevation <- s.getElevation(uuid)
						_ <- s.setElevation(uuid, lerp(currentElevation, elevation, adjustFactor))
					} yield ())
				}
