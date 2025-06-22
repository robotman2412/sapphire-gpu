package sapphire.sim

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import sapphire.dma._
import sapphire.phy.spi._
import spinal.lib.sim.StreamMonitor
import spinal.lib.sim.StreamDriver

case class SpiMemDUT(abits: BitCount) extends Component {
    val io = new Bundle {

        /** Enable; while false, will instantly stop transfer and never initiate
          * transfer.
          */
        val enable = in port Bool()

        /** DMA slave port. */
        val dma = slave port DmaBus(abits)

        /** SPI memory access settings. */
        val settings = in port SpiMemSettings()

        /** Assert memory's chip select (active high). */
        val chipSelect = out port Bool()

        /** Explicit SPI clock output that follows CPOL and CPHA rules. */
        val sclk = out port Bool()

        /** Data output pins. */
        val mosi = out port Bits(4 bits)

        /** Output enables. */
        val mosiEn = out port Bits(4 bits)

        /** Data input pins. */
        val miso = in port Bits(8 bits)
    }

    val memctl = SpiMemCtrl(abits)
    val phy    = SpiMaster()
    memctl.io.spi <> phy.io.bus

    memctl.io.enable   := io.enable
    memctl.io.dma <> io.dma
    memctl.io.settings := io.settings
    io.chipSelect      := memctl.io.chipSelect

    io.sclk     := phy.io.sclk
    io.mosi     := phy.io.mosi
    io.mosiEn   := phy.io.mosiEn
    phy.io.miso := io.miso
}

object SpiMemTest extends App {
    Config.sim
        .compile(SpiMemDUT(32 bits))
        .doSim(this.getClass.getSimpleName) { dut =>
            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            StreamMonitor(dut.io.dma.rdata, dut.clockDomain) { data =>
                println(s"Received: ${data.toInt}")
            }

            val wdata  = Seq(0xf0, 0x0d, 0xba, 0xbe)
            var windex = 0
            StreamDriver(dut.io.dma.wdata, dut.clockDomain) { data =>
                val hasData = windex < wdata.length
                if (hasData) {
                    data #= wdata(windex)
                    windex += 1
                }
                hasData
            }

            // Reset triggering signals.
            dut.io.dma.setup.setup #= false
            dut.io.dma.setup.teardown #= false
            dut.io.dma.rdata.ready #= false

            // Enable the controller
            dut.io.enable #= true

            // Enter SPI settings for command.
            dut.io.settings.cmdSettings.fullDuplex #= false
            dut.io.settings.cmdSettings.log2Bits #= 2
            dut.io.settings.cmdSettings.cpol #= false
            dut.io.settings.cmdSettings.cpha #= false

            // Enter SPI settings for address / data.
            dut.io.settings.dataSettings.fullDuplex #= false
            dut.io.settings.dataSettings.log2Bits #= 2
            dut.io.settings.dataSettings.cpol #= false
            dut.io.settings.dataSettings.cpha #= false

            // Enter SPI memory access parameters.
            dut.io.settings.preAddrCycles #= 0
            dut.io.settings.addrLen #= 3
            dut.io.settings.writeCmd #= 0x38
            dut.io.settings.readCmd #= 0xeb
            dut.io.settings.preAddrCycles #= 0
            dut.io.settings.preReadCycles #= 6
            dut.io.settings.preWriteCycles #= 0
            dut.io.settings.csResetCycles #= 2

            // Issue write DMA setup.
            dut.io.dma.setup.setup #= true
            dut.io.dma.setup.write #= true
            dut.io.dma.setup.addr #= 0x332211
            dut.clockDomain.waitSampling()
            while (!dut.io.dma.setup.setupReady.toBoolean) {
                dut.clockDomain.waitSampling()
            }
            dut.io.dma.setup.setup #= false

            // Wait for all data to be written.
            while (windex < wdata.length) {
                dut.clockDomain.waitSampling()
            }

            // Issue DMA teardown.
            dut.io.dma.setup.teardown #= true
            dut.clockDomain.waitSampling()
            while (!dut.io.dma.setup.teardownReady.toBoolean) {
                dut.clockDomain.waitSampling()
            }
            dut.io.dma.setup.teardown #= false

            // Issue read DMA setup.
            dut.io.dma.setup.setup #= true
            dut.io.dma.setup.write #= false
            dut.io.dma.setup.addr #= 0x665544
            dut.clockDomain.waitSampling()
            while (!dut.io.dma.setup.setupReady.toBoolean) {
                dut.clockDomain.waitSampling()
            }
            dut.io.dma.setup.setup #= false

            // Delay for a bit.
            dut.clockDomain.waitSampling(20)

            // Accept data beyond SPI throughput.
            dut.io.dma.rdata.ready #= true
            dut.clockDomain.waitSampling(32)
            dut.io.dma.rdata.ready #= false

            // Accept data at exactly SPI throughput.
            for (i <- 0 until 16) {
                dut.io.dma.rdata.ready #= false
                dut.clockDomain.waitSampling()
                dut.io.dma.rdata.ready #= true
                dut.clockDomain.waitSampling()
            }
            dut.io.dma.rdata.ready #= false

            // Issue DMA teardown.
            dut.io.dma.setup.teardown #= true
            dut.clockDomain.waitSampling()
            while (!dut.io.dma.setup.teardownReady.toBoolean) {
                dut.clockDomain.waitSampling()
            }
            dut.io.dma.setup.teardown #= false

            dut.clockDomain.waitSampling(20)
        }
}
