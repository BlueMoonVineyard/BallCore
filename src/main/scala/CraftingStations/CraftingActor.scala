// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.CraftingStations

import org.bukkit.entity.Player
import org.bukkit.block.Block
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import BallCore.Folia.LocationExecutionContext
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext
import org.bukkit.block.BlockFace
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import scala.util.chaining._
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import BallCore.DataStructures.Actor
import BallCore.Folia.FireAndForget
import BallCore.UI.ChatElements._

enum CraftingMessage:
	case startWorking(p: Player, f: Block, r: Recipe)
	case stopWorking(p: Player)
	case tick

class CraftingActor(using p: Plugin) extends Actor[CraftingMessage]:
	var jobs = Map[Block, Job]()
	val sides = List(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST)

	protected def handleInit(): Unit =
		val _ = p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(CraftingMessage.tick), 0, 1, TimeUnit.SECONDS)
	protected def handleShutdown(): Unit =
		()

	def ensureLoaded(b: Block): Unit =
		// ensure any double chests across chunk seams are loaded
		sides.foreach(face => b.getRelative(face))

	def updateFirst[A](l: List[A])(p: A => Boolean)(update: A => A): List[A] =
		val found = l.zipWithIndex.find { case (item, _) => p(item) }
		found match
			case Some((item, idx)) => l.updated(idx, update(item))
			case None => l

	def inventoryContains(recipe: List[(RecipeChoice, Int)], inventory: Inventory): Boolean =
		var rezept = recipe
		inventory.getStorageContents().foreach { is =>
			if is != null then
				rezept = updateFirst(rezept)((ingredient, amount) => ingredient.test(is) && amount > 0)((ingredient, amount) => (ingredient, amount - is.getAmount()))
		}
		rezept.forall(_._2 <= 0)

	def removeFrom(recipe: List[(RecipeChoice, Int)], inventory: Inventory): Boolean =
		var rezept = recipe
		inventory.getStorageContents().foreach { is =>
			if is != null then
				rezept = updateFirst(rezept)((ingredient, amount) => ingredient.test(is) && amount > 0) { (ingredient, amount) => 
					val targeted = is.clone().tap(_.setAmount(amount.min(is.getAmount())))
					inventory.removeItem(targeted)
					(ingredient, amount - targeted.getAmount())
				}
		}
		rezept.forall(_._2 <= 0)

	def insertInto(outputs: List[ItemStack], inventory: Inventory, at: Block): Unit =
		outputs.foreach { output =>
			if !inventory.addItem(output).isEmpty() then
				val _ = at.getWorld().dropItemNaturally(at.getLocation(), output)
		}

	def completeJob(job: Job): Unit =
		val workChest = sides.map(face => job.factory.getRelative(face)).find(block => block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) match
			case None =>
				job.workedBy.foreach { player =>
					given ec: ExecutionContext = EntityExecutionContext(player)
					FireAndForget {
						notifyFailedJob(player, job, "Chest Missing!")
					}
				}
				return
			case Some(value) => value

		ensureLoaded(workChest)
		val chest = workChest.getState().asInstanceOf[Chest]
		val inv = chest.getBlockInventory()

		if removeFrom(job.recipe.inputs, inv) then
			insertInto(job.recipe.outputs, inv, job.factory)

			job.workedBy.foreach { player =>
				given ec: ExecutionContext = EntityExecutionContext(player)
				FireAndForget {
					notifyFinishedJob(player, job)
				}
			}
		else
			job.workedBy.foreach { player =>
				given ec: ExecutionContext = EntityExecutionContext(player)
				FireAndForget {
					notifyFailedJob(player, job, "Ingredients Missing!")
				}
			}

	def notifyFinishedJob(player: Player, job: Job): Unit =
		val component =
			Component.text()
				.append(Component.text(job.recipe.name, NamedTextColor.GOLD))
				.append(Component.text(" "))
				.append(Component.text("|".repeat(40), NamedTextColor.GREEN))
				.append(Component.text(" "))
				.append(Component.text(s"All Done!"))
				.build()
		player.sendActionBar(component)

	def notifyFailedJob(player: Player, job: Job, what: String): Unit =
		val component =
			Component.text()
				.append(Component.text(job.recipe.name, NamedTextColor.GOLD))
				.append(Component.text(" "))
				.append(Component.text("|".repeat(40), NamedTextColor.RED))
				.append(Component.text(" "))
				.append(Component.text(what))
				.build()
		player.sendActionBar(component)

	def notifyInProgressJob(player: Player, job: Job): Unit =
		val progressBarSize = 40
		val done = (progressBarSize * job.currentWork) / job.recipe.work
		val notDone = progressBarSize - done

		val component =
			Component.text()
				.append(Component.text(job.recipe.name, NamedTextColor.GOLD))
				.append(Component.text(" "))
				.append(Component.text("|".repeat(done), NamedTextColor.GREEN))
				.append(Component.text("|".repeat(notDone), NamedTextColor.GRAY))
				.append(Component.text(" "))
				.append(Component.text(s"${job.currentWork} / ${job.recipe.work} Work"))
				.build()

		player.sendActionBar(component)

	def stopWorking(p: Player): Unit =
		jobs.find(_._2.workedBy.contains(p)) match
			case None =>
			case Some((factory, job)) =>
				if job.workedBy.length == 1 then
					jobs = jobs.removed(factory)
				else
					jobs = jobs.updated(factory, job.copy(workedBy = job.workedBy.filterNot(_ == p)))

	def handle(m: CraftingMessage): Unit =
		m match
			case CraftingMessage.startWorking(p, f, r) =>
				stopWorking(p)
				jobs.get(f) match
					case None =>
						jobs = jobs.updated(f, Job(f, r, 0, List(p)))
					case Some(job) if job.recipe == r =>
						jobs = jobs.updated(f, job.copy(workedBy = p :: job.workedBy))
					case Some(_) =>
						p.sendServerMessage(txt"Someone else is already working a different recipe with this workstation!")
			case CraftingMessage.stopWorking(p) =>
				stopWorking(p)
			case CraftingMessage.tick =>
				jobs = jobs.map { (b, j) =>
					(b, if j.workedBy.length >= j.recipe.minimumPlayersRequiredToWork then
						j.copy(currentWork = j.currentWork + j.workedBy.length)
					else
						j)
				}
				jobs = jobs.filterNot { (_, j) =>
					val cond = j.currentWork >= j.recipe.work
					if cond then
						given ec: ExecutionContext = LocationExecutionContext(j.factory.getLocation())
						FireAndForget { completeJob(j) }
					else
						j.workedBy.foreach { player =>
							given ec: ExecutionContext = EntityExecutionContext(player)
							FireAndForget { notifyInProgressJob(player, j) }
						}
					cond
				}
		
