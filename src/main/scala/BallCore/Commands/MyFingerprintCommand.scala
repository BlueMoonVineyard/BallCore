package BallCore.Commands

import BallCore.Storage.SQLManager
import BallCore.TextComponents.*
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import cats.effect.IO
import BallCore.Fingerprints.FingerprintManager

class MyFingerprintCommand(using
    sql: SQLManager,
    fingerprints: FingerprintManager,
):
    val node =
        CommandTree("fingerprint")
            .executesPlayer({ (sender, args) =>
                sql.useFireAndForget(sql.withS(for {
                    print <- sql.withTX(
                        fingerprints.fingerprintFor(sender.getUniqueId)
                    )
                    _ <- IO {
                        sender.sendServerMessage(
                            txt"Your fingerprint ID is ${print}"
                        )
                        sender.sendServerMessage(
                            txt"Remember: once you share it with other players, you can't unshare it!"
                        )
                    }
                } yield ()))
            }: PlayerCommandExecutor)
