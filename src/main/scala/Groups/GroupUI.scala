// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.UI.Elements._
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import org.bukkit.Material
import BallCore.UI.PaneAccumulator
import org.bukkit.Bukkit
import BallCore.UI.UIProgram
import BallCore.UI.UIServices
import scala.concurrent.Future
import net.kyori.adventure.text.format.NamedTextColor
import com.github.stefvanschie.inventoryframework.pane.Pane
import net.kyori.adventure.text.format.TextDecoration
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import BallCore.Beacons.CivBeaconManager

class GroupManagementProgram(using gm: GroupManager, cbm: CivBeaconManager) extends UIProgram:
    case class Flags(groupID: GroupID, userID: UserID)
    case class Model(group: GroupState, userID: UserID, viewing: ViewingWhat, canBindToHeart: Boolean)
    enum ViewingWhat:
        case Players
        case Roles
    enum Message:
        case ViewMembers
        case ViewRoles
        case InviteMember
        case BindToHeart
        case ClickRole(role: RoleID)

    override def init(flags: Flags): Model =
        val group = gm.getGroup(flags.groupID).toOption.get
        val canBindToHeart = cbm.getBeaconFor(flags.userID).map(cbm.beaconSize).map(_ == 1).getOrElse(false)
        Model(group, flags.userID, ViewingWhat.Players, canBindToHeart)
    override def view(model: Model): Callback ?=> Gui =
        val group = model.group
        Root(txt"Viewing ${group.metadata.name}", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(Material.PLAYER_HEAD, txt"Members".style(NamedTextColor.GREEN), Message.ViewMembers, highlighted = model.viewing == ViewingWhat.Players)()
                if group.check(Permissions.ManageRoles, model.userID, nullUUID) then
                    Button(Material.WRITABLE_BOOK, txt"Manage Roles".style(NamedTextColor.GREEN), Message.ViewRoles, highlighted = model.viewing == ViewingWhat.Roles)()
                if group.check(Permissions.InviteUser, model.userID, nullUUID) then
                    Button(Material.COMPASS, txt"Invite A Member".style(NamedTextColor.GREEN), Message.InviteMember)()
                if model.canBindToHeart then
                    Button(Material.WHITE_CONCRETE, txt"Bind to Beacon".style(NamedTextColor.GREEN), Message.BindToHeart)()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item(Material.BLACK_STAINED_GLASS_PANE, displayName = Some(txt""))()
            }
            model.viewing match
                case ViewingWhat.Players => Players(model)
                case ViewingWhat.Roles => Roles(model)
        }
    def Players(model: Model)(using PaneAccumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 6) {
            group.users.keys.toList.map(x => Bukkit.getOfflinePlayer(x)).sortBy(_.getName()).foreach { x =>
                Item(Material.PLAYER_HEAD, displayName = Some(txt"${x.getName()}")) {
                    Skull(x)
                    if group.owners.contains(x.getUniqueId()) then
                        Lore(txt"Owner".style(NamedTextColor.GREEN, TextDecoration.BOLD))
                    val roles = group.roles
                        .filter(r => group.users(x.getUniqueId()).contains(r.id))
                        .filterNot(_.id == everyoneUUID)
                    if roles.length > 0 then
                        Lore(txt"")
                        Lore(txt"Roles".style(NamedTextColor.WHITE, TextDecoration.UNDERLINED))
                        roles.foreach { x =>
                            Lore(txt"- ${x.name}".style(NamedTextColor.WHITE))
                        }
                }
            }
        }
    def Roles(model: Model)(using PaneAccumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 6) {
            group.roles.foreach { x =>
                Button(Material.LEATHER_CHESTPLATE, txt"${x.name}".style(NamedTextColor.WHITE), Message.ClickRole(x.id)) {
                    if x.permissions.size > 0 then
                        Lore(txt"")
                        Lore(txt"Permissions".style(NamedTextColor.WHITE, TextDecoration.UNDERLINED))
                        x.permissions.toList.sortBy(_._1.ordinal).foreach { x =>
                            val (p, r) = x
                            if r == RuleMode.Allow then
                                Lore(txt"- ✔ ${p.displayName()}".style(NamedTextColor.GREEN))
                            else
                                Lore(txt"- ✖ ${p.displayName()}".style(NamedTextColor.RED))
                        }
                }
            }
        }
    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case Message.ViewMembers =>
                model.copy(viewing = ViewingWhat.Players)
            case Message.ViewRoles =>
                model.copy(viewing = ViewingWhat.Roles)
            case Message.BindToHeart =>
                cbm.getBeaconFor(model.userID).map { beacon =>
                    cbm.setGroup(beacon, model.group.metadata.id).isRight
                } match
                    case None =>
                        services.notify(s"You don't have a Civilization Beacon to bind ${model.group.metadata.name} to!")
                    case Some(ok) =>
                        if ok then
                            services.notify(s"Bound ${model.group.metadata.name} to your Civilization Beacon!")
                        else
                            services.notify(s"Failed to bind ${model.group.metadata.name} to your Civilization Beacon!")
                    model.copy(canBindToHeart = cbm.getBeaconFor(model.userID).map(cbm.beaconSize).map(_ == 1).getOrElse(false))
            case Message.InviteMember =>
                services.prompt("Who do you want to invite?")
                    .map { username =>
                        Option(Bukkit.getOfflinePlayerIfCached(username)) match
                            case None =>
                                services.notify("I couldn't find a player with that username")
                            case Some(plr) if model.group.users.contains(plr.getUniqueId()) =>
                                services.notify(s"${plr.getName()} is already in ${model.group.metadata.name}!")
                            case Some(plr) =>
                                gm.invites.inviteToGroup(model.userID, plr.getUniqueId(), model.group.metadata.id)
                                services.notify(s"Invited ${plr.getName()} to ${model.group.metadata.name}!")
                        model
                    }
            case Message.ClickRole(role) =>
                val p = RoleManagementProgram()
                services.transferTo(p, p.Flags(model.group.metadata.id, role, model.userID))
                model

class RoleManagementProgram(using gm: GroupManager, cbm: CivBeaconManager) extends UIProgram:
    case class Flags(groupID: GroupID, roleID: RoleID, userID: UserID)
    case class Model(group: GroupState, groupID: GroupID, userID: UserID, role: RoleState)
    enum Message:
        case DeleteRole
        case TogglePermission(val perm: Permissions)
        case GoBack

    override def init(flags: Flags): Model =
        val group = gm.getGroup(flags.groupID).toOption.get
        val role = group.roles.find(_.id == flags.roleID).get
        Model(group, flags.groupID, flags.userID, role)
    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case Message.DeleteRole =>
                gm.deleteRole(model.userID, model.role.id, model.groupID) match
                    case Left(err) =>
                        services.notify(s"You cannot delete that role because ${err.explain()}")
                        model
                    case Right(_) =>
                        val p = GroupManagementProgram()
                        services.transferTo(p, p.Flags(model.groupID, model.userID))
                        model
            case Message.TogglePermission(perm) =>
                val newPerm = model.role.permissions.get(perm) match
                    case None => Some(RuleMode.Allow)
                    case Some(RuleMode.Allow) => Some(RuleMode.Deny)
                    case Some(RuleMode.Deny) => None
                val newPermissions =
                    newPerm match
                        case None => model.role.permissions.removed(perm)
                        case Some(mode) => model.role.permissions.updated(perm, mode)
                gm.setRolePermissions(model.userID, model.groupID, model.role.id, newPermissions) match
                    case Left(err) =>
                        services.notify(s"You cannot change that permission because ${err.explain()}")
                        model
                    case Right(_) =>
                        model.copy(role = model.role.copy(permissions = newPermissions))
            case Message.GoBack =>
                val p = GroupManagementProgram()
                services.transferTo(p, p.Flags(model.groupID, model.userID))
                model
    override def view(model: Model): Callback ?=> Gui =
        val role = model.role
        val group = model.group
        Root(txt"Viewing Role ${role.name} in ${group.metadata.name}", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(Material.OAK_DOOR, txt"Go Back".style(NamedTextColor.WHITE), Message.GoBack)()
                if role.id != everyoneUUID then
                    Button(Material.LAVA_BUCKET, txt"Delete Role".style(NamedTextColor.GREEN), Message.DeleteRole)()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item(Material.BLACK_STAINED_GLASS_PANE, displayName = Some(txt""))()
            }
            OutlinePane(2, 0, 7, 6) {
                Permissions.values.foreach { x =>
                    val name =
                        role.permissions.get(x) match
                            case None => s"§7* ${x.displayName()}"
                            case Some(RuleMode.Allow) => s"§a✔ ${x.displayName()}"
                            case Some(RuleMode.Deny) => s"§c✖ ${x.displayName()}"

                    Button(x.displayItem(), txt"${name}", Message.TogglePermission(x)) {
                        Lore(txt"${x.displayExplanation()}".color(NamedTextColor.WHITE))
                        Lore(txt"")

                        role.permissions.get(x) match
                            case None => Lore(txt"This role does not affect this permission".color(NamedTextColor.GRAY))
                            case Some(RuleMode.Allow) => Lore(txt"This role allows this permission unless overridden by a higher role".color(NamedTextColor.GRAY))
                            case Some(RuleMode.Deny) => Lore(txt"This role denies this permission unless overridden by a higher role".color(NamedTextColor.GRAY))

                        Lore(txt"")
                        role.permissions.get(x) match
                            case None => Lore(txt"Click to toggle ${txt"ignore".color(NamedTextColor.WHITE)}/allow/deny".color(NamedTextColor.GRAY))
                            case Some(RuleMode.Allow) => Lore(txt"Click to toggle ignore/${txt"allow".color(NamedTextColor.WHITE)}/deny".color(NamedTextColor.GRAY))
                            case Some(RuleMode.Deny) => Lore(txt"Click to toggle ignore/allow/${txt"deny".color(NamedTextColor.WHITE)}".color(NamedTextColor.GRAY))
                    }
                }
            }
        }

class InvitesListProgram(using gm: GroupManager) extends UIProgram:
    case class Flags(userID: UserID)
    enum Mode:
        case List
        case ViewingInvite(user: UserID, group: GroupState)
    case class Model(userID: UserID, invites: List[(UserID, GroupState)], mode: Mode)
    enum Message:
        case ClickInvite(inviter: UserID, group: GroupID)
        case AcceptInvite(group: GroupID)
        case DenyInvite(group: GroupID)

    def computeInvites(userID: UserID): List[(UserID, GroupState)] =
        gm.invites.getInvitesFor(userID).map { (uid, gid) =>
            gm.getGroup(gid).toOption.map((uid, _))
        }.flatten
    override def init(flags: Flags): Model =
        Model(flags.userID, computeInvites(flags.userID), Mode.List)
    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case Message.ClickInvite(user, group) =>
                model.copy(mode = Mode.ViewingInvite(user, model.invites.find(_._1 == user).get._2))
            case Message.AcceptInvite(group) =>
                gm.invites.acceptInvite(model.userID, group)
                model.copy(mode = Mode.List, invites = computeInvites(model.userID))
            case Message.DenyInvite(group) =>
                gm.invites.deleteInvite(model.userID, group)
                model.copy(mode = Mode.List, invites = computeInvites(model.userID))
    override def view(model: Model): Callback ?=> Gui =
        model.mode match
            case Mode.List => list(model)
            case Mode.ViewingInvite(user, group) => viewing(model, user, group)
    def list(model: Model): Callback ?=> Gui =
        val rows = (model.invites.length / 9).max(1)
        Root(txt"Invites", rows) {
            OutlinePane(0, 0, 1, rows) {
                model.invites.foreach { invite =>
                    val player = Bukkit.getOfflinePlayer(invite._1)
                    Button(Material.PLAYER_HEAD, txt"${player.getName()}".color(NamedTextColor.WHITE), Message.ClickInvite(invite._1, invite._2.metadata.id)) {
                        Skull(player)
                        Lore(txt"Invited you to ${txt"${invite._2.metadata.name}".color(NamedTextColor.GREEN)}".color(NamedTextColor.WHITE))
                    }
                }
            }
        }
    def viewing(model: Model, inviter: UserID, group: GroupState): Callback ?=> Gui =
        Root(txt"Accept / Reject Invite?", 1) {
            OutlinePane(0, 0, 1, 1) {
                Button(Material.RED_DYE, txt"Reject Invite".color(NamedTextColor.WHITE), Message.DenyInvite(group.metadata.id))()
            }
            OutlinePane(4, 0, 1, 1) {
                val player = Bukkit.getOfflinePlayer(inviter)
                Item(Material.PLAYER_HEAD, displayName = Some(txt"§${player.getName()}".style(NamedTextColor.WHITE))) {
                    Skull(player)
                    Lore(txt"Invited you to ${txt"${group.metadata.name}".style(NamedTextColor.GREEN)}".style(NamedTextColor.WHITE))
                }
            }
            OutlinePane(8, 0, 1, 1) {
                Button(Material.LIME_DYE, txt"Accept Invite".color(NamedTextColor.WHITE), Message.AcceptInvite(group.metadata.id))()
            }
        }

class GroupListProgram(using gm: GroupManager, cbm: CivBeaconManager) extends UIProgram:
    case class Flags(userID: UserID)
    case class Model(userID: UserID, groups: List[GroupStates])
    enum Message:
        case ClickGroup(groupID: GroupID)
        case CreateGroup
        case Invites

    override def init(flags: Flags): Model =
        Model(flags.userID, gm.userGroups(flags.userID).toOption.get)

    override def update(msg: Message, model: Model)(using services: UIServices): Future[Model] =
        msg match
            case Message.ClickGroup(groupID) =>
                val p = GroupManagementProgram()
                services.transferTo(p, p.Flags(groupID, model.userID))
                model
            case Message.Invites =>
                val p = InvitesListProgram()
                services.transferTo(p, p.Flags(model.userID))
                model
            case Message.CreateGroup =>
                val answer = services.prompt("What do you want to call the group?")
                answer.map { result =>
                    gm.createGroup(model.userID, result)
                    model.copy(groups = gm.userGroups(model.userID).toOption.get)
                }

    override def view(model: Model): Callback ?=> Gui =
        val groups = model.groups
        Root(txt"Groups", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(Material.NAME_TAG, txt"Create Group".color(NamedTextColor.GREEN), Message.CreateGroup)()
                Button(Material.PAPER, txt"View Invites".color(NamedTextColor.GREEN), Message.Invites)()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item(Material.BLACK_STAINED_GLASS_PANE, displayName = Some(txt" "))()
            }
            OutlinePane(2, 0, 7, 6) {
                groups.foreach { x =>
                    Button(Material.LEATHER_CHESTPLATE, txt"${x.name}".color(NamedTextColor.GREEN), Message.ClickGroup(x.id))()
                }
            }
        }
