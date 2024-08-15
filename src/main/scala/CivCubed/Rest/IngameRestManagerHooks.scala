package CivCubed.Rest

import cats.effect.IO
import java.{util => ju}
import CivCubed.Sidebar.SidebarActor
import CivCubed.Sidebar.SidebarLine
import org.bukkit.Bukkit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Location
import CivCubed.Folia.LocationExecutionContext
import CivCubed.Rest.RoomEvaluator.getMajorityRole
import cats.data.OptionT
import org.bukkit.plugin.Plugin
import skunk.Session

class IngameRestManagerHooks(using sidebar: SidebarActor, p: Plugin)
    extends RestManagerHooks:
    private def restString(value: Double): Component =
        import CivCubed.TextComponents._
        val res =
            if value >= 0.8 then txt" Very Well Rested"
            else if value >= 0.6 then txt" Well Rested"
            else if value >= 0.4 then txt" Rested"
            else if value >= 0.2 then txt" Somewhat Rested"
            else if value >= 0.05 then txt" Barely Rested"
            else txt" Not Rested"
        res.color(TextColor.fromHexString("#8adcff"))
    def updateSidebar(playerID: ju.UUID, rest: Double): IO[Unit] =
        IO {
            sidebar.set(
                SidebarLine.rest,
                Bukkit.getPlayer(playerID),
                Some(restString(rest)),
            )
        }
    override def evaluateRoomAt(rm: RestManager, location: Location)(using Session[IO]): IO[Option[(RoomRole, Evaluation, Bounds)]] =
        (for
            data <- OptionT(IO.blocking {
                RoomEvaluator.gatherBlocksAround(location)
            }.evalOn(LocationExecutionContext(location)))
            people <- OptionT.liftF(rm.getSpawnLocationsWithin(data._2.lower, data._2.upper, location.getWorld.getUID))
            evaluate = getMajorityRole(data._1, data._2, people.length)
            trio <- OptionT.fromOption[IO](evaluate.map { (role, evaluation) => (role, evaluation, data._2) }.toOption)
        yield trio).value