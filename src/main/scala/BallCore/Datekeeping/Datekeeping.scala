// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Datekeeping

import BallCore.DataStructures.Clock
import BallCore.Sidebar.{SidebarActor, SidebarLine}
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

import java.time.temporal.{Temporal, TemporalUnit}
import java.time.{Duration, Instant, OffsetDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

case class GameDate(
    year: Long, // 12*31*24*60 minutes
    month: Long, // 31*24*60 minutes
    day: Long, // 24*60 minutes
    hour: Long, // 60 minutes
    minute: Long,
):
    def toDateString: String =
        val monat = Month.fromOrdinal(month.toInt)
        val suffixed = s"${day}" +
            ((if day < 20 then day else day % 10) match
                case 1 => "st"
                case 2 => "nd"
                case 3 => "rd"
                case _ => "th"
            )
        s"$suffixed of ${monat.displayName}, ${year}"

    def toTimeString: String =
        s"$hour:${minute.toString.reverse.padTo(2, '0').reverse}"

enum Season:
    case spring
    case summer
    case autumn
    case winter

    def display: String =
        this match
            case Season.spring => "spring"
            case Season.summer => "summer"
            case Season.autumn => "autumn"
            case Season.winter => "winter"

enum DateUnit(val width: Duration) extends TemporalUnit:
    case minute extends DateUnit(Datekeeping.Periods.minute)
    case hour extends DateUnit(Datekeeping.Periods.hour)
    case day extends DateUnit(Datekeeping.Periods.day)
    case month extends DateUnit(Datekeeping.Periods.month)
    case year extends DateUnit(Datekeeping.Periods.year)

    override def isDateBased: Boolean = false

    override def isTimeBased: Boolean = false

    override def isDurationEstimated: Boolean = false

    override def getDuration: Duration = width

    override def between(
        temporal1Inclusive: Temporal,
        temporal2Exclusive: Temporal,
    ): Long =
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

    def displayName: String = this match
        case Nieuwice => "Early Winter"
        case Bleibschnee => "Mid Winter"
        case Coldwane => "Late Winter"
        case Lluvita => "Early Spring"
        case Floraison => "Mid Spring"
        case Caldera => "Late Spring"
        case Zha => "Early Summer"
        case Dashu => "Mid Summer"
        case Nurui => "Late Summer"
        case Sarada => "Early Autumn"
        case Aban => "Mid Autumn"
        case Sisira => "Late Autumn"

    def season: Season = this match
        case Nieuwice => Season.winter
        case Bleibschnee => Season.winter
        case Coldwane => Season.winter
        case Lluvita => Season.spring
        case Floraison => Season.spring
        case Caldera => Season.spring
        case Zha => Season.summer
        case Dashu => Season.summer
        case Nurui => Season.summer
        case Sarada => Season.autumn
        case Aban => Season.autumn
        case Sisira => Season.autumn

object Datekeeping:
    // pizza tower release date on steam
    val epoch: OffsetDateTime =
        truncateLarge(Instant.ofEpochSecond(1674756019), DateUnit.year)

    // start year (pizza tower album track count * 10)
    val year0 = 730

    object Periods:
        val minute: Duration = Duration.ofMillis(2500)
        val hour: Duration = minute multipliedBy 60
        val day: Duration = hour multipliedBy 24
        val month: Duration = day multipliedBy 31
        val year: Duration = month multipliedBy 12

    private def truncateLarge(
        instant: Instant,
        truncatedTo: TemporalUnit,
    ): OffsetDateTime =
        val dur = truncatedTo.getDuration.toNanos
        val nanos = instant.getNano
        val seconds = instant.getEpochSecond
        val nod = (seconds * 1000000000) + nanos
        val result = Math.floorDiv(nod, dur) * dur
        OffsetDateTime.ofInstant(
            instant.plusNanos(result - nod),
            ZoneOffset.UTC,
        )

    def timeFrom(date: OffsetDateTime): GameDate =
        // the only thing in IRL units
        val diff = Duration.between(epoch, date).toMillis

        // ingame minutes that have elapsed since the epoch
        val minutes = diff / 2500

        // date elements
        val dateYear = year0 + minutes / (12 * 31 * 24 * 60)
        val dateMonth = (minutes % (12 * 31 * 24 * 60)) / (31 * 24 * 60)
        val dateDay =
            ((minutes % (12 * 31 * 24 * 60)) % (31 * 24 * 60)) / (24 * 60)
        val dateHour =
            (((minutes % (12 * 31 * 24 * 60)) % (31 * 24 * 60)) % (24 * 60)) / 60
        val dateMinutes =
            (((minutes % (12 * 31 * 24 * 60)) % (31 * 24 * 60)) % (24 * 60)) % 60

        GameDate(dateYear, dateMonth, dateDay + 1, dateHour, dateMinutes)

    def time()(using clock: Clock): GameDate =
        timeFrom(clock.now())

    def startSidebarClock()(using
        sid: SidebarActor,
        c: Clock,
        p: Plugin,
    ): Unit =
        import BallCore.UI.ChatElements.*

        val handler: Consumer[ScheduledTask] = { _ =>
            val date = time()
            sid.setAll(
                SidebarLine.date,
                Some(txt" ${date.toDateString}"),
            )
            sid.setAll(
                SidebarLine.time,
                Some(
                    txt" ${date.toTimeString}"
                ),
            )
            if p.isEnabled() then
                p.getServer.getGlobalRegionScheduler
                    .execute(
                        p,
                        () => {
                            val minute =
                                (date.hour * 60 + date.minute) * 16.6666
                            Bukkit.getServer
                                .getWorld("world")
                                .setTime(minute.floor.toLong + 18000)
                        },
                    )
        }
        val _ = p.getServer.getAsyncScheduler
            .runAtFixedRate(p, handler, 0, 1000, TimeUnit.MILLISECONDS)
