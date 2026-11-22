package sapphire.sim

import sapphire._
import sapphire.render._
import spinal.core.sim._

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

object IntInterpolatorTest extends App {
    val failures = Seq(3, 4, 8).flatMap { numAdders =>
        try {
            Config.sim
                .compile(IntInterpolator(InterpolatorConfig(8, numAdders, 8, 8)))
                .doSim(s"IntInterpolatorTest_${numAdders}adders")(basicTest _)
            None
        } catch {
            case error: AssertionError => Some(new AssertionError(s"$numAdders adders: ${error.getMessage}", error))
        }
    }
    if (failures.nonEmpty) {
        val error = new AssertionError(s"${failures.size} interpolator configurations failed")
        failures.foreach(error.addSuppressed)
        throw error
    }

    def basicTest(dut: IntInterpolator): Unit = {
        val io = dut.io
        val mask = (BigInt(1) << dut.cfg.dataBits) - 1
        val start = Vector[BigInt](0, 250, 1, 255, 42, 128, 7, 99)
        val step = Vector[BigInt](1, 9, -3, 1, 0, 128, -1, 17)
        val restart = start.reverse

        io.cancel #= false
        io.din.valid #= false
        io.dout.ready #= false
        dut.clockDomain.forkStimulus(period = 10)

        def tick(): Unit = dut.clockDomain.waitSampling()
        def waitFor(description: String)(condition: => Boolean): Unit = {
            sleep(1)
            var waited = 0
            while (!condition && waited < dut.cyclePerAdd + 2) {
                tick()
                sleep(1)
                waited += 1
            }
            assert(condition, s"Timed out waiting for $description")
        }

        def assertIdle(): Unit = {
            for (_ <- 0 until dut.cyclePerAdd + 2) {
                sleep(1)
                assert(!io.dout.valid.toBoolean, "Unexpected output while idle")
                assert(io.din.ready.toBoolean, "Idle interpolator did not accept input")
                tick()
            }
        }

        def trigger(origin: Vector[BigInt], increments: Int): Unit = {
            io.din.cycles #= increments
            for (i <- origin.indices) {
                io.din.start(i) #= origin(i)
                io.din.step(i) #= (step(i) & mask)
            }
            io.din.valid #= true
            waitFor("input ready")(io.din.ready.toBoolean)
            tick()
            io.din.valid #= false
        }

        def checkPoint(origin: Vector[BigInt], index: Int, stall: Int = 0): Unit = {
            io.dout.ready #= false
            waitFor(s"point $index")(io.dout.valid.toBoolean)
            val expected = origin.zip(step).map { case (coord, delta) => (coord + delta * index) & mask }

            def checkPayload(): Unit = {
                assert(io.dout.valid.toBoolean, s"Output valid dropped at point $index")
                val actual = origin.indices.map(i => io.dout.payload(i).toBigInt)
                assert(actual == expected, s"Point $index: expected $expected, got $actual")
            }

            checkPayload()
            for (_ <- 0 until stall) {
                tick()
                sleep(1)
                checkPayload()
            }
            io.dout.ready #= true
            tick()
            io.dout.ready #= false
        }

        def cancel(): Unit = {
            io.din.valid #= false
            io.cancel #= true
            tick()
            assertIdle()
            io.cancel #= false
            assertIdle()
            trigger(restart, increments = 1)
            checkPoint(restart, 0)
            checkPoint(restart, 1)
            assertIdle()
        }

        tick()
        assertIdle()
        cancel()

        // A zero increment count still emits the starting point.
        trigger(start, increments = 0)
        checkPoint(start, 0)
        assertIdle()

        trigger(start, increments = 5)
        for (index <- 0 to 5) checkPoint(start, index, stall = if (index == 2) 3 else 0)
        assertIdle()

        // Cancel while the first point is held by downstream backpressure.
        trigger(start, increments = 5)
        waitFor("stalled output")(io.dout.valid.toBoolean)
        tick()
        cancel()

        // Cancel after a transfer, at every intermediate shared-adder cycle.
        for (elapsed <- 0 until (dut.cyclePerAdd - 1).max(1)) {
            trigger(start, increments = 5)
            checkPoint(start, 0)
            for (_ <- 0 until elapsed) tick()
            cancel()
        }
    }
}
