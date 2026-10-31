package sapphire.sim

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire.Config
import sapphire.dma.{DmaBus, DmaMux}
import spinal.core.sim._
import scala.util.Random

/** A controller independently negotiates setup, transfers a few beats, and
  * tears down.
  */
class DmaControllerSim(val bus: DmaBus, random: Random) {
    private object State extends Enumeration {
        val Idle, Setup, Transfer, Teardown = Value
    }
    import State._

    private var state          = Idle
    private var delay          = 0
    private var remaining      = 0
    private var write          = false
    private var address        = 0
    private var completedCount = 0

    def completed: Int  = completedCount
    def active: Boolean = state == Transfer || state == Teardown

    def drive(): Unit = {
        bus.setup.setup #= state == Setup
        bus.setup.teardown #= state == Teardown
        bus.setup.write #= write
        bus.setup.addr #= address
        bus.wdata.valid #= state == Transfer && write && delay == 0
        bus.wdata.payload #= 0
        bus.rdata.ready #= state == Transfer && !write && delay == 0
    }

    def sample(): Unit = state match {
        case Idle     =>
            if (delay > 0) delay -= 1
            else {
                write = completed % 2 == 0
                address = random.nextInt(65536)
                remaining = 1 + random.nextInt(8)
                state = Setup
            }
        case Setup    =>
            if (bus.setup.setupReady.toBoolean) {
                delay = random.nextInt(3)
                state = Transfer
            }
        case Transfer =>
            if (delay > 0) delay -= 1
            else if (
                if (write) bus.wdata.ready.toBoolean
                else bus.rdata.valid.toBoolean
            ) {
                remaining -= 1
                delay = random.nextInt(2)
                if (remaining == 0) state = Teardown
            }
        case Teardown =>
            if (bus.setup.teardownReady.toBoolean) {
                completedCount += 1
                delay = random.nextInt(10)
                state = Idle
            }
    }
}

/** The target remembers its own active state and independently delays data and
  * teardown.
  */
class DmaTargetSim(val bus: DmaBus, random: Random) {
    private object State extends Enumeration {
        val Idle, Active = Value
    }
    import State._

    private var state         = Idle
    private var setupDelay    = 4
    private var dataDelay     = 0
    private var teardownDelay = 0
    private var write         = false

    def active: Boolean = state == Active

    def drive(): Unit = {
        bus.setup.setupReady #= state == Idle && setupDelay == 0
        bus.setup.teardownReady #= active && teardownDelay == 0
        bus.rdata.valid #= active && !write && dataDelay == 0
        bus.rdata.payload #= 0
        bus.wdata.ready #= active && write && dataDelay == 0
    }

    def sample(): Unit = state match {
        case Idle   =>
            assert(!bus.setup.teardown.toBoolean, "target: teardown while idle")
            if (setupDelay > 0) setupDelay -= 1
            else if (bus.setup.setup.toBoolean) {
                write = bus.setup.write.toBoolean
                dataDelay = random.nextInt(3)
                teardownDelay = random.nextInt(2)
                state = Active
            }
        case Active =>
            assert(!bus.setup.setup.toBoolean, "target: setup while active")
            if (dataDelay > 0) dataDelay -= 1
            if (teardownDelay > 0) teardownDelay -= 1
            if (
                bus.setup.teardown.toBoolean && bus.setup.teardownReady.toBoolean
            ) {
                setupDelay = random.nextInt(3)
                state = Idle
            } else if (
                (bus.rdata.valid.toBoolean && bus.rdata.ready.toBoolean) ||
                (bus.wdata.valid.toBoolean && bus.wdata.ready.toBoolean)
            ) {
                dataDelay = random.nextInt(2)
            }
    }
}

/** Checks exclusive ownership and handshake routing, without assuming an
  * arbitration order.
  */
class DmaMuxMonitor(controllers: Seq[DmaControllerSim], target: DmaTargetSim) {
    private var owner: Option[DmaControllerSim] = None

    def sample(): Unit = {
        val bus      = target.bus
        val granted  = controllers.filter(c =>
            c.bus.setup.setup.toBoolean && c.bus.setup.setupReady.toBoolean
        )
        val released = controllers.filter(c =>
            c.bus.setup.teardown.toBoolean && c.bus.setup.teardownReady.toBoolean
        )
        val setup    = bus.setup.setup.toBoolean && bus.setup.setupReady.toBoolean
        val teardown =
            bus.setup.teardown.toBoolean && bus.setup.teardownReady.toBoolean

        assert(
            granted.size == (if (setup) 1 else 0),
            "setup handshakes disagree"
        )
        assert(
            released.size == (if (teardown) 1 else 0),
            "teardown handshakes disagree"
        )
        assert(
            target.active == owner.isDefined,
            "target active state disagrees with ownership"
        )
        for ((controller, index) <- controllers.zipWithIndex) {
            val c = controller.bus
            assert(
                controller.active == owner.contains(controller),
                s"controller $index active state disagrees"
            )
            if (owner.contains(controller)) {
                assert(
                    c.setup.teardown.toBoolean == bus.setup.teardown.toBoolean,
                    "teardown request misrouted"
                )
                assert(
                    c.setup.teardownReady.toBoolean == bus.setup.teardownReady.toBoolean,
                    "teardown readiness misrouted"
                )
                assert(
                    c.rdata.valid.toBoolean == bus.rdata.valid.toBoolean &&
                        c.rdata.ready.toBoolean == bus.rdata.ready.toBoolean &&
                        c.wdata.valid.toBoolean == bus.wdata.valid.toBoolean &&
                        c.wdata.ready.toBoolean == bus.wdata.ready.toBoolean,
                    "stream handshake misrouted"
                )
            } else {
                assert(
                    !c.rdata.valid.toBoolean && !c.wdata.ready.toBoolean,
                    s"unselected controller $index has data access"
                )
                assert(
                    !c.setup.teardownReady.toBoolean,
                    s"unselected controller $index can tear down"
                )
            }
        }
        if (owner.isEmpty)
            assert(
                !bus.wdata.valid.toBoolean && !bus.rdata.ready.toBoolean,
                "data access without ownership"
            )
        if (teardown) {
            assert(
                owner.contains(released.head),
                "teardown by a different controller"
            )
            owner = None
        }
        if (setup) {
            assert(
                owner.isEmpty && !teardown,
                "setup overlaps another transaction"
            )
            val controller = granted.head
            assert(
                controller.bus.setup.addr.toInt == bus.setup.addr.toInt &&
                    controller.bus.setup.write.toBoolean == bus.setup.write.toBoolean,
                "setup parameters misrouted"
            )
            owner = Some(controller)
        }
    }
}

/** Run with: sbt "runMain sapphire.sim.DmaMuxTest [seed]". */
object DmaMuxTest extends App {
    val seed = args.headOption.map(_.toLong).getOrElse(0xd3a316L)

    Config.sim.compile(DmaMux(3, 16)).doSim(s"DmaMuxTest_$seed") { dut =>
        val random      = new Random(seed)
        val controllers =
            dut.io.controllers.zipWithIndex.map { case (bus, index) =>
                new DmaControllerSim(bus, random)
            }.toSeq
        val target      = new DmaTargetSim(dut.io.target, random)
        val monitor     = new DmaMuxMonitor(controllers, target)

        def drive(): Unit = {
            controllers.foreach(_.drive())
            target.drive()
        }
        drive()
        dut.clockDomain.forkStimulus(period = 10)
        dut.clockDomain.waitSampling()

        // All three controllers repeatedly request access after random delays. A fixed
        // duration permits any controller to be starved; no fairness is asserted.
        for (cycle <- 0 until 10000) {
            drive()
            dut.clockDomain.waitSampling()
            try {
                monitor.sample()
                controllers.foreach(_.sample())
                target.sample()
            } catch {
                case error: AssertionError =>
                    throw new AssertionError(
                        s"seed=$seed cycle=$cycle: ${error.getMessage}",
                        error
                    )
            }
        }
        val completed = controllers.map(_.completed)
        assert(completed.sum > 0, s"seed=$seed: no transactions exercised")
        println(
            s"DMA mux arbitration passed: seed=$seed completed=${completed.mkString(",")}"
        )
    }
}
