package BallCore.NoodleEditor

import BallCore.Storage.SQLManager
import skunk.Session
import skunk.Transaction
import cats.effect.IO
import skunk.implicits._
import skunk.codec.all._
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.GeometryFactory
import org.bukkit.Bukkit
import java.util.UUID
import cats.syntax.all._

class EssenceDrainer(using sql: SQLManager, em: EssenceManager, dm: DelinquencyManager):
    private val geometryFactory = GeometryFactory()
    private val multiLineStringCodec =
        import org.locationtech.jts.io.geojson.{GeoJsonReader, GeoJsonWriter}

        text.imap[MultiLineString] { json =>
            GeoJsonReader(geometryFactory)
                .read(json)
                .asInstanceOf[MultiLineString]
        } { polygon =>
            GeoJsonWriter().write(polygon)
        }

    def drainEssence(using Session[IO], Transaction[IO]): IO[Unit] =
        for
            stream <- sql.queryStreamIO(
                sql"""
            SELECT ST_AsGeoJSON(Area), GroupID, WorldID FROM Noodles;
            """,
                (multiLineStringCodec *: uuid *: uuid),
                skunk.Void,
            )
            payments <- stream.parEvalMapUnordered(10) {
                (noodle, group, world) =>
                    val noodleGraph =
                        NoodleManager.recover(noodle, Bukkit.getWorld(world))
                    val cost = PlayerState.cost(noodleGraph)
                    em.depleteEssenceFor(group, cost).map((group, cost, _))
            }.compile.toList
            _ <- payments.foldLeft(Map[UUID, (Int, Int)]()) { case (map, (group, cost, unpaid)) =>
                val (cost_, unpaid_) = map.getOrElse(group, (0, 0))
                map + (group -> (cost + cost_, unpaid_ + unpaid))
            }.toList.traverse_ { case (group, (cost, unpaid)) =>
                if unpaid == 0 then
                    dm.paid(group, cost)
                else
                    dm.failedToPay(group, cost)
            }
        yield ()
