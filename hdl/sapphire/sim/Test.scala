package sapphire.sim

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import spinal.core._
import spinal.core.sim._
import spinal.lib._

case class TestBench() extends Component {
    val io = new Bundle {
        val din  = in  port Bool()
        val dout = out port Bool()
    }
    val reg = RegInit(False)
    reg := io.din
    io.dout := reg
}

object Test extends App {
    Config.sim.compile(TestBench()).doSim(this.getClass.getSimpleName) { dut =>
        // Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 10)
        
        // Let it run for a little while
        for (i <- 0 until 10) {
            dut.io.din #= ((i & 1) == 0)
            dut.clockDomain.waitSampling(1)
        }
    }
}