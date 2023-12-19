package BallCore.NoodleEditor

import cats.effect.IO

import org.bukkit.Location
import BallCore.Folia.LocationExecutionContext
import org.bukkit.plugin.Plugin
import org.bukkit.Material

object GameEssenceManagerHooks:
    val levels = Vector(
        Material.WHITE_CONCRETE,
        Material.TERRACOTTA,
        Material.RED_CONCRETE,
        Material.ORANGE_TERRACOTTA,
        Material.YELLOW_TERRACOTTA,
        Material.GREEN_TERRACOTTA,
        Material.LIGHT_BLUE_TERRACOTTA,
        Material.BLUE_TERRACOTTA,
        Material.PURPLE_TERRACOTTA,
        Material.RED_TERRACOTTA,
        Material.ORANGE_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.BLUE_CONCRETE,
        Material.PURPLE_CONCRETE,
        Material.MAGENTA_CONCRETE,
        Material.PINK_CONCRETE,
    )

class GameEssenceManagerHooks(using p: Plugin) extends EssenceManagerHooks:
    override def updateHeart(l: Location, amount: Int): IO[Unit] =
        IO {
            val block = l.getBlock()
            val kind =
                if amount >= GameEssenceManagerHooks.levels.size then
                    GameEssenceManagerHooks.levels.last
                else GameEssenceManagerHooks.levels(amount)
            block.setType(kind)
        }.evalOn(LocationExecutionContext(l))
