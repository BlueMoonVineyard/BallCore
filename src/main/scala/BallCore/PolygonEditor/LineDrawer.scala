package BallCore.PolygonEditor

import org.bukkit.Color
import org.bukkit.entity.Player
import org.bukkit.entity.ItemDisplay
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.entity.EntityType
import org.bukkit.entity.Display.Brightness
import org.bukkit.util.Transformation
import org.joml.Vector3f
import org.joml.AxisAngle4f

enum LineColour:
    case red
    case orange
    case yellow
    case white
    case teal
    case lime
    case gray

    def customModelData: Int =
        this match
            case LineColour.red => 9
            case LineColour.orange => 10
            case LineColour.yellow => 11
            case LineColour.white => 12
            case LineColour.teal => 13
            case LineColour.lime => 14
            case LineColour.gray => 15

    def asBukkitColor: Color =
        this match
            case LineColour.red => Color.RED
            case LineColour.orange => Color.ORANGE
            case LineColour.yellow => Color.YELLOW
            case LineColour.white => Color.WHITE
            case LineColour.teal => Color.TEAL
            case LineColour.lime => Color.LIME
            case LineColour.gray => Color.GRAY

class LineDrawer(val player: Player, offset: org.bukkit.util.Vector)(using
    p: Plugin
):
    import scala.util.chaining._

    private var lineEntities = List[ItemDisplay]()
    private var previousLines = List[(Location, Location, LineColour)]()

    private def itemStackOfColour(l: LineColour): ItemStack =
        val is = ItemStack(Material.STICK)
        is.setItemMeta(
            is.getItemMeta().tap(_.setCustomModelData(l.customModelData))
        )
        is

    def clear(): Unit =
        lineEntities.foreach(_.remove())

    def setLines(lines: List[(Location, Location, LineColour)]): Unit =
        if previousLines == lines then return

        if lineEntities.size < lines.size then
            for i <- (lineEntities.size + 1 to lines.size).map(_ - 1) do
                lineEntities = lineEntities.appended(
                    lines(i)._1
                        .getWorld()
                        .spawnEntity(
                            lines(i)._1.clone().add(offset),
                            EntityType.ITEM_DISPLAY,
                        )
                        .asInstanceOf[ItemDisplay]
                        .tap(_.setVisibleByDefault(false))
                        .tap(_.setGlowing(true))
                        .tap(_.setBrightness(Brightness(15, 15)))
                        .tap(x => player.showEntity(p, x))
                )
        else if lines.size < lineEntities.size then
            lineEntities
                .takeRight(lineEntities.size - lines.size)
                .foreach(_.remove())
            lineEntities = lineEntities.take(lines.size)

        lineEntities.lazyZip(lines).zipWithIndex.foreach {
            case ((display, (lineStart, lineEnd, colour)), idx) =>
                if previousLines.size != lines.size || previousLines(
                        idx
                    ) != lines(idx)
                then
                    display.setItemStack(itemStackOfColour(colour))
                    val from = lineStart.clone().add(offset)
                    val to = lineEnd.clone().add(offset)

                    display.setGlowColorOverride(colour.asBukkitColor)

                    val targetFrom =
                        from
                            .clone()
                            .setDirection(
                                to.clone()
                                    .subtract(from)
                                    .toVector()
                            )
                    val _ = display.teleportAsync(targetFrom)
                    val distance = from.distance(to)
                    val transformation = Transformation(
                        Vector3f(0f, 0f, distance.toFloat / 2f),
                        AxisAngle4f(),
                        Vector3f(1f, 1f, 16f * distance.toFloat),
                        AxisAngle4f(),
                    )
                    display.setTransformation(transformation)
        }

        previousLines = lines
