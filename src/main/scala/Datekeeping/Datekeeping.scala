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

object Datekeeping:
    // pizza tower release date on steam
    val epoch = Instant.ofEpochSecond(1674756019)

    // start year (pizza tower album track count * 10)
    val year0 = 730

    def time()(using clock: Clock): GameDate =
        val diff = Duration.between(epoch, clock.now()).toMillis()
        val minutes = diff / 2500
        val year = year0 + minutes / (12*31*24*60)
        val month = (minutes % (12*31*24*60)) / (31*24*60)
        val day = ((minutes % (12*31*24*60)) % (31*24*60)) / (24*60)
        val hour = (((minutes % (12*31*24*60)) % (31*24*60)) % (24*60)) / 60
        val gameMinutes = (((minutes % (12*31*24*60)) % (31*24*60)) % (24*60)) % 60
        GameDate(year, month, day, hour, gameMinutes)
