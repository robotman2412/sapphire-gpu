package sapphire.sim

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import sapphire.phy.spi._
import sapphire.phy.spi.SpiMaster.ActionType
import sapphire.phy.spi.SpiSettings
import sapphire.phy.spi.SpiMaster.Action
import spinal.lib.sim.StreamMonitor
import spinal.lib.sim.StreamDriver
import scala.util.Random

object SpiMasterTest extends App {
    Config.sim
        .compile(SpiMaster())
        .doSim(this.getClass.getSimpleName) { dut =>
            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            dut.io.bus.rxData.ready #= true

            StreamMonitor(dut.io.bus.rxData, dut.clockDomain) { data =>
                println(s"Received: ${data.toInt}")
            }

            val testData = Seq(0x82, 0x41, 0xff, 0x00, 0x55, 0xaa, 0x55, 0xaa)
            var index    = 0

            StreamDriver(dut.io.bus.action, dut.clockDomain) { data =>
                val hasData = index < testData.length
                if (hasData) {
                    data.atype #= ActionType.SEND_BYTE
                    data.data #= testData(index)
                    data.settings.fullDuplex #= false
                    data.settings.log2Bits #= Random.nextInt(3)
                    data.settings.cpol #= false
                    data.settings.cpha #= true
                    index += 1
                }
                hasData
            }

            // Let it run for a little while
            while (
                index < testData.length
                || (dut.io.bus.action.ready.toBoolean && dut.io.bus.action.valid.toBoolean)
                || dut.io.busy.toBoolean
            ) {
                dut.clockDomain.waitSampling(1)
            }
            dut.clockDomain.waitSampling(5)
        }
}
