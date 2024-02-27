// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import BallCore.CraftingStations.WorkChestUtils.findWorkChest
import BallCore.DataStructures.Actor
import BallCore.Folia.{
    EntityExecutionContext,
    FireAndForget,
    LocationExecutionContext,
}
import BallCore.UI.ChatElements.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.{Block, BlockFace, Chest, DoubleChest}
import org.bukkit.entity.Player
import org.bukkit.inventory.{Inventory, ItemStack}
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.util.chaining.*
import BallCore.CustomItems.ItemRegistry
import BallCore.Advancements.BallAdvancement
import org.bukkit.NamespacedKey
import BallCore.Advancements.UseStation
import cats.effect.IO
import cats.syntax.all._

private class AdvancementTracker(
    matches: Set[NamespacedKey],
    advancement: BallAdvancement[_],
    criteria: advancement.Criteria,
):
    def check(player: Player, recipe: Recipe): Unit =
        if matches.contains(recipe.id) then
            val _ = advancement.grant(player, criteria)

private val advancements: List[AdvancementTracker] = List(
)

enum CraftingMessage:
    case startWorking(p: Player, f: Block, r: Recipe, autorepeat: Boolean)
    case stopWorking(p: Player)
    case tick

object WorkChestUtils:
    private val sides: List[BlockFace] = List(
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.EAST,
        BlockFace.UP,
    )

    def findWorkChest(nearby: Block): Option[(Block, Inventory)] =
        sides
            .map(face => nearby.getRelative(face))
            .find(block =>
                block.getType == Material.CHEST || block.getType == Material.TRAPPED_CHEST
            )
            .flatMap { chest =>
                chest.getState() match
                    case double: DoubleChest =>
                        Some((chest, double.getInventory))
                    case single: Chest => Some((chest, single.getInventory))
                    case _ => None
            }

    private def splitinate(inputs: (ItemStack, Int)): List[ItemStack] =
        if inputs._2 <= 64 then
            List(inputs._1.clone().tap(_.setAmount(inputs._2)))
        else
            inputs._1.clone().tap(_.setAmount(64)) :: splitinate(
                (inputs._1, inputs._2 - 64)
            )

    def insertInto(
        outputs: List[(ItemStack, Int)],
        inventory: Inventory,
        at: Block,
    ): Unit =
        outputs.foreach { output =>
            splitinate(output).foreach { stack =>
                inventory.addItem(stack).forEach { (_, extra) =>
                    val _ =
                        at.getWorld.dropItemNaturally(at.getLocation(), stack)
                }
            }
        }

    def ensureLoaded(b: Block): Unit =
        // ensure any double chests across chunk seams are loaded
        sides.foreach(face => b.getRelative(face))

private def updateFirst[A](l: List[A])(p: A => Boolean)(
    update: A => A
): List[A] =
    val found = l.zipWithIndex.find { case (item, _) => p(item) }
    found match
        case Some((item, idx)) => l.updated(idx, update(item))
        case None => l

object CraftingActor:
    def inventoryContains(
        recipe: List[(RecipeIngredient, Int)],
        inventory: Inventory,
    )(using ir: ItemRegistry): Boolean =
        var rezept = recipe
        inventory.getStorageContents.foreach { is =>
            if is != null then
                rezept = updateFirst(rezept)((ingredient, amount) =>
                    ingredient.test(is) && amount > 0
                )((ingredient, amount) => (ingredient, amount - is.getAmount))
        }
        rezept.forall(_._2 <= 0)

    def validateJob(
        recipe: Recipe,
        workstation: Block,
    )(using ItemRegistry): Boolean =
        WorkChestUtils.findWorkChest(workstation) match
            case None => false
            case Some((_, chest)) =>
                inventoryContains(recipe.inputs, chest)

class CraftingActor(using p: Plugin, ir: ItemRegistry)
    extends Actor[CraftingMessage]:
    private var jobs: Map[Block, Job] = Map[Block, Job]()
    private val sides: List[BlockFace] =
        List(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST)

    protected def handleInit(): Unit =
        val _ = p.getServer.getAsyncScheduler
            .runAtFixedRate(
                p,
                _ => send(CraftingMessage.tick),
                0,
                1,
                TimeUnit.SECONDS,
            )

    protected def handleShutdown(): Unit =
        ()

    private def ensureLoaded(b: Block): Unit =
        // ensure any double chests across chunk seams are loaded
        sides.foreach(face => b.getRelative(face))

    private def removeFrom(
        recipe: List[(RecipeIngredient, Int)],
        inventory: Inventory,
    ): Boolean =
        var rezept = recipe
        inventory.getStorageContents.foreach { is =>
            if is != null then
                rezept = updateFirst(rezept)((ingredient, amount) =>
                    ingredient.test(is) && amount > 0
                ) { (ingredient, amount) =>
                    val targeted =
                        is.clone().tap(_.setAmount(amount.min(is.getAmount)))
                    inventory.removeItem(targeted)
                    (ingredient, amount - targeted.getAmount)
                }
        }
        rezept.forall(_._2 <= 0)

    private def completeJob(job: Job): Boolean =
        val (workChest, inventory) =
            WorkChestUtils.findWorkChest(job.factory) match
                case None =>
                    job.workedBy.foreach { player =>
                        given ec: ExecutionContext =
                            EntityExecutionContext(player)

                        FireAndForget {
                            notifyFailedJob(player, job, "Chest Missing!")
                        }
                    }
                    return false
                case Some(value) => value

        ensureLoaded(workChest)

        if removeFrom(job.recipe.inputs, inventory) then
            WorkChestUtils.insertInto(
                job.recipe.outputs,
                inventory,
                job.factory,
            )

            job.workedBy.foreach { player =>
                given ec: ExecutionContext = EntityExecutionContext(player)

                FireAndForget {
                    notifyFinishedJob(player, job)
                }
            }
            true
        else
            job.workedBy.foreach { player =>
                given ec: ExecutionContext = EntityExecutionContext(player)

                FireAndForget {
                    notifyFailedJob(player, job, "Ingredients Missing!")
                }
            }
            false

    private def notifyFinishedJob(player: Player, job: Job): Unit =
        val component =
            Component
                .text()
                .append(job.recipe.name.color(NamedTextColor.GOLD))
                .append(Component.text(" "))
                .append(Component.text("|".repeat(40), NamedTextColor.GREEN))
                .append(Component.text(" "))
                .append(Component.text(s"All Done!"))
                .build()
        UseStation.grant(player, "worked_recipe")
        advancements.foreach(_.check(player, job.recipe))
        player.sendActionBar(component)

    private def notifyFailedJob(player: Player, job: Job, what: String): Unit =
        val component =
            Component
                .text()
                .append(job.recipe.name.color(NamedTextColor.GOLD))
                .append(Component.text(" "))
                .append(Component.text("|".repeat(40), NamedTextColor.RED))
                .append(Component.text(" "))
                .append(Component.text(what))
                .build()
        player.sendActionBar(component)

    private def notifyInProgressJob(player: Player, job: Job): Unit =
        val progressBarSize = 40
        val done = (progressBarSize * job.currentWork) / job.recipe.work
        val notDone = progressBarSize - done

        val component =
            Component
                .text()
                .append(job.recipe.name.color(NamedTextColor.GOLD))
                .append(Component.text(" "))
                .append(Component.text("|".repeat(done), NamedTextColor.GREEN))
                .append(
                    Component.text("|".repeat(notDone), NamedTextColor.GRAY)
                )
                .append(Component.text(" "))
                .append(
                    Component.text(
                        s"${job.currentWork} / ${job.recipe.work} Work"
                    )
                )
                .build()

        player.sendActionBar(component)

    private def stopWorking(p: Player): Unit =
        jobs.find(_._2.workedBy.contains(p)) match
            case None =>
            case Some((factory, job)) =>
                if job.workedBy.length == 1 then jobs = jobs.removed(factory)
                else
                    jobs = jobs.updated(
                        factory,
                        job.copy(workedBy = job.workedBy.filterNot(_ == p)),
                    )

    def handle(m: CraftingMessage): Unit =
        m match
            case CraftingMessage.startWorking(p, f, r, repeat) =>
                stopWorking(p)
                jobs.get(f) match
                    case None =>
                        jobs = jobs.updated(f, Job(f, r, 0, List(p), repeat))
                    case Some(job) if job.recipe == r =>
                        jobs = jobs.updated(
                            f,
                            job.copy(workedBy = p :: job.workedBy),
                        )
                    case Some(_) =>
                        p.sendServerMessage(
                            txt"Someone else is already working a different recipe with this workstation!"
                        )
            case CraftingMessage.stopWorking(p) =>
                stopWorking(p)
            case CraftingMessage.tick =>
                jobs = jobs.map { (b, j) =>
                    (
                        b,
                        if j.workedBy.length >= j.recipe.minimumPlayersRequiredToWork
                        then
                            j.copy(currentWork =
                                j.currentWork + j.workedBy.length
                            )
                        else j,
                    )
                }
                jobs = fs2.Stream
                    .iterable[IO, (Block, Job)](jobs)
                    .parEvalMap(10) { (b, j) =>
                        if j.currentWork >= j.recipe.work then
                            for
                                ok <- IO { completeJob(j) }
                                    .evalOn(LocationExecutionContext(j.factory.getLocation()))
                                shouldRepeat <- if j.repeat then IO {
                                    CraftingActor.validateJob(j.recipe, j.factory)
                                }.evalOn(LocationExecutionContext(j.factory.getLocation()))
                                else IO.pure(false)
                            yield
                                if shouldRepeat then
                                    Some((b, j.copy(currentWork = 0)))
                                else
                                    None
                        else
                            j.workedBy.traverse_ { player =>
                                IO {
                                    notifyInProgressJob(player, j)
                                }.evalOn(EntityExecutionContext(player))
                            }.map(_ => Some((b, j)))
                    }
                    .compile
                    .toList
                    .map(_.flatten)
                    .unsafeRunSync()(using cats.effect.unsafe.IORuntime.global)
                    .toMap
