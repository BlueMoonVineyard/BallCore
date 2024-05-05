package CivCubed.Commands

import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import CivCubed.Beacons.HeartBlock

class GetHeart:
    val node =
        CommandTree("get-heart")
            .executesPlayer({ (sender, args) =>
                val _ =
                    sender.getInventory().addItem(HeartBlock.itemStack.clone())
            }: PlayerCommandExecutor)
