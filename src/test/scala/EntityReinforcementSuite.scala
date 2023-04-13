import BallCore.Storage
import BallCore.Groups
import BallCore.Reinforcements
import java.{util => ju}
import org.bukkit.NamespacedKey
import java.util.jar.Attributes.Name
import BallCore.DataStructures.Clock
import BallCore.DataStructures.TestClock
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.time.temporal.ChronoUnit
import BallCore.Reinforcements.ReinforcementError
import BallCore.Reinforcements.ReinforcementState
import BallCore.Reinforcements.ReinforcementTypes

class EntityReinforcementSuite extends munit.FunSuite {
    test("basic stuff") {
        given sql: Storage.SQLManager = new Storage.SQLManager(test = true)
        given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal
        given gm: Groups.GroupManager = new Groups.GroupManager
        given csm: Reinforcements.ChunkStateManager = new Reinforcements.ChunkStateManager
        given esm: Reinforcements.EntityStateManager = new Reinforcements.EntityStateManager
        given clock: Clock = new TestClock(Instant.MIN)
        given erm: Reinforcements.EntityReinforcementManager = new Reinforcements.EntityReinforcementManager

        val u1 = ju.UUID.randomUUID()
        val u2 = ju.UUID.randomUUID()
        val world = ju.UUID.randomUUID()
        val entity = ju.UUID.randomUUID()

        val gid = gm.createGroup(u1, "test")
        gm.addToGroup(u2, gid)

        val res1 = erm.reinforce(u2, gid, entity, ReinforcementTypes.IronLike)
        assert(res1 == Left(Reinforcements.ReinforcementGroupError(Groups.GroupError.NoPermissions)), res1)

        val res2 = erm.reinforce(u1, gid, entity, ReinforcementTypes.IronLike)
        assert(res2 == Right(()), res2)

        val rid = gm.roles(gid).getOrElse(List()).find { x => x.name == "Admin" }.get.id
        assert(gm.assignRole(u1, u2, gid, rid, true).isRight)

        val res3 = erm.reinforce(u2, gid, entity, ReinforcementTypes.IronLike)
        assert(res3 == Left(Reinforcements.AlreadyExists()), res3)

        val res4 = erm.unreinforce(u2, entity)
        assert(res4 == Right(()), res4)

        val res5 = erm.unreinforce(u2, entity)
        assert(res5 == Left(Reinforcements.DoesntExist()))
    }
    test("damaging shenanigans") {
        given sql: Storage.SQLManager = new Storage.SQLManager(test = true)
        given keyVal: Storage.SQLKeyVal = new Storage.SQLKeyVal
        given gm: Groups.GroupManager = new Groups.GroupManager
        given csm: Reinforcements.ChunkStateManager = new Reinforcements.ChunkStateManager
        given esm: Reinforcements.EntityStateManager = new Reinforcements.EntityStateManager
        given clock: TestClock = new TestClock(Instant.MIN)
        given erm: Reinforcements.EntityReinforcementManager = new Reinforcements.EntityReinforcementManager

        val u1 = ju.UUID.randomUUID()
        val u2 = ju.UUID.randomUUID()
        val world = ju.UUID.randomUUID()
        val entity = ju.UUID.randomUUID()

        val gid = gm.createGroup(u1, "test")
        gm.addToGroup(u2, gid)

        val res1 = erm.reinforce(u1, gid, entity, ReinforcementTypes.IronLike)
        assert(res1 == Right(()), res1)

        val res2 = erm.damage(entity)
        assert(res2.isRight, res2)

        val res3 = erm.damage(entity)
        assert(res3.isRight, res3)

        val d1 = res2.right.get.health - res3.right.get.health

        clock.changeTimeBy(ChronoUnit.HOURS.getDuration().multipliedBy(5))

        val res4 = erm.damage(entity)
        assert(res4.isRight, res4)

        val d2 = res3.right.get.health - res4.right.get.health

        assert(d2 < d1, (d2, d1))

        csm.evictAll()
    }
}