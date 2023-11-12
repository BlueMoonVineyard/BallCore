// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import BallCore.Folia.{EntityExecutionContext, FireAndForget}
import BallCore.UI.ChatElements.*
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.{EventHandler, EventPriority, Listener}
import org.bukkit.plugin.Plugin

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

private case class PromptState(
                                promise: Promise[String]
                              )

class Prompts(using plugin: Plugin) extends Listener:
  private val prompts = scala.collection.concurrent.TrieMap[UUID, PromptState]()
  plugin.getServer.getPluginManager.registerEvents(this, plugin)

  private def stringify(c: Component) =
    PlainTextComponentSerializer.plainText().serialize(c)

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  def playerChat(event: AsyncChatEvent): Unit =
    prompts.get(event.getPlayer.getUniqueId).foreach { prompt =>
      event.setCancelled(true)
      prompt.promise.complete(Try(stringify(event.message())))
      prompts.remove(event.getPlayer.getUniqueId)
    }

  def prompt(player: Player, prompt: String): Future[String] =
    if prompts.contains(player.getUniqueId) then
      val state = prompts(player.getUniqueId)
      state.promise.failure(Exception("cancelled by another prompt"))
      val _ = prompts.remove(player.getUniqueId)

    val promise = Promise[String]()

    given ctx: ExecutionContext = EntityExecutionContext(player)

    val state = PromptState(promise)
    FireAndForget {
      player.closeInventory()
      player.sendServerMessage(txt(prompt))
    }
    prompts(player.getUniqueId) = state
    promise.future
