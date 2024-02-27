// SPDX-FileCopyrightText: 2022 Janet Blackquill <uhhadd@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package BallCore.Groups

import BallCore.Beacons.CivBeaconManager
import BallCore.Folia.FireAndForget
import BallCore.PolyhedraEditor.PolyhedraEditor
import BallCore.Storage.SQLManager
import BallCore.UI.Elements.*
import BallCore.UI.{PaneAccumulator, UIProgram, UIServices}
import com.destroystokyo.paper.MaterialTags
import com.github.stefvanschie.inventoryframework.gui.`type`.util.Gui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.{Bukkit, Material}

import scala.concurrent.Future
import net.kyori.adventure.text.Component
import BallCore.UI.ItemAccumulator
import BallCore.PrimeTime.PrimeTimeManager
import java.time.OffsetTime
import java.util.TimeZone
import BallCore.Storage.KeyVal
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import scala.util.Try
import BallCore.PrimeTime.PrimeTimeError
import java.time.LocalTime
import org.bukkit.OfflinePlayer
import cats.effect.IO
import BallCore.NoodleEditor.NoodleEditor
import BallCore.NoodleEditor.NoodleKey
import BallCore.Advancements.BindCivHeart

extension (p: BallCore.Groups.Permissions)
    def displayItem(): Material =
        import BallCore.Groups.Permissions.*

        p match
            case ManageRoles => Material.LEATHER_CHESTPLATE
            case ManageUserRoles => Material.IRON_CHESTPLATE
            case InviteUser => Material.PLAYER_HEAD
            case RemoveUser => Material.BARRIER
            case UpdateGroupInformation => Material.NAME_TAG
            case AddReinforcements => Material.STONE
            case RemoveReinforcements => Material.IRON_PICKAXE
            case Build => Material.BRICKS
            case Chests => Material.CHEST
            case Doors => Material.OAK_DOOR
            case Crops => Material.WHEAT
            case Signs => Material.OAK_SIGN
            case Entities => Material.EGG
            case ManageClaims => Material.BEACON
            case ManageSubgroups => Material.RED_BED

class ConfirmationPrompt(
    title: Component,
    yesLabel: Component,
    noLabel: Component,
    centerLabel: ItemAccumulator ?=> Unit,
    onYes: UIServices ?=> Unit,
    onNo: UIServices ?=> Unit,
) extends UIProgram:
    case class Flags()
    case class Model()

    enum Message:
        case Accept
        case Deny

    override def init(flags: Flags): Model =
        Model()

    override def view(model: Model): Callback ?=> Gui =
        Root(title, 1) {
            OutlinePane(0, 0, 1, 1) {
                Button(
                    Material.RED_DYE,
                    noLabel.color(NamedTextColor.WHITE),
                    Message.Deny,
                )()
            }
            OutlinePane(4, 0, 1, 1) {
                centerLabel
            }
            OutlinePane(8, 0, 1, 1) {
                Button(
                    Material.LIME_DYE,
                    yesLabel.color(NamedTextColor.WHITE),
                    Message.Accept,
                )()
            }
        }

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.Accept =>
                onYes(using services)
                model
            case Message.Deny =>
                onNo(using services)
                model

class GroupManagementProgram(using
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    editor: PolyhedraEditor,
    primeTime: PrimeTimeManager,
    kv: KeyVal,
    noodleEditor: NoodleEditor,
) extends UIProgram:
    case class Flags(groupID: GroupID, userID: UserID)

    case class Model(
        group: GroupState,
        userID: UserID,
        viewing: ViewingWhat,
        canBindToHeart: Boolean,
        primeTime: Option[OffsetTime],
        userZone: Option[TimeZone],
    )

    // noinspection ScalaWeakerAccess
    enum ViewingWhat:
        case Players
        case Roles
        case Subgroups

    enum Message:
        case View(what: ViewingWhat)
        case InviteMember
        case EditArterialClaims
        case BindToHeart
        case ClickRole(role: RoleID)
        case CreateSubgroup
        case ClickSubgroup(subgroup: SubgroupID)
        case LeaveGroup
        case DeleteGroup
        case UpdateVulnerabilityWindow
        case KickPlayer(player: OfflinePlayer)
        case CreateRole

    private def randomBedFor(obj: Object): Material =
        val beds = MaterialTags.BEDS.getValues.toArray(Array[Material]())
        val idx = obj.hashCode().abs % beds.length
        beds(idx)

    private def canBindToHeart(self: UserID): Boolean =
        sql
            .useBlocking(sql.withS(cbm.getBeaconFor(self)))
            .map(beaconID =>
                sql.useBlocking(sql.withS(cbm.beaconSize(beaconID)))
            )
            .contains(1)

    override def init(flags: Flags): Model =
        import BallCore.PrimeTime.TimeZoneCodec.{encoder, decoder}

        val (group, vulnWindow, userZone) =
            sql.useBlocking(
                sql.withS(sql.withTX(for {
                    group <- gm.getGroup(flags.groupID).value
                    primeTime <- primeTime.getGroupPrimeTime(flags.groupID)
                    userTimezone <- kv.get[TimeZone](flags.userID, "time-zone")
                } yield (group.toOption.get, primeTime, userTimezone)))
            )
        Model(
            group,
            flags.userID,
            ViewingWhat.Players,
            canBindToHeart(flags.userID),
            vulnWindow,
            userZone,
        )

    override def view(model: Model): Callback ?=> Gui =
        val group = model.group
        Root(
            trans"ui.groups.viewing.title".args(
                group.metadata.name.toComponent
            ),
            6,
        ) {
            VerticalPane(0, 0, 2, 6) {
                Button(
                    Material.PLAYER_HEAD,
                    trans"ui.groups.members".style(NamedTextColor.GREEN),
                    Message.View(ViewingWhat.Players),
                    highlighted = model.viewing == ViewingWhat.Players,
                )()
                if group.check(Permissions.ManageRoles, model.userID, nullUUID)
                then
                    Button(
                        Material.WRITABLE_BOOK,
                        trans"ui.groups.manage-roles".style(
                            NamedTextColor.GREEN
                        ),
                        Message.View(ViewingWhat.Roles),
                        highlighted = model.viewing == ViewingWhat.Roles,
                    )()
                if group.check(
                        Permissions.ManageSubgroups,
                        model.userID,
                        nullUUID,
                    )
                then
                    Button(
                        Material.RED_BED,
                        trans"ui.groups.manage-subgroups".style(
                            NamedTextColor.GREEN
                        ),
                        Message.View(ViewingWhat.Subgroups),
                        highlighted = model.viewing == ViewingWhat.Subgroups,
                    )()
                if !group.owners.contains(model.userID) then
                    Button(
                        Material.MINECART,
                        trans"ui.groups.leave".style(NamedTextColor.GREEN),
                        Message.LeaveGroup,
                    )()
                else if group.owners.length == 1 then
                    Button(
                        Material.LAVA_BUCKET,
                        trans"ui.groups.delete".style(NamedTextColor.GREEN),
                        Message.DeleteGroup,
                    )()
                if model.canBindToHeart then
                    Button(
                        Material.WHITE_CONCRETE,
                        trans"ui.groups.bind".style(NamedTextColor.GREEN),
                        Message.BindToHeart,
                    )()
                if group.check(
                        Permissions.ManageClaims,
                        model.userID,
                        nullUUID,
                    )
                then
                    Button(
                        Material.RAIL,
                        trans"ui.groups.arterial".style(NamedTextColor.GREEN),
                        Message.EditArterialClaims,
                    ) {
                        Lore(
                            trans"ui.groups.arterial.subtitle"
                                .color(NamedTextColor.GRAY)
                        )
                    }
                if group.check(
                        Permissions.UpdateGroupInformation,
                        model.userID,
                        nullUUID,
                    )
                then
                    Button(
                        Material.CLOCK,
                        trans"ui.groups.vulnerability-window".style(
                            NamedTextColor.GREEN
                        ),
                        Message.UpdateVulnerabilityWindow,
                    ) {
                        model.primeTime match
                            case None =>
                                Lore(
                                    trans"ui.groups.vulnerability-window.unset"
                                        .color(NamedTextColor.WHITE)
                                )
                                model.userZone match
                                    case None =>
                                        Lore(
                                            trans"ui.groups.vulnerability-window.timezone-required"
                                                .args(
                                                    trans"sample-commands.timezone"
                                                        .color(Colors.teal)
                                                )
                                                .color(NamedTextColor.WHITE)
                                        )
                                    case Some(value) =>
                                        Lore(
                                            trans"ui.groups.vulnerability-window.set-lore"
                                                .color(NamedTextColor.WHITE)
                                        )
                            case Some(time) =>
                                val formatter =
                                    DateTimeFormatter.ofPattern("HH:mm")
                                model.userZone match
                                    case Some(zone) =>
                                        val start = time.withOffsetSameInstant(
                                            zone.toZoneId()
                                                .getRules()
                                                .getOffset(Instant.now())
                                        )
                                        val end =
                                            start.plus(primeTime.windowSize)
                                        val startS =
                                            formatter.format(start).toComponent
                                        val endS =
                                            formatter.format(end).toComponent
                                        Lore(
                                            trans"ui.groups.vulnerability-window.span"
                                                .args(startS, endS)
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            trans"ui.groups.vulnerability-window.set-lore"
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            trans"ui.groups.vulnerability-window.set.warning-line-one"
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            trans"ui.groups.vulnerability-window.set.warning-line-two"
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            trans"ui.groups.vulnerability-window.set.warning-line-three"
                                                .color(NamedTextColor.WHITE)
                                        )
                                    case None =>
                                        val start = time.withOffsetSameInstant(
                                            ZoneOffset.UTC
                                        )
                                        val end =
                                            start.plus(primeTime.windowSize)
                                        val startS =
                                            formatter.format(start).toComponent
                                        val endS =
                                            formatter.format(end).toComponent
                                        Lore(
                                            trans"ui.groups.vulnerability-window.span"
                                                .args(startS, endS)
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            trans"ui.groups.vulnerability-window.timezone-required"
                                                .args(
                                                    trans"sample-commands.timezone"
                                                        .color(Colors.teal)
                                                )
                                                .color(NamedTextColor.WHITE)
                                        )
                    }
            }
            model.viewing match
                case ViewingWhat.Players => Players(model)
                case ViewingWhat.Roles => Roles(model)
                case ViewingWhat.Subgroups => Subgroups(model)
        }

    private def Subgroups(model: Model)(using PaneAccumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 5) {
            group.subgroups.foreach { subgroup =>
                Button(
                    randomBedFor(subgroup.name),
                    subgroup.name.toComponent.style(NamedTextColor.WHITE),
                    Message.ClickSubgroup(subgroup.id),
                ) {
                    Lore(txt"")
                    Lore(
                        trans"ui.groups.subgroup.edit".color(
                            NamedTextColor.GRAY
                        )
                    )
                }
            }
        }
        OutlinePane(2, 5, 7, 1) {
            Button(
                Material.WRITABLE_BOOK,
                trans"ui.groups.subgroup.create".style(NamedTextColor.GREEN),
                Message.CreateSubgroup,
            )()
        }

    private def Players(model: Model)(using PaneAccumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 5) {
            group.users.keys.toList
                .map(x => Bukkit.getOfflinePlayer(x))
                .sortBy(_.getName())
                .foreach { x =>
                    Button(
                        Material.PLAYER_HEAD,
                        x.getName.toComponent,
                        Message.KickPlayer(x),
                    ) {
                        Skull(x)
                        if group.owners.contains(x.getUniqueId) then
                            Lore(
                                trans"ui.groups.owner-status".style(
                                    NamedTextColor.GREEN,
                                    TextDecoration.BOLD,
                                )
                            )
                        val roles = group.roles
                            .filter(r =>
                                group.users(x.getUniqueId).contains(r.id)
                            )
                            .filterNot(_.id == everyoneUUID)
                        if roles.nonEmpty then
                            Lore(txt"")
                            Lore(
                                trans"ui.groups.roles"
                                    .style(
                                        NamedTextColor.WHITE,
                                        TextDecoration.UNDERLINED,
                                    )
                            )
                            roles.foreach { x =>
                                Lore(
                                    txt"- ${x.name}".style(NamedTextColor.WHITE)
                                )
                            }
                        if group.check(
                                Permissions.RemoveUser,
                                model.userID,
                                nullUUID,
                            )
                        then
                            Lore(
                                trans"ui.groups.remove-user"
                                    .color(NamedTextColor.RED)
                            )
                    }
                }
        }
        OutlinePane(2, 5, 7, 1) {
            if group.check(Permissions.InviteUser, model.userID, nullUUID)
            then
                Button(
                    Material.COMPASS,
                    trans"ui.groups.invite-member".style(NamedTextColor.GREEN),
                    Message.InviteMember,
                )()
        }

    private def Roles(model: Model)(using PaneAccumulator): Unit =
        val group = model.group
        OutlinePane(2, 0, 7, 5) {
            group.roles.foreach { x =>
                Button(
                    Material.LEATHER_CHESTPLATE,
                    txt"${x.name}".style(NamedTextColor.WHITE),
                    Message.ClickRole(x.id),
                ) {
                    if x.permissions.nonEmpty then
                        Lore(txt"")
                        Lore(
                            trans"ui.group.permissions"
                                .style(
                                    NamedTextColor.WHITE,
                                    TextDecoration.UNDERLINED,
                                )
                        )
                        x.permissions.toList.sortBy(_._1.ordinal).foreach { x =>
                            val (p, r) = x
                            if r == RuleMode.Allow then
                                Lore(
                                    txt"- ✔ ${p.displayName()}"
                                        .style(NamedTextColor.GREEN)
                                )
                            else
                                Lore(
                                    txt"- ✖ ${p.displayName()}"
                                        .style(NamedTextColor.RED)
                                )
                        }
                }
            }
        }
        OutlinePane(2, 5, 7, 1) {
            if group.check(Permissions.ManageRoles, model.userID, nullUUID)
            then
                Button(
                    Material.WRITABLE_BOOK,
                    trans"ui.groups.roles.create",
                    Message.CreateRole,
                )()
        }

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.View(what) =>
                model.copy(viewing = what)
            case Message.BindToHeart =>
                sql.useBlocking(sql.withS(cbm.getBeaconFor(model.userID))).map {
                    beacon =>
                        sql.useBlocking(
                            sql.withS(
                                cbm.setGroup(beacon, model.group.metadata.id)
                            )
                        ).isRight
                } match
                    case None =>
                        services.notify(
                            s"You don't have a Civilization Beacon to bind ${model.group.metadata.name} to!"
                        )
                    case Some(ok) =>
                        if ok then
                            BindCivHeart.grant(
                                Bukkit.getPlayer(model.userID),
                                "bind",
                            )
                            services.notify(
                                s"Bound ${model.group.metadata.name} to your Civilization Beacon!"
                            )
                            services.notify(
                                s"You can now right-click it to set up an area of protection!"
                            )
                        else
                            services.notify(
                                s"Failed to bind ${model.group.metadata.name} to your Civilization Beacon!"
                            )
                model.copy(canBindToHeart = canBindToHeart(model.userID))
            case Message.InviteMember =>
                services
                    .prompt("Who do you want to invite?")
                    .map { username =>
                        Option(Bukkit.getOfflinePlayerIfCached(username)) match
                            case None =>
                                services.notify(
                                    "I couldn't find a player with that username"
                                )
                            case Some(plr)
                                if model.group.users.contains(
                                    plr.getUniqueId
                                ) =>
                                services.notify(
                                    s"${plr.getName} is already in ${model.group.metadata.name}!"
                                )
                            case Some(plr) =>
                                sql.useBlocking(
                                    sql.withS(
                                        gm.invites.inviteToGroup(
                                            model.userID,
                                            plr.getUniqueId,
                                            model.group.metadata.id,
                                        )
                                    )
                                )
                                services.notify(
                                    s"Invited ${plr.getName} to ${model.group.metadata.name}!"
                                )
                        model
                    }
            case Message.ClickRole(role) =>
                val p = RoleManagementProgram()
                services.transferTo(
                    p,
                    p.Flags(model.group.metadata.id, role, model.userID, None),
                )
                model
            case Message.ClickSubgroup(subgroup) =>
                val p = SubgroupManagementProgram()
                services.transferTo(
                    p,
                    p.Flags(model.group.metadata.id, subgroup, model.userID),
                )
                model
            case Message.CreateSubgroup =>
                services
                    .prompt("What do you want to call the subgroup?")
                    .map { name =>
                        sql.useBlocking(
                            sql.withS(
                                sql.withTX(
                                    gm.createSubgroup(
                                        model.userID,
                                        model.group.metadata.id,
                                        name,
                                    ).value
                                )
                            )
                        ) match
                            case Left(err) =>
                                services.notify(
                                    s"Subgroup creation failed because ${err.explain()}"
                                )
                            case Right(_) =>
                                services.notify(
                                    s"Subgroup successfully created"
                                )
                        val group = sql
                            .useBlocking(
                                sql.withS(
                                    sql.withTX(
                                        gm.getGroup(model.group.metadata.id)
                                            .value
                                    )
                                )
                            )
                            .toOption
                            .get
                        model.copy(group = group)
                    }
            case Message.DeleteGroup =>
                val p = ConfirmationPrompt(
                    trans"ui.groups.delete.confirmation.title".args(
                        model.group.metadata.name.toComponent
                    ),
                    trans"ui.groups.delete.confirmation.yes".args(
                        model.group.metadata.name.toComponent
                    ),
                    trans"ui.groups.delete.confirmation.no".args(
                        model.group.metadata.name.toComponent
                    ),
                    Item(
                        Material.LEATHER_CHESTPLATE,
                        displayName = Some(
                            model.group.metadata.name.toComponent
                                .color(NamedTextColor.GREEN)
                        ),
                    )(), {
                        val services = summon[UIServices]
                        sql.useBlocking(
                            sql.withS(
                                sql.withTX(
                                    gm.deleteGroup(
                                        model.userID,
                                        model.group.metadata.id,
                                    ).value
                                )
                            )
                        ) match
                            case Left(err) =>
                                services.notify(
                                    s"Could not delete ${model.group.metadata.name} because ${err.explain()}"
                                )
                                services.quit()
                            case Right(_) =>
                                val p = GroupListProgram()
                                services.notify(
                                    s"Deleted ${model.group.metadata.name}"
                                )
                                services.transferTo(p, p.Flags(model.userID))
                    }, {
                        val services = summon[UIServices]
                        services.transferTo(
                            this,
                            this.Flags(model.group.metadata.id, model.userID),
                        )
                    },
                )
                services.transferTo(p, p.Flags())
                model
            case Message.LeaveGroup =>
                val p = ConfirmationPrompt(
                    trans"ui.groups.leave.confirmation.title".args(
                        model.group.metadata.name.toComponent
                    ),
                    trans"ui.groups.leave.confirmation.yes".args(
                        model.group.metadata.name.toComponent
                    ),
                    trans"ui.groups.leave.confirmation.no".args(
                        model.group.metadata.name.toComponent
                    ),
                    Item(
                        Material.LEATHER_CHESTPLATE,
                        displayName = Some(
                            model.group.metadata.name.toComponent
                                .color(NamedTextColor.GREEN)
                        ),
                    )(), {
                        val services = summon[UIServices]
                        sql.useBlocking(
                            sql.withS(
                                sql.withTX(
                                    gm.leaveGroup(
                                        model.userID,
                                        model.group.metadata.id,
                                    )
                                )
                            )
                        ) match
                            case Left(err) =>
                                services.notify(
                                    s"Could not leave ${model.group.metadata.name} because ${err.explain()}"
                                )
                                services.quit()
                            case Right(_) =>
                                val p = GroupListProgram()
                                services.notify(
                                    s"Left ${model.group.metadata.name}"
                                )
                                services.transferTo(p, p.Flags(model.userID))
                    }, {
                        val services = summon[UIServices]
                        services.transferTo(
                            this,
                            this.Flags(model.group.metadata.id, model.userID),
                        )
                    },
                )
                services.transferTo(p, p.Flags())
                model
            case Message.EditArterialClaims =>
                val player = Bukkit.getPlayer(model.userID)
                noodleEditor.edit(
                    player,
                    NoodleKey(model.group.metadata.id, nullUUID),
                    player.getWorld,
                )
                services.quit()
                model
            case Message.KickPlayer(target) =>
                sql.useFuture(for {
                    result <- sql.withS(
                        sql.withTX(
                            gm.kickUserFromGroup(
                                model.userID,
                                target.getUniqueId,
                                model.group.metadata.id,
                            )
                        )
                    )
                } yield result match
                    case Left(err) =>
                        services.notify(
                            s"You couldn't kick ${target.getName()} from the group because ${err.explain()}"
                        )
                        model
                    case Right(_) =>
                        services.notify(
                            s"You successfully kicked ${target.getName()} from the group"
                        )
                        model.copy(group =
                            model.group.copy(users =
                                model.group.users.removed(target.getUniqueId)
                            )
                        )
                )
            case Message.CreateRole =>
                sql.useFuture(sql.withS(sql.withTX(for {
                    name <- IO.fromFuture {
                        IO {
                            services.prompt(
                                "What do you want to call the role?"
                            )
                        }
                    }
                    result <- gm
                        .createRole(model.userID, model.group.metadata.id, name)
                        .value
                    updatedGroup <- gm.getGroup(model.group.metadata.id).value
                } yield result match
                    case Left(err) =>
                        services.notify(
                            s"You couldn't create that role because ${err.explain()}"
                        )
                        model
                    case Right(_) =>
                        model.copy(group = updatedGroup.getOrElse(???))
                )))
            case Message.UpdateVulnerabilityWindow =>
                model.userZone match
                    case None =>
                        services.notify(
                            "You need to set a time with /settings timezone <timezone> in order to update the vulnerability window!"
                        )
                        model
                    case Some(zone) =>
                        val formatter =
                            DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy")
                        val parser =
                            DateTimeFormatter
                                .ofPattern("HH:mm")
                                .withZone(zone.toZoneId())
                        val name = zone
                            .toZoneId()
                            .getDisplayName(TextStyle.FULL, Locale.US)
                        val example =
                            formatter.format(ZonedDateTime.now(zone.toZoneId()))
                        services.notify(
                            s"Your configured timezone is $name. It is currently $example."
                        )
                        services.notify(
                            s"Warning: if you change the vulnerability window, the new one and the old one will coexist for today and tomorrow."
                        )
                        services.notify(
                            s"You will not be able to change it until the end of the old time tomorrow."
                        )
                        services
                            .prompt(
                                s"What time would you like the vulneraiblity window to start? (In 24-hour time, such as 13:00) It will last 6 hours from the time you specify. If you provide an invalid time, it won't be changed."
                            )
                            .map { response =>
                                Try(
                                    LocalTime
                                        .parse(response, parser)
                                        .atOffset(
                                            zone.toZoneId()
                                                .getRules()
                                                .getOffset(Instant.now())
                                        )
                                ).toEither match
                                    case Right(time) =>
                                        sql.useBlocking(
                                            sql.withS(
                                                sql.withTX(
                                                    primeTime.setGroupPrimeTime(
                                                        model.userID,
                                                        model.group.metadata.id,
                                                        time,
                                                    )
                                                )
                                            )
                                        ) match
                                            case Left(err) =>
                                                err match
                                                    case PrimeTimeError
                                                            .groupError(
                                                                error
                                                            ) =>
                                                        services.notify(
                                                            s"Couldn't update the time because ${error.explain()}."
                                                        )
                                                        model
                                                    case PrimeTimeError.waitUntilTomorrowWindowHasPassed =>
                                                        services.notify(
                                                            s"Couldn't update the time because it was changed too recently."
                                                        )
                                                        model
                                            case Right(ok) =>
                                                val updatedTime =
                                                    parser.format(time)
                                                services.notify(
                                                    s"Successfully updated the vulnerability window to ${updatedTime}! The old one will apply for two more days alongside the new one."
                                                )
                                                model.copy(primeTime =
                                                    Some(time)
                                                )
                                    case Left(err) =>
                                        println(err)
                                        services.notify(
                                            s"You provided an invalid time. Cancelling..."
                                        )
                                        model
                            }

class SubgroupManagementProgram(using
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    editor: PolyhedraEditor,
    primeTime: PrimeTimeManager,
    kv: KeyVal,
    noodleEditor: NoodleEditor,
) extends UIProgram:
    case class Flags(groupID: GroupID, subgroupID: SubgroupID, userID: UserID)

    case class Model(group: GroupState, subgroup: SubgroupState, userID: UserID)

    enum Message:
        case DeleteSubgroup
        case ClickRole(role: RoleID)
        case EditClaims
        case EditArterialClaims
        case GoBack

    override def init(flags: Flags): Model =
        val group =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.getGroup(flags.groupID).value))
            ).toOption
                .get
        val subgroup = group.subgroups.find(_.id == flags.subgroupID).get
        Model(group, subgroup, flags.userID)

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.GoBack =>
                val p = GroupManagementProgram()
                services.transferTo(
                    p,
                    p.Flags(model.group.metadata.id, model.userID),
                )
                model
            case Message.DeleteSubgroup =>
                sql.useBlocking(
                    sql.withS(
                        sql.withTX(
                            gm.deleteSubgroup(
                                model.userID,
                                model.group.metadata.id,
                                model.subgroup.id,
                            ).value
                        )
                    )
                ) match
                    case Left(err) =>
                        services.notify(
                            s"You cannot delete that subgroup because ${err.explain()}"
                        )
                        model
                    case Right(_) =>
                        val p = GroupManagementProgram()
                        services.transferTo(
                            p,
                            p.Flags(model.group.metadata.id, model.userID),
                        )
                        model
            case Message.ClickRole(role) =>
                val p = RoleManagementProgram()
                services.transferTo(
                    p,
                    p.Flags(
                        model.group.metadata.id,
                        role,
                        model.userID,
                        Some(model.subgroup.id),
                    ),
                )
                model
            case Message.EditClaims =>
                val player = Bukkit.getPlayer(model.userID)
                FireAndForget {
                    editor.create(
                        player,
                        player.getWorld,
                        model.group.metadata.id,
                        model.subgroup.id,
                    )
                }
                noodleEditor.edit(
                    player,
                    NoodleKey(model.group.metadata.id, model.subgroup.id),
                    player.getWorld,
                )
                services.quit()
                model
            case Message.EditArterialClaims =>
                val player = Bukkit.getPlayer(model.userID)
                noodleEditor.edit(
                    player,
                    NoodleKey(model.group.metadata.id, model.subgroup.id),
                    player.getWorld,
                )
                services.quit()
                model

    override def view(model: Model): Callback ?=> Gui =
        Root(
            trans"ui.groups.subgroup.title".args(
                model.subgroup.name.toComponent,
                model.group.metadata.name.toComponent,
            ),
            6,
        ) {
            OutlinePane(0, 0, 1, 6) {
                Button(
                    Material.OAK_DOOR,
                    trans"ui.go-back".style(NamedTextColor.WHITE),
                    Message.GoBack,
                )()
                Button(
                    Material.LAVA_BUCKET,
                    trans"ui.groups.subgroup.delete".style(
                        NamedTextColor.GREEN
                    ),
                    Message.DeleteSubgroup,
                )()
                Button(
                    Material.SPYGLASS,
                    trans"ui.groups.subgroups.assign-land".style(
                        NamedTextColor.GREEN
                    ),
                    Message.EditClaims,
                ) {
                    Lore(
                        trans"ui.groups.subgroups.assign-land.lore"
                    )
                }
                Button(
                    Material.RAIL,
                    trans"ui.groups.arterial".style(NamedTextColor.GREEN),
                    Message.EditArterialClaims,
                ) {
                    Lore(
                        trans"ui.groups.arterial.subgroup.subtitle"
                            .color(NamedTextColor.GRAY)
                    )
                }
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item(
                    Material.BLACK_STAINED_GLASS_PANE,
                    displayName = Some(txt""),
                )()
            }
            OutlinePane(2, 0, 7, 6) {
                model.group.roles.foreach { x =>
                    Button(
                        Material.LEATHER_CHESTPLATE,
                        txt"${x.name}".style(NamedTextColor.WHITE),
                        Message.ClickRole(x.id),
                    ) {
                        val permissions =
                            model.subgroup.permissions.getOrElse(x.id, Map())
                        if permissions.nonEmpty then
                            Lore(txt"")
                            Lore(
                                trans"ui.groups.subgroups.permissions-overrides"
                                    .style(
                                        NamedTextColor.WHITE,
                                        TextDecoration.UNDERLINED,
                                    )
                            )
                            permissions.toList.sortBy(_._1.ordinal).foreach {
                                x =>
                                    val (p, r) = x
                                    if r == RuleMode.Allow then
                                        Lore(
                                            txt"- ✔ ${p.displayName()}"
                                                .style(NamedTextColor.GREEN)
                                        )
                                    else
                                        Lore(
                                            txt"- ✖ ${p.displayName()}"
                                                .style(NamedTextColor.RED)
                                        )
                            }
                    }
                }
            }
        }

class RoleManagementProgram(using
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    editor: PolyhedraEditor,
    primeTime: PrimeTimeManager,
    kv: KeyVal,
    noodleEditor: NoodleEditor,
) extends UIProgram:
    case class Flags(
        groupID: GroupID,
        roleID: RoleID,
        userID: UserID,
        subgroup: Option[SubgroupID],
    )

    case class Model(
        group: GroupState,
        groupID: GroupID,
        userID: UserID,
        role: RoleState,
        subgroup: Option[SubgroupState],
    )

    enum Message:
        case DeleteRole
        case TogglePermission(val perm: Permissions)
        case GoBack
        case AssignToMember
        case RevokeFromMember

    override def init(flags: Flags): Model =
        val group =
            sql.useBlocking(
                sql.withS(sql.withTX(gm.getGroup(flags.groupID).value))
            ).toOption
                .get
        val role = group.roles.find(_.id == flags.roleID).get
        val subgroup =
            flags.subgroup.map(id => group.subgroups.find(_.id == id).get)
        Model(group, flags.groupID, flags.userID, role, subgroup)

    def back(model: Model)(using services: UIServices): Future[Model] =
        model.subgroup match
            case None =>
                val p = GroupManagementProgram()
                services.transferTo(p, p.Flags(model.groupID, model.userID))
                model
            case Some(subgroup) =>
                val p = SubgroupManagementProgram()
                services.transferTo(
                    p,
                    p.Flags(model.groupID, subgroup.id, model.userID),
                )
                model

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.DeleteRole =>
                sql.useBlocking(
                    sql.withS(
                        sql.withTX(
                            gm.deleteRole(
                                model.userID,
                                model.role.id,
                                model.groupID,
                            ).value
                        )
                    )
                ) match
                    case Left(err) =>
                        services.notify(
                            s"You cannot delete that role because ${err.explain()}"
                        )
                        model
                    case Right(_) =>
                        back(model)
            case Message.TogglePermission(perm) =>
                val permissions = model.subgroup match
                    case None =>
                        model.role.permissions
                    case Some(subgroup) =>
                        subgroup.permissions.getOrElse(model.role.id, Map())

                val newPerm = permissions.get(perm) match
                    case None => Some(RuleMode.Allow)
                    case Some(RuleMode.Allow) => Some(RuleMode.Deny)
                    case Some(RuleMode.Deny) => None
                val newPermissions =
                    newPerm match
                        case None => permissions.removed(perm)
                        case Some(mode) => permissions.updated(perm, mode)

                model.subgroup match
                    case None =>
                        sql.useBlocking(
                            sql.withS(
                                sql.withTX(
                                    gm.setRolePermissions(
                                        model.userID,
                                        model.groupID,
                                        model.role.id,
                                        newPermissions,
                                    ).value
                                )
                            )
                        ) match
                            case Left(err) =>
                                services.notify(
                                    s"You cannot change that permission because ${err.explain()}"
                                )
                                model
                            case Right(_) =>
                                model.copy(role =
                                    model.role.copy(permissions =
                                        newPermissions
                                    )
                                )
                    case Some(subgroup) =>
                        sql.useBlocking(
                            sql.withS(
                                sql.withTX(
                                    gm.setSubgroupRolePermissions(
                                        model.userID,
                                        model.groupID,
                                        subgroup.id,
                                        model.role.id,
                                        newPermissions,
                                    ).value
                                )
                            )
                        ) match
                            case Left(err) =>
                                services.notify(
                                    s"You cannot change that permission in ${subgroup.name} because ${err.explain()}"
                                )
                                model
                            case Right(_) =>
                                model.copy(subgroup =
                                    Some(
                                        subgroup.copy(permissions =
                                            subgroup.permissions
                                                .updated(
                                                    model.role.id,
                                                    newPermissions,
                                                )
                                        )
                                    )
                                )
            case Message.AssignToMember =>
                services
                    .prompt("Who do you want to assign this role to?")
                    .map { username =>
                        Option(Bukkit.getOfflinePlayerIfCached(username)) match
                            case None =>
                                services.notify(
                                    "I couldn't find a player with that username"
                                )
                            case Some(plr) =>
                                sql.useBlocking(
                                    sql.withS(
                                        sql.withTX(
                                            gm.assignRole(
                                                model.userID,
                                                plr.getUniqueId,
                                                model.group.metadata.id,
                                                model.role.id,
                                                true,
                                            ).value
                                        )
                                    )
                                ) match
                                    case Left(err) =>
                                        services.notify(
                                            s"Couldn't assign role because ${err.explain()}"
                                        )
                                    case Right(_) =>
                                        services.notify(
                                            s"Assigned role!"
                                        )
                        model
                    }
            case Message.RevokeFromMember =>
                services
                    .prompt("Who do you want to revoke this role from?")
                    .map { username =>
                        Option(Bukkit.getOfflinePlayerIfCached(username)) match
                            case None =>
                                services.notify(
                                    "I couldn't find a player with that username"
                                )
                            case Some(plr) =>
                                sql.useBlocking(
                                    sql.withS(
                                        sql.withTX(
                                            gm.assignRole(
                                                model.userID,
                                                plr.getUniqueId,
                                                model.group.metadata.id,
                                                model.role.id,
                                                false,
                                            ).value
                                        )
                                    )
                                ) match
                                    case Left(err) =>
                                        services.notify(
                                            s"Couldn't revoke role because ${err.explain()}"
                                        )
                                    case Right(_) =>
                                        services.notify(
                                            s"Revoked role!"
                                        )
                        model
                    }
            case Message.GoBack =>
                back(model)

    override def view(model: Model): Callback ?=> Gui =
        val role = model.role
        val group = model.group
        val title = model.subgroup match
            case None =>
                trans"ui.groups.role.title".args(
                    role.name.toComponent,
                    group.metadata.name.toComponent,
                )
            case Some(subgroup) =>
                trans"ui.groups.role.override.title".args(
                    role.name.toComponent,
                    subgroup.name.toComponent,
                    group.metadata.name.toComponent,
                )

        Root(title, 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(
                    Material.OAK_DOOR,
                    trans"ui.go-back".style(NamedTextColor.WHITE),
                    Message.GoBack,
                )()
                if model.subgroup.isEmpty && role.id != everyoneUUID && role.id != groupMemberUUID
                then
                    Button(
                        Material.LAVA_BUCKET,
                        trans"ui.groups.roles.delete".style(
                            NamedTextColor.GREEN
                        ),
                        Message.DeleteRole,
                    )()
                if model.subgroup.isEmpty then
                    Button(
                        Material.GREEN_CONCRETE,
                        trans"ui.groups.roles.assign".style(
                            NamedTextColor.WHITE
                        ),
                        Message.AssignToMember,
                    )()
                    Button(
                        Material.RED_CONCRETE_POWDER,
                        trans"ui.groups.roles.revoke".style(
                            NamedTextColor.WHITE
                        ),
                        Message.RevokeFromMember,
                    )()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item(
                    Material.BLACK_STAINED_GLASS_PANE,
                    displayName = Some(txt""),
                )()
            }
            OutlinePane(2, 0, 7, 6) {
                Permissions.values.foreach { x =>
                    val permissions =
                        model.subgroup match
                            case None => role.permissions
                            case Some(subgroup) =>
                                subgroup.permissions.getOrElse(role.id, Map())

                    val name =
                        permissions.get(x) match
                            case None => s"§7* ${x.displayName()}"
                            case Some(RuleMode.Allow) =>
                                s"§a✔ ${x.displayName()}"
                            case Some(RuleMode.Deny) =>
                                s"§c✖ ${x.displayName()}"

                    Button(
                        x.displayItem(),
                        txt"$name",
                        Message.TogglePermission(x),
                    ) {
                        Lore(
                            txt"${x.displayExplanation()}"
                                .color(NamedTextColor.WHITE)
                        )
                        Lore(txt"")

                        model.subgroup match
                            case None =>
                                permissions.get(x) match
                                    case None =>
                                        Lore(
                                            trans"ui.groups.roles.permissions.neutral"
                                                .color(
                                                    NamedTextColor.GRAY
                                                )
                                        )
                                    case Some(RuleMode.Allow) =>
                                        Lore(
                                            trans"ui.groups.roles.permissions.allow"
                                                .color(NamedTextColor.GRAY)
                                        )
                                    case Some(RuleMode.Deny) =>
                                        Lore(
                                            trans"ui.groups.roles.permissions.deny"
                                                .color(NamedTextColor.GRAY)
                                        )
                            case Some(subgroup) =>
                                permissions.get(x) match
                                    case None =>
                                        Lore(
                                            trans"ui.groups.roles.permissions.subgroup.neutral"
                                                .args(subgroup.name.toComponent)
                                                .color(NamedTextColor.GRAY)
                                        )
                                    case Some(RuleMode.Allow) =>
                                        Lore(
                                            trans"ui.groups.roles.permissions.subgroup.allow"
                                                .args(subgroup.name.toComponent)
                                                .color(NamedTextColor.GRAY)
                                        )
                                    case Some(RuleMode.Deny) =>
                                        Lore(
                                            trans"ui.groups.roles.permissions.subgroup.deny"
                                                .args(subgroup.name.toComponent)
                                                .color(NamedTextColor.GRAY)
                                        )

                        Lore(txt"")
                        permissions.get(x) match
                            case None =>
                                Lore(
                                    txt"Click to toggle ${txt"ignore".color(NamedTextColor.WHITE)}/allow/deny"
                                        .color(NamedTextColor.GRAY)
                                )
                            case Some(RuleMode.Allow) =>
                                Lore(
                                    txt"Click to toggle ignore/${txt"allow"
                                            .color(NamedTextColor.WHITE)}/deny"
                                        .color(NamedTextColor.GRAY)
                                )
                            case Some(RuleMode.Deny) =>
                                Lore(
                                    txt"Click to toggle ignore/allow/${txt"deny"
                                            .color(NamedTextColor.WHITE)}"
                                        .color(NamedTextColor.GRAY)
                                )
                    }
                }
            }
        }

class InvitesListProgram(using
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    phe: PolyhedraEditor,
    primeTime: PrimeTimeManager,
    kv: KeyVal,
    noodleEditor: NoodleEditor,
) extends UIProgram:
    case class Flags(userID: UserID)

    // noinspection ScalaWeakerAccess
    enum Mode:
        case List
        case ViewingInvite(user: UserID, group: GroupState)

    case class Model(
        userID: UserID,
        invites: List[(UserID, GroupState)],
        mode: Mode,
    )

    enum Message:
        case ClickInvite(inviter: UserID, group: GroupID)
        case AcceptInvite(group: GroupID)
        case DenyInvite(group: GroupID)
        case GoBack()

    private def computeInvites(userID: UserID): List[(UserID, GroupState)] =
        sql
            .useBlocking(sql.withS(gm.invites.getInvitesFor(userID)))
            .flatMap { (uid, gid) =>
                sql.useBlocking(sql.withS(sql.withTX(gm.getGroup(gid).value)))
                    .toOption
                    .map((uid, _))
            }

    override def init(flags: Flags): Model =
        Model(flags.userID, computeInvites(flags.userID), Mode.List)

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
        msg match
            case Message.ClickInvite(user, group) =>
                model.copy(mode =
                    Mode.ViewingInvite(
                        user,
                        model.invites.find(_._1 == user).get._2,
                    )
                )
            case Message.AcceptInvite(group) =>
                sql.useBlocking(
                    sql.withS(
                        sql.withTX(gm.invites.acceptInvite(model.userID, group))
                    )
                )
                model.copy(
                    mode = Mode.List,
                    invites = computeInvites(model.userID),
                )
            case Message.DenyInvite(group) =>
                sql.useBlocking(
                    sql.withS(gm.invites.deleteInvite(model.userID, group))
                )
                model.copy(
                    mode = Mode.List,
                    invites = computeInvites(model.userID),
                )
            case Message.GoBack() =>
                val p = GroupListProgram()
                services.transferTo(
                    p,
                    p.Flags(model.userID),
                )
                model

    override def view(model: Model): Callback ?=> Gui =
        model.mode match
            case Mode.List => list(model)
            case Mode.ViewingInvite(user, group) => viewing(user, group)

    def list(model: Model): Callback ?=> Gui =
        val rows = (model.invites.length / 7).max(1)
        Root(trans"ui.groups.invites.title", rows) {
            OutlinePane(0, 0, 1, rows) {
                Button(
                    Material.OAK_DOOR,
                    trans"ui.go-back".style(NamedTextColor.WHITE),
                    Message.GoBack,
                )()
            }
            OutlinePane(
                1,
                0,
                1,
                rows,
                priority = Priority.LOWEST,
                repeat = true,
            ) {
                Item(
                    Material.BLACK_STAINED_GLASS_PANE,
                    displayName = Some(txt""),
                )()
            }
            OutlinePane(2, 0, 7, rows) {
                model.invites.foreach { invite =>
                    val player = Bukkit.getOfflinePlayer(invite._1)
                    Button(
                        Material.PLAYER_HEAD,
                        player.getName.toComponent.color(NamedTextColor.WHITE),
                        Message.ClickInvite(invite._1, invite._2.metadata.id),
                    ) {
                        Skull(player)
                        Lore(
                            trans"ui.groups.invites.invited-to-group"
                                .args(
                                    invite._2.metadata.name.toComponent
                                        .color(NamedTextColor.GREEN)
                                )
                                .color(NamedTextColor.WHITE)
                        )
                    }
                }
            }
        }

    private def viewing(
        inviter: UserID,
        group: GroupState,
    ): Callback ?=> Gui =
        Root(trans"ui.groups.invites.confirmation.title", 1) {
            OutlinePane(0, 0, 1, 1) {
                Button(
                    Material.RED_DYE,
                    trans"ui.groups.invites.confirmation.deny".color(
                        NamedTextColor.WHITE
                    ),
                    Message.DenyInvite(group.metadata.id),
                )()
            }
            OutlinePane(4, 0, 1, 1) {
                val player = Bukkit.getOfflinePlayer(inviter)
                Item(
                    Material.PLAYER_HEAD,
                    displayName = Some(
                        player.getName.toComponent.style(NamedTextColor.WHITE)
                    ),
                ) {
                    Skull(player)
                    Lore(
                        trans"ui.groups.invites.invited-to-group"
                            .args(
                                group.metadata.name.toComponent
                                    .color(NamedTextColor.GREEN)
                            )
                            .color(NamedTextColor.WHITE)
                    )
                }
            }
            OutlinePane(8, 0, 1, 1) {
                Button(
                    Material.LIME_DYE,
                    trans"ui.groups.invites.confirmation.accept".color(
                        NamedTextColor.WHITE
                    ),
                    Message.AcceptInvite(group.metadata.id),
                )()
            }
        }

class GroupListProgram(using
    gm: GroupManager,
    cbm: CivBeaconManager,
    sql: SQLManager,
    editor: PolyhedraEditor,
    primeTime: PrimeTimeManager,
    kv: KeyVal,
    noodleEditor: NoodleEditor,
) extends UIProgram:
    case class Flags(userID: UserID)

    case class Model(userID: UserID, groups: List[GroupStates])

    enum Message:
        case ClickGroup(groupID: GroupID)
        case CreateGroup
        case Invites

    override def init(flags: Flags): Model =
        Model(
            flags.userID,
            sql
                .useBlocking {
                    sql.withS(sql.withTX(gm.userGroups(flags.userID).value))
                }
                .toOption
                .get,
        )

    override def update(msg: Message, model: Model)(using
        services: UIServices
    ): Future[Model] =
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
                val answer =
                    services.prompt("What do you want to call the group?")
                answer.map { result =>
                    val newGroups = sql.useBlocking(sql.withS(sql.withTX(for {
                        _ <- gm.createGroup(model.userID, result)
                        userGroups <- gm.userGroups(model.userID).value
                    } yield userGroups.toOption.get)))
                    model.copy(groups = newGroups)
                }

    override def view(model: Model): Callback ?=> Gui =
        val groups = model.groups
        Root(trans"ui.groups.title", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(
                    Material.NAME_TAG,
                    trans"ui.groups.create".color(NamedTextColor.GREEN),
                    Message.CreateGroup,
                )()
                Button(
                    Material.PAPER,
                    trans"ui.groups.invites".color(NamedTextColor.GREEN),
                    Message.Invites,
                )()
            }
            OutlinePane(1, 0, 1, 6, priority = Priority.LOWEST, repeat = true) {
                Item(
                    Material.BLACK_STAINED_GLASS_PANE,
                    displayName = Some(txt" "),
                )()
            }
            OutlinePane(2, 0, 7, 6) {
                groups.foreach { x =>
                    Button(
                        Material.LEATHER_CHESTPLATE,
                        x.name.toComponent.color(NamedTextColor.GREEN),
                        Message.ClickGroup(x.id),
                    )()
                }
            }
        }
