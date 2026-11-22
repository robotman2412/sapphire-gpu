package sapphire.sim

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.mem._
import spinal.core._
import spinal.core.sim._
import spinal.lib._

case class ExtIo() extends Bundle with IMasterSlave {
    val a = Bool()
    val b = Bool()
    val q = Bool()

    override def asMaster(): Unit = {
        in(a, b)
        out(q)
    }
}

case class ExposedIoInner() extends Component {
    val io = new Bundle {
        val ext = master port ExtIo()
    }
    io.ext.q := io.ext.a & io.ext.b
}

case class ExposedIoOuter() extends Component {
    val inner = ExposedIoInner()

    val io = new Bundle {
        val ext = master(
            inner.io.ext.clone().asInstanceOf[ExtIo]
        )
    }

    io.ext <> inner.io.ext
}

object ExposedIoTest extends App {
    Config.sim
        .compile(ExposedIoOuter())
        .doSim(this.getClass.getSimpleName) { dut =>
            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            dut.io.ext.a #= false
            dut.io.ext.b #= false
            dut.clockDomain.waitSampling()

            dut.io.ext.a #= true
            dut.io.ext.b #= false
            dut.clockDomain.waitSampling()

            dut.io.ext.a #= false
            dut.io.ext.b #= true
            dut.clockDomain.waitSampling()

            dut.io.ext.a #= true
            dut.io.ext.b #= true
            dut.clockDomain.waitSampling()
        }
}
