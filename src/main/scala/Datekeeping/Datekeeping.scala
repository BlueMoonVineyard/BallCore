package BallCore.Datekeeping

import java.time.Instant
import BallCore.DataStructures.Clock
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.time.temporal.Temporal

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

object Datekeeping:
    // pizza tower release date on steam
    val epoch = Instant.ofEpochSecond(1674756019).truncatedTo(DateUnit.year)

    // start year (pizza tower album track count * 10)
    val year0 = 730

    object Periods:
        val minute = Duration.ofMillis(2500)
        val hour = minute multipliedBy 60
        val day = hour multipliedBy 24
        val month = day multipliedBy 31
        val year = month multipliedBy 12

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

        GameDate(dateYear, dateMonth, dateDay, dateHour, dateMinutes)
