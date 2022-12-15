package BallCore.Acclimation

import BallCore.Storage.*

import java.util.UUID
import scala.language.implicitConversions

class Storage()(using kvs: KeyVal):
    private def get(player: UUID, key: String, default: Float): Float =
        kvs.get[Float](player, key) match
            case Some(value) => value
            case None => default
    private def set(player: UUID, key: String, value: Float) =
        kvs.set(player, key, value)

    /** temperature ranges from 0.0 (coldest climate) to 1.0 (hottest climate) */
    def getTemperature(player: UUID): Float =
        get(player, "acclimation.temperature", 0.5)
    def setTemperature(player: UUID, value: Float) =
        set(player, "acclimation.temperature", value)

    /** elevation ranges from -1.0 (bedrock) to 0.0 (sea level) to 1.0 (build limit) */
    def getElevation(player: UUID): Float =
        get(player, "acclimation.elevation", 0.0)
    def setElevation(player: UUID, value: Float) =
        set(player, "acclimation.elevation", value)

    /** elevation ranges from -1.0 (westmost) to 0.0 (centre) to 1.0 (eastmost) */
    def getLongitude(player: UUID): Float =
        get(player, "acclimation.longitude", 0.0)
    def setLongitude(player: UUID, value: Float) =
        set(player, "acclimation.longitude", value)

    /** latitude ranges from -1.0 (northmost) to 0.0 (centre) to 1.0 (southmost) */
    def getLatitude(player: UUID): Float =
        get(player, "acclimation.latitude", 0.0)
    def setLatitude(player: UUID, value: Float) =
        set(player, "acclimation.latitude", value)
