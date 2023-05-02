package BallCore.Chat

import BallCore.DataStructures.Actor
import org.bukkit.plugin.Plugin
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import BallCore.Groups.GroupID
import org.bukkit.Bukkit
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import BallCore.Groups.GroupManager
import scala.collection.JavaConverters._
import net.kyori.adventure.audience.Audience

enum ChatMessage:
	case send(p: Player, m: Component)

	case joined(p: Player)
	case left(p: Player)

	case chattingInGroup(p: Player, group: GroupID)
	case chattingInGlobal(p: Player)
	case chattingInLocal(p: Player)

enum PlayerState:
	case globalChat
	case localChat
	case groupChat(group: GroupID)

class ChatActor(using p: Plugin, gm: GroupManager) extends Actor[ChatMessage]:
	import BallCore.UI.ChatElements._
	var states = Map[Player, PlayerState]().withDefaultValue(PlayerState.globalChat)
	val globalGrey = TextColor.fromHexString("#686b6f")
	val groupGrey = TextColor.fromHexString("#9b9ea2")
	val localGrey = TextColor.fromHexString("#b6b9bd")

	def handle(m: ChatMessage): Unit =
		m match
			case ChatMessage.send(p, m) =>
				states(p) match
					case PlayerState.globalChat =>
						Bukkit.getServer().sendMessage(txt"[!] ${p.getDisplayName()}: ${m.color(NamedTextColor.WHITE)}".color(globalGrey))
					case PlayerState.localChat =>
						val nearby = Bukkit.getOnlinePlayers().asScala.filter { plr =>
							plr.getWorld() == p.getWorld()
						}.filter { plr =>
							plr.getLocation().distanceSquared(p.getLocation()) < 500 * 500
						}.asJava
						Audience.audience(nearby).sendMessage(txt"[Local] ${p.getDisplayName()}: ${m.color(NamedTextColor.WHITE)}".color(localGrey))
					case PlayerState.groupChat(group) =>
						gm.groupAudience(group).map { (name, aud) =>
							aud.sendMessage(txt"[${name}] ${p.getDisplayName()}: ${m.color(NamedTextColor.WHITE)}".color(groupGrey))
						}
			case ChatMessage.joined(p) =>
				Bukkit.getServer().sendMessage(txt"${p.getDisplayName()} has joined the game".color(NamedTextColor.YELLOW))
			case ChatMessage.left(p) =>
				Bukkit.getServer().sendMessage(txt"${p.getDisplayName()} has left the game".color(NamedTextColor.YELLOW))
			case ChatMessage.chattingInGroup(p, group) =>
				states += p -> PlayerState.groupChat(group)
				p.sendMessage(txt"You are now chatting in a group".color(NamedTextColor.GREEN))
			case ChatMessage.chattingInGlobal(p) =>
				states += p -> PlayerState.globalChat
				p.sendMessage(txt"You are now chatting in global chat".color(NamedTextColor.GREEN))
			case ChatMessage.chattingInLocal(p) =>
				states += p -> PlayerState.localChat
				p.sendMessage(txt"You are now chatting in local chat".color(NamedTextColor.GREEN))
