package BallCore.Rest

import cats.effect.IO
import java.{util => ju}
import BallCore.Sidebar.SidebarActor
import BallCore.Sidebar.SidebarLine
import org.bukkit.Bukkit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

class IngameRestManagerHooks(using sidebar: SidebarActor)
    extends RestManagerHooks:
    private def restString(value: Double): Component =
        import BallCore.TextComponents._
        val res =
            if value >= 0.8 then txt"Very Well Rested"
            else if value >= 0.6 then txt"Well Rested"
            else if value >= 0.4 then txt"Rested"
            else if value >= 0.2 then txt"Somewhat Rested"
            else if value >= 0.05 then txt"Barely Rested"
            else txt"Not Rested"
        res.color(TextColor.fromHexString("#8adcff"))
    def updateSidebar(playerID: ju.UUID, rest: Double): IO[Unit] =
        IO {
            sidebar.set(
                SidebarLine.rest,
                Bukkit.getPlayer(playerID),
                Some(restString(rest)),
            )
        }
