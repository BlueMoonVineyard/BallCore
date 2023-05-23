package BallCore.Acclimation

import BallCore.DataStructures.Actor
import BallCore.Hearts.HeartNetworkManager
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
	def register()(using s: Storage, p: Plugin, hnm: HeartNetworkManager): Unit =
		AcclimationActor().startListener()

class AcclimationActor(using s: Storage, p: Plugin, hnm: HeartNetworkManager) extends Actor[AcclimationMessage]:
	private def sixHoursMillis = 6 * 60 * 60 * 1000
	private def millisToNextSixthHour(): Long =
		val nextHour = LocalDateTime.now().plusHours(6).truncatedTo(ChronoUnit.HOURS)
		LocalDateTime.now().until(nextHour, ChronoUnit.MILLIS)

	protected def handleInit(): Unit =
		p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(AcclimationMessage.tick), millisToNextSixthHour(), sixHoursMillis, TimeUnit.MILLISECONDS)
	protected def handleShutdown(): Unit =
		()
	def handle(m: AcclimationMessage): Unit =
		m match
			case AcclimationMessage.tick =>
				Bukkit.getOfflinePlayers().foreach { x =>
					val uuid = x.getUniqueId()
					val location =
						if x.getPlayer() != null then
							x.getPlayer().getLocation()
						else
							???

					val (lat, long) = Information.latLong(location.getX().toFloat, location.getZ().toFloat)
					val temp = Information.temperature(location.getX().toInt, location.getY().toInt, location.getZ().toInt)
					val elevation = Information.elevation(location.getY().toInt)

					val adjustFactor =
						hnm.getHeartNetworkFor(uuid) match
							case Some(network) if hnm.heartNetworksContaining(location).contains(network) =>
								BoostFactors.boosted
							case Some(network) =>
								BoostFactors.antiBoosted
							case None =>
								BoostFactors.nonBoosted

					s.setLatitude(uuid, lerp(s.getLatitude(uuid), lat, adjustFactor))
					s.setLongitude(uuid, lerp(s.getLongitude(uuid), long, adjustFactor))
					s.setTemperature(uuid, lerp(s.getTemperature(uuid), temp, adjustFactor))
					s.setElevation(uuid, lerp(s.getElevation(uuid), elevation, adjustFactor))
				}
