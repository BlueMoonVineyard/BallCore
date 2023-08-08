package BallCore.Datekeeping

import java.time.Instant
import BallCore.DataStructures.Clock
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.time.temporal.Temporal
import BallCore.Sidebar.SidebarActor
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import BallCore.Sidebar.SidebarLine
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.function.Consumer
import org.bukkit.Bukkit

case class GameDate(
    val year: Long, // 12*31*24*60 minutes
    val month: Long, // 31*24*60 minutes
    val day: Long, // 24*60 minutes
    val hour: Long, // 60 minutes
    val minute: Long,
)

enum Season:
    case spring
    case summer
    case autumn
    case winter

enum DateUnit(val width: Duration) extends TemporalUnit:
    case minute extends DateUnit(Datekeeping.Periods.minute)
    case hour extends DateUnit(Datekeeping.Periods.hour)
    case day extends DateUnit(Datekeeping.Periods.day)
    case month extends DateUnit(Datekeeping.Periods.month)
    case year extends DateUnit(Datekeeping.Periods.year)

    override def isDateBased(): Boolean = false
    override def isTimeBased(): Boolean = false
    override def isDurationEstimated(): Boolean = false
    override def getDuration(): Duration = width
    override def between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long =
        temporal1Inclusive.until(temporal2Exclusive, this)
    override def addTo[R <: Temporal](temporal: R, amount: Long): R =
        temporal.plus(width.multipliedBy(amount)).asInstanceOf[R]

enum Month:
    case Nieuwice 
    case Bleibschnee
    case Coldwane
    case Lluvita
    case Floraison
    case Caldera
    case Zha
    case Dashu
    case Nurui
    case Sarada
    case Aban
    case Sisira

object Datekeeping:
    // pizza tower release date on steam
    val epoch = truncateLarge(Instant.ofEpochSecond(1674756019), DateUnit.year)

    // start year (pizza tower album track count * 10)
    val year0 = 730

    object Periods:
        val minute = Duration.ofMillis(2500)
        val hour = minute multipliedBy 60
        val day = hour multipliedBy 24
        val month = day multipliedBy 31
        val year = month multipliedBy 12

    def truncateLarge(instant: Instant, truncatedTo: TemporalUnit): Instant =
        val dur = truncatedTo.getDuration().toNanos()
        val nanos = instant.getNano()
        val seconds = instant.getEpochSecond()
        val nod = (seconds * 1000000000) + nanos
        val result = Math.floorDiv(nod, dur) * dur
        instant.plusNanos(result - nod)

    def time()(using clock: Clock): GameDate =
        // the only thing in IRL units
        val diff = Duration.between(epoch, clock.now()).toMillis()

        // ingame minutes that have elapsed since the epoch
        val minutes = diff / 2500

        // date elements
        val dateYear = year0 + minutes / (12*31*24*60)
        val dateMonth = (minutes % (12*31*24*60)) / (31*24*60)
        val dateDay = ((minutes % (12*31*24*60)) % (31*24*60)) / (24*60)
        val dateHour = (((minutes % (12*31*24*60)) % (31*24*60)) % (24*60)) / 60
        val dateMinutes = (((minutes % (12*31*24*60)) % (31*24*60)) % (24*60)) % 60

        GameDate(dateYear, dateMonth, dateDay + 1, dateHour, dateMinutes)

    def startSidebarClock()(using sid: SidebarActor, c: Clock, p: Plugin): Unit =
        import BallCore.UI.ChatElements._

        val handler: Consumer[ScheduledTask] = { _ =>
            val date = time()
            val monat = Month.fromOrdinal(date.month.toInt)
            val suffixed = s"${date.day}" +
                ((if date.day < 20 then date.day else date.day%10) match
                    case 1 => "st"
                    case 2 => "nd"
                    case 3 => "rd"
                    case _ => "th")
            sid.setAll(SidebarLine.date, Some(txt" ${suffixed} of ${monat}, ${date.year}"))
            sid.setAll(SidebarLine.time, Some(txt" ${date.hour}:${date.minute.toString.reverse.padTo(2, '0').reverse}"))
            p.getServer().getGlobalRegionScheduler().execute(p, () => {
                val minute = (date.hour*60 + date.minute)*16.6666
                Bukkit.getServer().getWorld("world").setTime(minute.floor.toLong + 18000)
            })
        }
        val _ = p.getServer().getAsyncScheduler().runAtFixedRate(p, handler, 0, 1000, TimeUnit.MILLISECONDS)
