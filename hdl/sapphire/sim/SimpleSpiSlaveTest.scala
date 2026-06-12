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
            dut.io.mosi #= false

            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            // Some test data to be sent over SPI.
            val mosiData =
                Seq(0x82, 0x41, 0xff, 0x00, 0xaa, 0x55, 0xf0, 0x0d, 0xba, 0xbe)
            val misoData =
                Seq(0xca, 0xfe, 0xba, 0xbe, 0xaa, 0x55, 0x82, 0x41, 0xff, 0x00)
            var misoDataIndex = 0
            dut.io.txd.payload #= misoData(0)

            val mosiRecv = scala.collection.mutable.ArrayBuffer[Int]()

            dut.clockDomain.onSamplings {
                // Collect all SPI recv data.
                if (dut.io.rxd.valid.toBoolean) {
                    mosiRecv += dut.io.rxd.payload.toInt
                }

                // Serve SPI send data: present misoData[index], advance only on a
                // commit (`ready`). A `peek` (prefetch without commit) must NOT
                // advance the index, otherwise a byte is dropped on a split read.
                if (dut.io.txd.ready.toBoolean) {
                    misoDataIndex += 1
                    val idx = misoDataIndex
                    dut.clockDomain.onNextSampling {
                        dut.io.txd.payload #= (if (idx < misoData.length) misoData(idx) else 0)
                    }
                }
            }

            // Half-period of the emulated SPI clock, in system clock cycles. The
            // real SPI clock is far slower than the FPGA clock, so this must be
            // comfortably larger than the input synchronizer depth for the slave
            // to settle each bit before it is sampled.
            val halfPeriod = 8

            // Clock one SPI byte: drive `mosiByte` and return the received MISO byte.
            def clockByte(mosiByte: Int): Int = {
                var rxd = 0
                for (bit <- 0 until 8) {
                    // Master drives MOSI while the clock is low (CPHA=0)...
                    dut.io.mosi #= ((mosiByte << bit) & 0x80) != 0
                    dut.clockDomain.waitSampling(halfPeriod)
                    // ...and samples MISO on the rising edge.
                    dut.io.sclk #= true
                    rxd = (rxd << 1) | dut.io.miso.toBoolean.toInt
                    dut.clockDomain.waitSampling(halfPeriod)
                    dut.io.sclk #= false
                }
                rxd
            }

            // --- Scenario 1: one continuous transaction transfers all bytes. ---
            misoDataIndex = 0
            dut.io.txd.payload #= misoData(0)
            dut.clockDomain.waitSampling(halfPeriod)
            dut.io.chipSelect #= true
            dut.clockDomain.waitSampling(halfPeriod)
            val cont = for (byte <- mosiData) yield clockByte(byte)
            dut.io.chipSelect #= false
            dut.clockDomain.waitSampling(halfPeriod)
            println("Continuous MISO: " + cont.map("0x%02x".format(_)).mkString(" "))
            assert(cont == misoData, "continuous read returned wrong MISO data")
            assert(
                mosiRecv.toSeq == mosiData,
                "continuous write received wrong MOSI data"
            )

            // --- Scenario 2: a split read must not drop a byte at the boundary. ---
            // Read the first six MISO bytes across two separate chip-select
            // transactions (3 + 3). Before the fix, the byte prefetched at the end
            // of transaction A was consumed but never clocked out, so transaction B
            // resumed one byte too far.
            misoDataIndex = 0
            dut.io.txd.payload #= misoData(0)
            dut.clockDomain.waitSampling(halfPeriod)
            val split = scala.collection.mutable.ArrayBuffer[Int]()

            // Transaction A: first three bytes.
            dut.io.chipSelect #= true
            dut.clockDomain.waitSampling(halfPeriod)
            for (i <- 0 until 3) split += clockByte(mosiData(i))
            dut.io.chipSelect #= false
            dut.clockDomain.waitSampling(halfPeriod)

            // Transaction B: next three bytes; must continue where A left off.
            dut.io.chipSelect #= true
            dut.clockDomain.waitSampling(halfPeriod)
            for (i <- 3 until 6) split += clockByte(mosiData(i))
            dut.io.chipSelect #= false
            dut.clockDomain.waitSampling(halfPeriod)

            println("Split MISO: " + split.map("0x%02x".format(_)).mkString(" "))
            assert(
                split.toSeq == misoData.take(6),
                "split read dropped or misaligned a byte"
            )

            println("SimpleSpiSlave tests passed.")
        }
}
