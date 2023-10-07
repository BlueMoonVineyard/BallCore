// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Acclimation

import BallCore.Storage.*

import java.util.UUID
import scala.language.implicitConversions
import org.bukkit.Bukkit
import org.bukkit.Location
import skunk.Session
import cats.effect.IO

class Storage()(using kvs: KeyVal):
    private def get(player: UUID, key: String, default: Double)(using Session[IO]): IO[Double] =
        kvs.get[Double](player, key).map { x =>
            x match
                case Some(value) => value
                case None => default
        }
    private def set(player: UUID, key: String, value: Double)(using Session[IO]): IO[Unit] =
        kvs.set(player, key, value)

    def getLastSeenLocation(player: UUID)(using Session[IO]): IO[Location] =
        kvs.get[(Double, Double, Double, UUID)](player, "acclimation.last-seen-location").map { x =>
            x match
                case Some((x, y, z, world)) =>
                    Location(Bukkit.getWorld(world), x, y, z)
                case None => Bukkit.getOfflinePlayer(player).getBedSpawnLocation()
        }

    def setLastSeenLocation(player: UUID, to: Location)(using Session[IO]): IO[Unit] =
        val tuple = (to.getX(), to.getY(), to.getZ(), to.getWorld().getUID())
        kvs.set(player, "acclimation.last-seen-location", tuple)

    /** temperature ranges from 0.0 (coldest climate) to 1.0 (hottest climate) */
    def getTemperature(player: UUID)(using Session[IO]): IO[Double] =
        get(player, "acclimation.temperature", 0.5)
    def setTemperature(player: UUID, value: Double)(using Session[IO]): IO[Unit] =
        set(player, "acclimation.temperature", value)

    /** elevation ranges from -1.0 (bedrock) to 0.0 (sea level) to 1.0 (build limit) */
    def getElevation(player: UUID)(using Session[IO]): IO[Double] =
        get(player, "acclimation.elevation", 0.0)
    def setElevation(player: UUID, value: Double)(using Session[IO]): IO[Unit] =
        set(player, "acclimation.elevation", value)

    /** elevation ranges from -1.0 (westmost) to 0.0 (centre) to 1.0 (eastmost) */
    def getLongitude(player: UUID)(using Session[IO]): IO[Double] =
        get(player, "acclimation.longitude", 0.0)
    def setLongitude(player: UUID, value: Double)(using Session[IO]): IO[Unit] =
        set(player, "acclimation.longitude", value)

    /** latitude ranges from -1.0 (northmost) to 0.0 (centre) to 1.0 (southmost) */
    def getLatitude(player: UUID)(using Session[IO]): IO[Double] =
        get(player, "acclimation.latitude", 0.0)
    def setLatitude(player: UUID, value: Double)(using Session[IO]): IO[Unit] =
        set(player, "acclimation.latitude", value)
