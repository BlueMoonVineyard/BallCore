package BallCore.CraftingStations

import org.bukkit.entity.Player
import org.bukkit.block.Block
import java.util.concurrent.LinkedTransferQueue
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import BallCore.Folia.LocationExecutionContext
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import BallCore.Folia.EntityExecutionContext

trait Actor[Msg]:
	def handle(m: Msg): Unit

	val queue = LinkedTransferQueue[Msg]()

	def send(m: Msg): Unit =
		this.queue.add(m)
	def mainLoop(): Unit =
		this.queue.forEach { msg =>
			handle(msg)
		}

enum CraftingMessage:
	case startWorking(p: Player, f: Block, r: Recipe)
	case stopWorking(p: Player)
	case tick

class CraftingActor(using p: Plugin) extends Actor[CraftingMessage]:
	p.getServer().getAsyncScheduler().runAtFixedRate(p, _ => send(CraftingMessage.tick), 0, 1, TimeUnit.SECONDS)

	var jobs = Map[Player, Job]()

	def completeJob(player: Player, job: Job): Unit =
		// slurp inputs
		// dump outputs
		given ec: ExecutionContext = EntityExecutionContext(player)
		Future {
			// notify player of completion
		}

	def handle(m: CraftingMessage): Unit =
		m match
			case CraftingMessage.startWorking(p, f, r) =>
				jobs += (p -> Job(p, f, r, 0))
			case CraftingMessage.stopWorking(p) =>
				jobs -= p
			case CraftingMessage.tick =>
				jobs = jobs.map { (p, j) => (p, j.copy(currentWork = j.currentWork + 1)) }
				jobs = jobs.filterNot { (p, j) =>
					val cond = j.currentWork >= j.recipe.work
					if cond then
						given ec: ExecutionContext = LocationExecutionContext(j.factory.getLocation())
						Future { completeJob(p, j) }
					cond
				}
		
