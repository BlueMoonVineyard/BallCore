package BallCore.Commands

import BallCore.PolygonEditor.PolygonEditor
import BallCore.PolyhedraEditor.PolyhedraEditor
import BallCore.TextComponents.*
import org.bukkit.entity.Player
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import BallCore.NoodleEditor.NoodleEditor

class CancelCommand(using
    editor: PolygonEditor,
    polyhedraEditor: PolyhedraEditor,
    noodleEditor: NoodleEditor,
):
    val node =
        CommandTree("cancel")
            .executesPlayer({ (sender, args) =>
                val plr = sender.asInstanceOf[Player]
                editor.cancel(plr)
                polyhedraEditor.cancel(plr)
                noodleEditor.cancel(plr)
            }: PlayerCommandExecutor)

class DoneCommand(using
    editor: PolygonEditor,
    polyhedraEditor: PolyhedraEditor,
    noodleEditor: NoodleEditor,
):
    val node =
        CommandTree("done")
            .executesPlayer({ (sender, args) =>
                val plr = sender.asInstanceOf[Player]
                editor.done(plr)
                polyhedraEditor.done(plr)
                noodleEditor.done(plr)
            }: PlayerCommandExecutor)

class DeclareCommand(using
    editor: PolygonEditor
):
    val node =
        CommandTree("declare")
            .executesPlayer({ (sender, args) =>
                editor.declare(sender)
            }: PlayerCommandExecutor)
