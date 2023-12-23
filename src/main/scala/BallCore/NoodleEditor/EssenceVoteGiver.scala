package BallCore.NoodleEditor

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import com.vexsoftware.votifier.model.VotifierEvent
import org.bukkit.Bukkit
import BallCore.TextComponents._

class EssenceVoteGiver extends Listener:
    @EventHandler
    def onVote(event: VotifierEvent): Unit =
        val username = event.getVote.getUsername
        val player = Bukkit.getPlayer(username)
        if player == null then return ()
        val toGive = Essence.template.clone()
        toGive.setAmount(1)
        player.sendServerMessage(txt"You voted on ${event.getVote.getServiceName}!")
        player.getInventory.addItem(toGive).forEach { (_, item) =>
            val _ =
                player.getWorld.dropItemNaturally(player.getLocation, item)
        }
