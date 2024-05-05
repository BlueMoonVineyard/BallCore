package CivCubed.Commands

import CivCubed.Storage.SQLManager
import CivCubed.TextComponents.*
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import cats.effect.IO
import CivCubed.Fingerprints.FingerprintManager

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
                            trans"commands.my-fingerprint.your-fingerprint-id".args(print.toComponent)
                        )
                        sender.sendServerMessage(
                            trans"commands.my-fingerprint.unshare.warning"
                        )
                    }
                } yield ()))
            }: PlayerCommandExecutor)
