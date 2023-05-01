package BallCore.CraftingStations

import BallCore.UI.UIProgram
import org.bukkit.entity.Player
import BallCore.UI.UIServices
import scala.concurrent.Future
import org.bukkit.block.Block
import BallCore.UI.callback
import BallCore.UI.Elements._
import scala.jdk.CollectionConverters._
import scala.xml.Elem
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ItemStack

enum RecipeSelectorMessage:
	case selectRecipe(index: Int)
	case nextPage
	case prevPage

class RecipeSelectorProgram(recipes: List[Recipe])(using actor: CraftingActor) extends UIProgram:
	import io.circe.generic.auto._

	val paginated = recipes.zipWithIndex.grouped(5 * 9).toList
	val numPages = paginated.size

	case class Flags(player: Player, factory: Block)
	case class Model(player: Player, factory: Block, page: Int)
	type Message = RecipeSelectorMessage

	override def init(flags: Flags): Model =
		Model(flags.player, flags.factory, 0)

	override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
		msg match
			case RecipeSelectorMessage.selectRecipe(index) =>
				actor.send(CraftingMessage.startWorking(model.player, model.factory, recipes(index)))
				services.notify("You've started working on that recipe!")
				model
			case RecipeSelectorMessage.nextPage =>
				model.copy(page = (model.page + 1).min(numPages-1))
			case RecipeSelectorMessage.prevPage =>
				model.copy(page = (model.page - 1).max(0))

	def choiceToString(input: RecipeChoice): String =
		input match
			case m: MaterialChoice =>
				m.getChoices().asScala.map(mat => ItemStack(mat).getI18NDisplayName()).mkString(" or ")
			case _ =>
				s"TODO: ${input}"

	override def view(model: Model): Elem =
		Root(s"Recipes (Page ${model.page+1} of ${numPages})", 6) {
			OutlinePane(0, 0, 9, 5) {
				paginated(model.page).foreach { (recipe, idx) =>
					Button(recipe.outputs(0).getType().toString().toLowerCase(), s"§a${recipe.name}", RecipeSelectorMessage.selectRecipe(idx)) {
						Lore(s"§r§f§nIngredients")
						recipe.inputs.foreach { (input, amount) =>
							Lore(s"§f - §7§l${choiceToString(input)}§f × ${amount}")
						}
						Lore(s"")
						Lore(s"§r§f§nResults")
						recipe.outputs.foreach { output =>
							Lore(s"§f - §7§l${output.getI18NDisplayName()}§f × ${output.getAmount()}")
						}
						Lore(s"")
						Lore(s"§fTakes §a${recipe.work} seconds§f of work")
					}
				}
			}
			OutlinePane(0, 5, 9, 1) {
				Button("red_dye", "§aPrevious Page", RecipeSelectorMessage.prevPage)()
				Button("lime_dye", "§aNext Page", RecipeSelectorMessage.nextPage)()
			}
		}