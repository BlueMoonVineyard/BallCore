package BallCore.Reinforcements

import BallCore.DataStructures.LRUCache
import org.bukkit.Location
import java.time.OffsetDateTime
import BallCore.DataStructures.Clock
import java.time.temporal.ChronoUnit

enum BustResult:
    case justBusted
    case alreadyBusted
    case busting
    case notBusting
    case bustingBlocked(windowOpensAt: OffsetDateTime)

class BustThroughTracker(using c: Clock):
    val blockHealth = 250
    val expiryMinutes = 10

    private val breakTracker =
        LRUCache[Location, (Int, OffsetDateTime)](1000, (_, _) => ())
    private val bustedBlocks =
        LRUCache[Location, OffsetDateTime](1000, (_, _) => ())

    def bust(at: Location): BustResult =
        import BustResult._

        bustedBlocks.get(at) match
            case Some(time)
                if ChronoUnit.MINUTES.between(time, c.now()) <= expiryMinutes =>
                return alreadyBusted
            case _ =>

        breakTracker.get(at) match
            case Some((nums, time))
                if ChronoUnit.MINUTES.between(
                    time,
                    c.now(),
                ) <= expiryMinutes =>
                val nextNums = nums + 1
                if nextNums < blockHealth then
                    breakTracker.update(at, (nextNums, c.now()))
                    busting
                else
                    breakTracker.remove(at)
                    bustedBlocks.update(at, c.now())
                    justBusted
            case _ =>
                breakTracker.update(at, (1, c.now()))
                busting
