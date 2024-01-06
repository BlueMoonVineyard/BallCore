package BallCore.Commands

import BallCore.Chat.{ChatActor, ChatMessage}
import BallCore.Groups.GroupManager
import BallCore.Storage.SQLManager
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.arguments.TextArgument
import org.bukkit.command.CommandSender
import net.kyori.adventure.text.Component
import dev.jorel.commandapi.arguments.AdventureChatArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions

class ChatCommands(using ca: ChatActor, gm: GroupManager, sql: SQLManager):
    val group =
        CommandTree("group")
            .withAliases("g")
            .`then`(
                TextArgument("group-to-chat-in")
                    .replaceSuggestions(suggestGroups(true))
                    .executesPlayer(withGroupArgument("group-to-chat-in") {
                        (sender, args, group) =>
                            ca.send(
                                ChatMessage
                                    .chattingInGroup(sender, group.id)
                            )
                    })
                    .`then`(
                        AdventureChatArgument("message")
                            .executesPlayer(
                                withGroupArgument("group-to-chat-in") {
                                    (sender, args, group) =>
                                        ca.send(
                                            ChatMessage
                                                .sendToGroup(
                                                    sender,
                                                    args.getUnchecked[
                                                        Component
                                                    ]("message"),
                                                    group.id,
                                                )
                                        )
                                }
                            )
                    )
            )

    val global =
        CommandTree("global")
            .executesPlayer({ (sender, args) =>
                ca.send(ChatMessage.chattingInGlobal(sender))
            }: PlayerCommandExecutor)
            .`then`(
                AdventureChatArgument("message")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage
                                .sendToGlobal(
                                    sender,
                                    args.getUnchecked[Component]("message"),
                                )
                        )
                    }: PlayerCommandExecutor)
            )

    val local =
        CommandTree("local")
            .withAliases("l")
            .executesPlayer({ (sender, args) =>
                ca.send(ChatMessage.chattingInLocal(sender))
            }: PlayerCommandExecutor)
            .`then`(
                AdventureChatArgument("message")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage
                                .sendToLocal(
                                    sender,
                                    args.getUnchecked[Component]("message"),
                                )
                        )
                    }: PlayerCommandExecutor)
            )
