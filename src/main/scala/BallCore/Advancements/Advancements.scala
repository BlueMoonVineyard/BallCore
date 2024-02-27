package BallCore.Advancements

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.Bukkit

sealed trait BallAdvancement[T <: String with Singleton]:
    type Criteria = T

    def key: NamespacedKey
    def grant(player: Player, which: T): Boolean =
        val advancement = Bukkit.getAdvancement(key)
        val progress = player.getAdvancementProgress(advancement)
        progress.awardCriteria(which)

object BindCivHeart extends BallAdvancement["bind"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/bind_civ_heart")

object CreateShopChest extends BallAdvancement["insert_order"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/create_shop_chest")

object GetNonToolBaseOres extends BallAdvancement["black_ore" | "blue_ore"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/get_non_tool_base_ores")

object GetSeed extends BallAdvancement["seed_dropped"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/get_seed")

object Groups extends BallAdvancement["joined_group"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/groups")

object PlaceCivHeart extends BallAdvancement["placed_heart"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/place_civ_heart")

object UseShopChest extends BallAdvancement["did_exchange"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/use_shop_chest")

object UseStation extends BallAdvancement["worked_recipe"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/use_station")

object ViewOres extends BallAdvancement["book_used"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/view_ores")

object ViewPlants extends BallAdvancement["plants_used"]:
    def key: NamespacedKey =
        NamespacedKey("civcubed", "civcubed/view_ores")
