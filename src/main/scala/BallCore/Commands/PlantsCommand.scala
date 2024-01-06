package BallCore.Commands

import BallCore.Plants.PlantListProgram
import BallCore.UI.UIProgramRunner
import org.bukkit.plugin.Plugin
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import BallCore.Folia.FireAndForget
import BallCore.Advancements.ViewPlants
import BallCore.Plants.Climate

class PlantsCommand(using prompts: BallCore.UI.Prompts, plugin: Plugin):
    val node =
        CommandTree("plants")
            .executesPlayer({ (sender, args) =>
                ViewPlants.grant(sender, "plants_used")
                given ExecutionContext = EntityExecutionContext(sender)
                FireAndForget {
                    val climate = Climate.climateAt(
                        sender.getX().toInt,
                        sender.getY().toInt,
                        sender.getZ().toInt,
                    )
                    val p = PlantListProgram()
                    val runner = UIProgramRunner(p, p.Flags(climate), sender)
                    runner.render()
                }
            }: PlayerCommandExecutor)
