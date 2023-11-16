package BallCore.Sigils

import BallCore.Beacons.BeaconID
import cats.effect.IO
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Geometry

class GameBattleHooks extends BattleHooks:
    override def spawnPillarFor(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        contestedArea: Geometry,
        defensiveBeacon: BeaconID,
    ): IO[Unit] = ???

    override def battleDefended(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        defensiveBeacon: BeaconID,
    ): IO[Unit] = ???

    override def battleTaken(
        battle: BattleID,
        offensiveBeacon: BeaconID,
        newOffensiveArea: Polygon,
        defensiveBeacon: BeaconID,
    ): IO[Unit] = ???
