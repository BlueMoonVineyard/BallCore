package BallCore.Commands

import BallCore.Chat.{ChatActor, ChatMessage}
import org.bukkit.entity.Player
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.arguments.PlayerArgument
import net.kyori.adventure.text.Component
import dev.jorel.commandapi.arguments.AdventureChatArgument

class MessageCommand(using ca: ChatActor):
    val meNode =
        CommandTree("me")
            .`then`(
                AdventureChatArgument("message")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage.sendMe(
                                sender,
                                args.getUnchecked[Component]("message"),
                            )
                        )
                    }: PlayerCommandExecutor)
            )

    val node =
        CommandTree("msg")
            .withAliases("w", "whisper", "message")
            .`then`(
                PlayerArgument("player")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage.chattingWithPlayer(
                                sender,
                                args.getUnchecked[Player]("player"),
                            )
                        )
                    }: PlayerCommandExecutor)
                    .`then`(
                        AdventureChatArgument("message")
                            .executesPlayer({ (sender, args) =>
                                ca.send(
                                    ChatMessage.sendToPlayer(
                                        sender,
                                        args.getUnchecked[Component]("message"),
                                        args.getUnchecked[Player]("player"),
                                    )
                                )
                            }: PlayerCommandExecutor)
                    )
            )

    val replyNode =
        CommandTree("reply")
            .withAliases("r")
            .`then`(
                AdventureChatArgument("message")
                    .executesPlayer({ (sender, args) =>
                        ca.send(
                            ChatMessage.replyToPlayer(
                                sender,
                                args.getUnchecked[Component]("message"),
                            )
                        )
                    }: PlayerCommandExecutor)
            )
