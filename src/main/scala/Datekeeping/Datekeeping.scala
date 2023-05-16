package BallCore.Datekeeping

import java.time.Instant
import BallCore.DataStructures.Clock
import java.time.Duration

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

object Datekeeping:
    // pizza tower release date on steam
    val epoch = Instant.ofEpochSecond(1674756019)

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
