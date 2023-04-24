package BallCore.CraftingStations

object Listener:
	def register(): Unit =
		Thread.startVirtualThread(() => CraftingActor.mainLoop())
