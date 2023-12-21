package BallCore.Gear

import BallCore.CustomItems.ItemRegistry
import BallCore.CustomItems.CustomItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.inventory.RecipeChoice.ExactChoice
import BallCore.TextComponents._
import org.bukkit.enchantments.Enchantment
import scala.util.chaining._
import BallCore.CustomItems.ItemGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemFlag
import BallCore.Alloys.Tier2
import BallCore.CustomItems.CustomItem
import BallCore.CustomItems.Listeners
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import scala.collection.concurrent.TrieMap
import java.util.UUID
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.block.Block
import org.bukkit.plugin.Plugin
import org.bukkit.Tag

class WideBlockBreakEvent(player: Player, block: Block)
    extends BlockBreakEvent(block, player)

private val group =
    ItemGroup(
        NamespacedKey("ballcore", "adamantite_gear"),
        ItemStack(Material.STICK),
    )

class WideItem(is: CustomItemStack)(using p: Plugin)
    extends CustomItem,
      Listeners.ItemBrokeBlock,
      Listeners.ItemLeftUsedOnBlock:
    override def group: ItemGroup = group
    override def template: CustomItemStack = is

    private val items = TrieMap[UUID, BlockFace]()

    override def onItemBrokeBlock(event: BlockBreakEvent): Unit =
        if event.isInstanceOf[WideBlockBreakEvent] then return

        val originBlock = event.getBlock
        val face = items(event.getPlayer.getUniqueId)
        val item = event.getPlayer.getInventory.getItemInMainHand

        val tag =
            if Tag.ITEMS_AXES.isTagged(item.getType) then Tag.MINEABLE_AXE
            else if Tag.ITEMS_PICKAXES.isTagged(item.getType) then
                Tag.MINEABLE_PICKAXE
            else Tag.MINEABLE_SHOVEL

        val mainHardness = originBlock.getType().getHardness()

        val faces =
            face match
                case BlockFace.UP | BlockFace.DOWN =>
                    for
                        we <- List(
                            BlockFace.WEST,
                            BlockFace.SELF,
                            BlockFace.EAST,
                        )
                        ns <- List(
                            BlockFace.NORTH,
                            BlockFace.SELF,
                            BlockFace.SOUTH,
                        )
                    yield (we, ns)
                case BlockFace.SOUTH | BlockFace.NORTH =>
                    for
                        ud <- List(BlockFace.UP, BlockFace.SELF, BlockFace.DOWN)
                        we <- List(
                            BlockFace.WEST,
                            BlockFace.SELF,
                            BlockFace.EAST,
                        )
                    yield (ud, we)
                case BlockFace.EAST | BlockFace.WEST =>
                    for
                        ud <- List(BlockFace.UP, BlockFace.SELF, BlockFace.DOWN)
                        ns <- List(
                            BlockFace.NORTH,
                            BlockFace.SELF,
                            BlockFace.SOUTH,
                        )
                    yield (ud, ns)
                case _ =>
                    return

        faces.foreach { (one, two) =>
            val target = originBlock.getRelative(one).getRelative(two)
            if !target.isEmpty() &&
                target.getType.getHardness <= mainHardness &&
                tag.isTagged(target.getType)
            then
                val ev = WideBlockBreakEvent(event.getPlayer, target)
                p.getServer().getPluginManager().callEvent(ev)
                if !ev.isCancelled() then
                    val _ = target.breakNaturally()
        }

    override def onItemLeftUsedOnBlock(event: PlayerInteractEvent): Unit =
        items(event.getPlayer.getUniqueId) = event.getBlockFace
        event.setCancelled(false)

object AdamantiteGear:
    private val ore =
        Tier2.adamantite.stack
    private val enchants = List(
        (Enchantment.DURABILITY, 1),
        (Enchantment.DIG_SPEED, 1),
    )
    private def hide(s: ItemStack): Unit =
        val meta = s.getItemMeta
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        val _ = s.setItemMeta(meta)
    private def register[E <: CustomModelDatas](
        is: CustomItemStack,
        cmd: E,
        enchants: Seq[(Enchantment, Int)],
    )(using
        registry: ItemRegistry,
        p: Plugin,
    ): Unit =
        hide(is)
        enchants.foreach {
            // noinspection ConvertibleToMethodValue
            is.addUnsafeEnchantment(_, _)
        }
        is.setItemMeta(is.getItemMeta.tap(_.setCustomModelData(cmd.num)))
        val it = WideItem(is)
        registry.register(it)

    private def pickaxe()(using ir: ItemRegistry, p: Plugin): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"adamantite_corebreaker"),
            ToolSet.Iron.pick,
            txt"Adamantite Corebreaker",
        )
        register(is, IronToolSetCustomModelDatas.adamantite, enchants)
        val recipe =
            ShapedRecipe(
                NamespacedKey("bc", s"adamantite_corebreaker"),
                is,
            )
        recipe.shape(
            "III",
            " S ",
            " S ",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        ir.addRecipe(recipe)
    private def axe()(using ir: ItemRegistry, p: Plugin): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"adamantite_woodeater"),
            ToolSet.Iron.axe,
            txt"Adamantite Woodeater",
        )
        register(is, IronToolSetCustomModelDatas.adamantite, enchants)
        val recipe =
            ShapedRecipe(NamespacedKey("ballcore", s"adamantite_woodeater"), is)
        recipe.shape(
            "II",
            "IS",
            " S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        ir.addRecipe(recipe)
    private def shovel()(using ir: ItemRegistry, p: Plugin): Unit =
        val is = CustomItemStack.make(
            NamespacedKey("ballcore", s"adamantite_earthmover"),
            ToolSet.Iron.shovel,
            txt"Adamantite Earthmover",
        )
        register(is, IronToolSetCustomModelDatas.adamantite, enchants)
        val recipe =
            ShapedRecipe(
                NamespacedKey("ballcore", s"adamantite_earthmover"),
                is,
            )
        recipe.shape(
            "I",
            "S",
            "S",
        )
        recipe.setIngredient('I', ExactChoice(ore))
        recipe.setIngredient('S', Material.STICK)
        ir.addRecipe(recipe)
    def register()(using ir: ItemRegistry, p: Plugin): Unit =
        pickaxe()
        axe()
        shovel()
