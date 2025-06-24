package sapphire.sim

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import sapphire._
import sapphire.phy.spi.SimpleSpiSlave
import spinal.core._
import spinal.core.sim._

object SimpleSpiSlaveTest extends App {
    Config.sim
        .compile(SimpleSpiSlave())
        .doSim(this.getClass.getSimpleName) { dut =>
            dut.io.chipSelect #= false
            dut.io.sclk #= false

            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            // Some test data to be sent over SPI.
            val mosiData      =
                Seq(0x82, 0x41, 0xff, 0x00, 0xaa, 0x55, 0xf0, 0x0d, 0xba, 0xbe)
            val misoData      =
                Seq(0xca, 0xfe, 0xba, 0xbe, 0xaa, 0x55, 0x82, 0x41, 0xff, 0x00)
            var misoDataIndex = 0
            dut.io.txd.payload #= misoData(0)

            dut.clockDomain.onSamplings {
                // Print all SPI recv data.
                if (dut.io.rxd.valid.toBoolean) {
                    println(
                        "MOSI stream: 0x%02x".format(dut.io.rxd.payload.toInt)
                    )
                }

                // Serve SPI send data.
                if (dut.io.txd.ready.toBoolean) {
                    misoDataIndex += 1
                    dut.clockDomain.onNextSampling {
                        if (misoDataIndex < misoData.length) {
                            dut.io.txd.payload #= misoData(misoDataIndex)
                        } else {
                            dut.io.txd.payload.randomize()
                        }
                    }
                }
            }

            dut.clockDomain.waitSampling(2)
            dut.io.chipSelect #= true
            dut.clockDomain.waitSampling(2)

            for (byte <- mosiData) {
                var rxd = 0
                for (bit <- 0 until 8) {
                    dut.io.mosi #= ((byte << bit) & 0x80) != 0
                    dut.clockDomain.waitSampling(2)
                    dut.io.sclk #= true
                    rxd = (rxd << 1) | dut.io.miso.toBoolean.toInt
                    dut.clockDomain.waitSampling(2)
                    dut.io.sclk #= false
                }
                println("MISO wire: 0x%02x".format(rxd))
            }
        }
}
