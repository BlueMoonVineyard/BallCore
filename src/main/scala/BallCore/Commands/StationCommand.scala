package BallCore.Commands

import BallCore.TextComponents.*
import BallCore.UI.UIProgramRunner
import org.bukkit.plugin.Plugin
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import BallCore.Folia.FireAndForget
import net.kyori.adventure.text.Component
import BallCore.CraftingStations.CraftingStation
import BallCore.CraftingStations.{StationListProgram, RecipeViewerProgram}
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class StationCommand(using
    stations: List[CraftingStation],
    p: Plugin,
    prompts: BallCore.UI.Prompts,
):
    import scala.jdk.CollectionConverters._

    private def stringify(c: Component) =
        PlainTextComponentSerializer.plainText().serialize(c)
    val node =
        CommandTree("workstations")
            .executesPlayer({ (sender, args) =>
                given ExecutionContext = EntityExecutionContext(sender)
                FireAndForget {
                    val p = StationListProgram(stations)
                    val runner = UIProgramRunner(
                        p,
                        p.Flags(0),
                        sender,
                    )
                    runner.render()
                }
            }: PlayerCommandExecutor)
            .`then`(
                GreedyStringArgument("workstation")
                    .replaceSuggestions(
                        ArgumentSuggestions.strings(
                            stations
                                .map(x =>
                                    stringify(
                                        x.template.getItemMeta().displayName()
                                    )
                                )
                                .asJava
                        )
                    )
                    .executesPlayer({ (sender, args) =>
                        val arg = args.getUnchecked[String]("workstation")
                        stations.find(station =>
                            stringify(
                                station.template.getItemMeta().displayName()
                            ) == arg
                        ) match
                            case None =>
                                sender.sendServerMessage(
                                    trans"commands.station.workstation-not-found"
                                )
                            case Some(station) =>
                                val p = RecipeViewerProgram(
                                    stations,
                                    station.recipes,
                                )
                                val runner = UIProgramRunner(
                                    p,
                                    p.Flags(0),
                                    sender,
                                )
                                runner.render()
                    }: PlayerCommandExecutor)
            )
