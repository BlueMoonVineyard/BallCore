// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.UI

import java.util.UUID
import scala.concurrent.Promise
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.conversations.Conversation
import org.bukkit.entity.Player
import org.bukkit.conversations.ConversationFactory
import org.bukkit.conversations.StringPrompt
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext
import BallCore.Folia.EntityExecutionContext

private case class PromptState(
    promise: Promise[String],
    conversation: Conversation,
)

class Prompts(using plugin: Plugin):
    private val prompts = scala.collection.concurrent.TrieMap[UUID, PromptState]()

    def prompt(player: Player, prompt: String): Future[String] =
        if prompts.contains(player.getUniqueId()) then
            val state = prompts(player.getUniqueId())
            state.promise.failure(Exception("cancelled by another prompt"))
            state.conversation.abandon()
            prompts.remove(player.getUniqueId())

        val promise = Promise[String]()
        given ctx: ExecutionContext = EntityExecutionContext(player)

        val bukkitPrompt = new StringPrompt:
            override def getPromptText(context: ConversationContext): String =
                prompt
            override def acceptInput(context: ConversationContext, input: String): Prompt =
                prompts(player.getUniqueId()).promise.complete(Try(input))
                prompts.remove(player.getUniqueId())
                Prompt.END_OF_CONVERSATION

        val conversation = ConversationFactory(plugin)
            .withModality(false)
            .withLocalEcho(false)
            .withFirstPrompt(bukkitPrompt)
            .buildConversation(player)
            
        val state = PromptState(promise, conversation)
        Future { player.closeInventory() }
        conversation.begin()
        prompts(player.getUniqueId()) = state
        promise.future

