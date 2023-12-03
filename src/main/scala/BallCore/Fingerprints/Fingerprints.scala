package BallCore.Fingerprints

import org.bukkit.plugin.Plugin
import BallCore.DataStructures.Clock
import BallCore.Storage.SQLManager
import cats.effect.std.Random
import cats.effect.IO

object Fingerprints:
	def register()(using p: Plugin, c: Clock, sql: SQLManager): FingerprintManager =
		given Random[IO] = sql.useBlocking(Random.scalaUtilRandom[IO])
		given it: FingerprintManager = FingerprintManager()
		p.getServer().getPluginManager().registerEvents(Listener(), p)
		it
