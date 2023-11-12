// SPDX-FileCopyrightText: 2023 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.MapCloning

import BallCore.MapCloning.MapCloningListener.persistenceKeyMapCopyStatus
import BallCore.UI.ChatElements.*
import com.destroystokyo.paper.event.inventory.PrepareResultEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration.State
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.event.inventory.{CraftItemEvent, PrepareItemCraftEvent}
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.inventory.CartographyInventory
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.{Material, NamespacedKey}

import java.util as ju
import scala.util.chaining.*

object MapCopyStatus:
    def withNameOpt(s: String): Option[MapCopyStatus] =
        values.find(_.toString == s)

enum MapCopyStatus:
    case original
    case copyOfOriginal
    case copyOfCopy

object MapCloningListener:
    val persistenceKeyMapCopyStatus: NamespacedKey =
        NamespacedKey("ballcore", "map_copy_status")

    def register()(using p: Plugin): Unit =
        p.getServer.getPluginManager.registerEvents(MapCloningListener(), p)

// https://github.com/PaperMC/Paper/issues/8749
// we have no real way of knowing when someone uses a cartography table so
class MapCloningListener extends Listener:
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def craftItemEvent(cie: CraftItemEvent): Unit =
        val matrix = cie.getInventory.getMatrix
        if !(matrix.exists(x =>
                x != null && x.getType == Material.FILLED_MAP
            ) && matrix.exists(x => x != null && x.getType == Material.MAP))
        then return

        val plusOne = matrix.map(item =>
            if item != null && item.getType == Material.FILLED_MAP then
                val is = item.clone()
                is.setAmount(is.getAmount + 1)
                is
            else item
        )

        cie.getInventory.setMatrix(plusOne)

    private def greyIt(component: Component): Component =
        component.style(x =>
            val _ = x
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, State.FALSE)
        )

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def prepareCraftEvent(pice: PrepareItemCraftEvent): Unit =

        if pice.getInventory.getResult == null || pice.getInventory.getResult.getType != Material.FILLED_MAP
        then
            return

            if !pice.getInventory.getContents
                    .exists(x => x != null && x.getType == Material.MAP)
            then return

        val inputMap = pice.getInventory.getMatrix
            .find(x => x != null && x.getType == Material.FILLED_MAP)
            .get
        val inputMapGeneration = inputMap.getItemMeta.getPersistentDataContainer
            .getOrDefault(
                persistenceKeyMapCopyStatus,
                PersistentDataType.STRING,
                "original",
            )
            .pipe(x =>
                MapCopyStatus.withNameOpt(x).getOrElse(MapCopyStatus.original)
            )
        val resultMap = pice.getInventory.getResult
        val resultMapMeta = resultMap.getItemMeta
        inputMapGeneration match
            case MapCopyStatus.original | MapCopyStatus.copyOfOriginal =>
                if inputMapGeneration == MapCopyStatus.original then
                    resultMapMeta.getPersistentDataContainer
                        .set(
                            persistenceKeyMapCopyStatus,
                            PersistentDataType.STRING,
                            MapCopyStatus.copyOfOriginal.toString,
                        )
                    resultMapMeta.lore(
                        ju.List.of(
                            Component
                                .translatable("book.generation.1")
                                .pipe(greyIt)
                        )
                    )
                else
                    resultMapMeta.getPersistentDataContainer
                        .set(
                            persistenceKeyMapCopyStatus,
                            PersistentDataType.STRING,
                            MapCopyStatus.copyOfCopy.toString,
                        )
                    resultMapMeta.lore(
                        ju.List.of(
                            Component
                                .translatable("book.generation.2")
                                .pipe(greyIt)
                        )
                    )
                val _ = resultMap.setItemMeta(resultMapMeta)
                resultMap.setAmount(1)
                pice.getInventory.setResult(resultMap)
            case MapCopyStatus.copyOfCopy =>
                pice.getInventory.setResult(null)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    def prepareResultEvent(per: PrepareResultEvent): Unit =
        if per.getResult == null || per.getResult.getType != Material.FILLED_MAP
        then
            return

            if !per.getInventory.getContents
                    .exists(x => x != null && x.getType == Material.MAP)
            then
                return

                if per.getInventory.isInstanceOf[CartographyInventory] then
                    per.getViewers
                        .forEach(
                            _.sendServerMessage(
                                txt"Duplicating maps with a cartography table is disabled due to limitations in PaperMC, sorry :( Use a crafting table instead"
                            )
                        )
                    // i would close the inventory here but that causes item loss
                    per.setResult(null)
                    return
