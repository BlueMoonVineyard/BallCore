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
        Root(txt"Viewing ${group.metadata.name}", 6) {
            VerticalPane(0, 0, 2, 6) {
                Button(
                    Material.PLAYER_HEAD,
                    txt"Members".style(NamedTextColor.GREEN),
                    Message.View(ViewingWhat.Players),
                    highlighted = model.viewing == ViewingWhat.Players,
                )()
                if group.check(Permissions.ManageRoles, model.userID, nullUUID)
                then
                    Button(
                        Material.WRITABLE_BOOK,
                        txt"Manage Roles".style(NamedTextColor.GREEN),
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
                        txt"Manage Subgroups".style(NamedTextColor.GREEN),
                        Message.View(ViewingWhat.Subgroups),
                        highlighted = model.viewing == ViewingWhat.Subgroups,
                    )()
                if !group.owners.contains(model.userID) then
                    Button(
                        Material.MINECART,
                        txt"Leave Group".style(NamedTextColor.GREEN),
                        Message.LeaveGroup,
                    )()
                else if group.owners.length == 1 then
                    Button(
                        Material.LAVA_BUCKET,
                        txt"Delete Group".style(NamedTextColor.GREEN),
                        Message.DeleteGroup,
                    )()
                if model.canBindToHeart then
                    Button(
                        Material.WHITE_CONCRETE,
                        txt"Bind to Beacon".style(NamedTextColor.GREEN),
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
                        txt"Edit Arterial Claims".style(NamedTextColor.GREEN),
                        Message.EditArterialClaims,
                    ) {
                        Lore(
                            txt"Claim strings of land on behalf of this group"
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
                        txt"Vulnerability Window".style(NamedTextColor.GREEN),
                        Message.UpdateVulnerabilityWindow,
                    ) {
                        model.primeTime match
                            case None =>
                                Lore(
                                    txt"The vulnerability window is not set yet."
                                        .color(NamedTextColor.WHITE)
                                )
                                model.userZone match
                                    case None =>
                                        Lore(
                                            txt"You need to set a personal timezone with ${txt("/settings timezone <time-zone>")
                                                    .color(Colors.teal)} in order to set the vulnerability window."
                                                .color(NamedTextColor.WHITE)
                                        )
                                    case Some(value) =>
                                        Lore(
                                            txt"Right click to set the vulnerability window."
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
                                        val startS = formatter.format(start)
                                        val endS = formatter.format(end)
                                        Lore(
                                            txt"The vulnerability window is from ${startS} to ${endS}."
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            txt"Right click to change the vulnerability window."
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            txt"Warning: if you change the vulnerability window,"
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            txt"the new one and the old one will coexist for today and tomorrow."
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            txt"You will not be able to change it until the end of the old time tomorrow."
                                                .color(NamedTextColor.WHITE)
                                        )
                                    case None =>
                                        val start = time.withOffsetSameInstant(
                                            ZoneOffset.UTC
                                        )
                                        val end =
                                            start.plus(primeTime.windowSize)
                                        val startS = formatter.format(start)
                                        val endS = formatter.format(end)
                                        Lore(
                                            txt"The vulnerability window is from ${startS} to ${endS}."
                                                .color(NamedTextColor.WHITE)
                                        )
                                        Lore(
                                            txt"You need to set a personal timezone with ${txt("/settings timezone <time-zone>")
                                                    .color(Colors.teal)} in order to set the vulnerability window."
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
                    txt"${subgroup.name}".style(NamedTextColor.WHITE),
                    Message.ClickSubgroup(subgroup.id),
                ) {
                    Lore(txt"")
                    Lore(
                        txt"Click to edit this subgroup".color(
                            NamedTextColor.GRAY
                        )
                    )
                }
            }
        }
        OutlinePane(2, 5, 7, 1) {
            Button(
                Material.WRITABLE_BOOK,
                txt"Create Subgroup".style(NamedTextColor.GREEN),
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
                        txt"${x.getName}",
                        Message.KickPlayer(x),
                    ) {
                        Skull(x)
                        if group.owners.contains(x.getUniqueId) then
                            Lore(
                                txt"Owner".style(
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
                                txt"Roles"
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
                                txt"Click to remove from the group"
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
                    txt"Invite A Member".style(NamedTextColor.GREEN),
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
                            txt"Permissions"
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
                    txt"Create Role",
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
                    txt"Delete ${model.group.metadata.name}?",
                    txt"Delete ${model.group.metadata.name}",
                    txt"Don't Delete ${model.group.metadata.name}",
                    Item(
                        Material.LEATHER_CHESTPLATE,
                        displayName = Some(
                            txt"${model.group.metadata.name}"
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
                    txt"Leave ${model.group.metadata.name}?",
                    txt"Leave ${model.group.metadata.name}",
                    txt"Don't Leave ${model.group.metadata.name}",
                    Item(
                        Material.LEATHER_CHESTPLATE,
                        displayName = Some(
                            txt"${model.group.metadata.name}"
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
            txt"Viewing Subgroup ${model.subgroup.name} in ${model.group.metadata.name}",
            6,
        ) {
            OutlinePane(0, 0, 1, 6) {
                Button(
                    Material.OAK_DOOR,
                    txt"Go Back".style(NamedTextColor.WHITE),
                    Message.GoBack,
                )()
                Button(
                    Material.LAVA_BUCKET,
                    txt"Delete Subgroup".style(NamedTextColor.GREEN),
                    Message.DeleteSubgroup,
                )()
                Button(
                    Material.SPYGLASS,
                    txt"Assign Land".style(NamedTextColor.GREEN),
                    Message.EditClaims,
                ) {
                    Lore(
                        txt"Assign portions of beacon-covered land to this subgroup"
                    )
                }
                Button(
                    Material.RAIL,
                    txt"Edit Arterial Claims".style(NamedTextColor.GREEN),
                    Message.EditArterialClaims,
                ) {
                    Lore(
                        txt"Claim strings of land on behalf of this subgroup"
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
                                txt"Permissions Overrides"
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
                txt"Viewing Role ${role.name} in ${group.metadata.name}"
            case Some(subgroup) =>
                txt"Viewing overrides for ${role.name} in ${subgroup.name} of ${group.metadata.name}"

        Root(title, 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(
                    Material.OAK_DOOR,
                    txt"Go Back".style(NamedTextColor.WHITE),
                    Message.GoBack,
                )()
                if model.subgroup.isEmpty && role.id != everyoneUUID && role.id != groupMemberUUID
                then
                    Button(
                        Material.LAVA_BUCKET,
                        txt"Delete Role".style(NamedTextColor.GREEN),
                        Message.DeleteRole,
                    )()
                if model.subgroup.isEmpty then
                    Button(
                        Material.GREEN_CONCRETE,
                        txt"Assign to Member".style(NamedTextColor.WHITE),
                        Message.AssignToMember,
                    )()
                    Button(
                        Material.RED_CONCRETE_POWDER,
                        txt"Revoke from Member".style(NamedTextColor.WHITE),
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
                                            txt"This role does not affect this permission"
                                                .color(
                                                    NamedTextColor.GRAY
                                                )
                                        )
                                    case Some(RuleMode.Allow) =>
                                        Lore(
                                            txt"This role allows this permission unless overridden by a higher role or subgroup permission"
                                                .color(NamedTextColor.GRAY)
                                        )
                                    case Some(RuleMode.Deny) =>
                                        Lore(
                                            txt"This role denies this permission unless overridden by a higher role or subgroup permission"
                                                .color(NamedTextColor.GRAY)
                                        )
                            case Some(subgroup) =>
                                permissions.get(x) match
                                    case None =>
                                        Lore(
                                            txt"This role does not affect this permission in ${subgroup.name}"
                                                .color(NamedTextColor.GRAY)
                                        )
                                    case Some(RuleMode.Allow) =>
                                        Lore(
                                            txt"This role allows this permission in ${subgroup.name} unless overridden by a higher role"
                                                .color(NamedTextColor.GRAY)
                                        )
                                    case Some(RuleMode.Deny) =>
                                        Lore(
                                            txt"This role denies this permission in ${subgroup.name} unless overridden by a higher role"
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
        Root(txt"Invites", rows) {
            OutlinePane(0, 0, 1, rows) {
                Button(
                    Material.OAK_DOOR,
                    txt"Go Back".style(NamedTextColor.WHITE),
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
                        txt"${player.getName}".color(NamedTextColor.WHITE),
                        Message.ClickInvite(invite._1, invite._2.metadata.id),
                    ) {
                        Skull(player)
                        Lore(
                            txt"Invited you to ${txt"${invite._2.metadata.name}"
                                    .color(NamedTextColor.GREEN)}"
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
        Root(txt"Accept / Reject Invite?", 1) {
            OutlinePane(0, 0, 1, 1) {
                Button(
                    Material.RED_DYE,
                    txt"Reject Invite".color(NamedTextColor.WHITE),
                    Message.DenyInvite(group.metadata.id),
                )()
            }
            OutlinePane(4, 0, 1, 1) {
                val player = Bukkit.getOfflinePlayer(inviter)
                Item(
                    Material.PLAYER_HEAD,
                    displayName = Some(
                        txt"§${player.getName}".style(NamedTextColor.WHITE)
                    ),
                ) {
                    Skull(player)
                    Lore(
                        txt"Invited you to ${txt"${group.metadata.name}".style(NamedTextColor.GREEN)}"
                            .style(NamedTextColor.WHITE)
                    )
                }
            }
            OutlinePane(8, 0, 1, 1) {
                Button(
                    Material.LIME_DYE,
                    txt"Accept Invite".color(NamedTextColor.WHITE),
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
        Root(txt"Groups", 6) {
            OutlinePane(0, 0, 1, 6) {
                Button(
                    Material.NAME_TAG,
                    txt"Create Group".color(NamedTextColor.GREEN),
                    Message.CreateGroup,
                )()
                Button(
                    Material.PAPER,
                    txt"View Invites".color(NamedTextColor.GREEN),
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
                        txt"${x.name}".color(NamedTextColor.GREEN),
                        Message.ClickGroup(x.id),
                    )()
                }
            }
        }
