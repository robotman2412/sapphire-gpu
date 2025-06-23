package sapphire.sim

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import sapphire.dma._
import sapphire.mem.Ahb3ToDma
import spinal.lib.bus.amba3.ahblite._
import scala.util.Random

case class Ahb3ToDmaDUT(abits: BitCount, dbits: BitCount) extends Component {
    val io = new Bundle {

        /** AHB-lite 3 bus. */
        val ahb = slave port AhbLite3(AhbLite3Config(abits.value, dbits.value))

        /** Arbitrary stall of reading. */
        val readStall = in port Bool()

        /** Arbitrary stall of writing. */
        val writeStall = in port Bool()
    }

    val ahb3ToDma = Ahb3ToDma(io.ahb.config)
    val dma       = DmaBus(abits)
    ahb3ToDma.io.dma <> dma
    ahb3ToDma.io.ahb <> io.ahb

    // Simple dummy implementation of DMA bus.
    val dmaBusy  = RegInit(False)
    val dmaWrite = Reg(Bool())
    dma.setup.setupReady    := !dmaBusy
    dma.setup.teardownReady := dmaBusy
    val dmaAddr = Reg(UInt(abits))
    dma.rdata.payload := dmaAddr.asBits
    dma.rdata.valid   := !dmaWrite && dmaBusy && !io.readStall
    dma.wdata.ready   := dmaWrite && dmaBusy && !io.writeStall
    when(dma.setup.setup && dma.setup.setupReady) {
        dmaAddr  := dma.setup.addr
        dmaBusy  := True
        dmaWrite := dma.setup.write
    } elsewhen (dma.setup.teardown && dma.setup.teardownReady) {
        dmaBusy := False
        dmaAddr.assignDontCare()
    } elsewhen (dma.rdata.fire || dma.wdata.fire) {
        dmaAddr := dmaAddr + 1
    }
}

object Ahb3ToDmaTest extends App {
    Config.sim
        .compile(Ahb3ToDmaDUT(8 bits, 32 bits))
        .doSim(this.getClass.getSimpleName) { dut =>
            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            dut.io.readStall #= false
            dut.io.writeStall #= false
            dut.io.ahb.HREADY #= false
            dut.clockDomain.waitSampling()

            // Do alternating random read and write with random delay in-between.
            for (i <- 0 until 4) {
                val isWrite = (i & 1) == 0
                dut.io.ahb.HREADY #= true
                dut.io.ahb.HTRANS #= 2 // NONSEQ
                dut.io.ahb.HWRITE #= isWrite
                dut.io.ahb.HSIZE #= Random.nextInt(3)
                dut.io.ahb.HADDR.randomize()
                dut.io.ahb.HWDATA.randomize()

                do {
                    dut.clockDomain.waitSampling()
                } while (!dut.io.ahb.HREADYOUT.toBoolean)
                dut.io.ahb.HREADY #= false
                dut.io.ahb.HTRANS.randomize()
                dut.io.ahb.HWRITE.randomize()
                dut.io.ahb.HSIZE.randomize()
                dut.io.ahb.HADDR.randomize()
                dut.io.ahb.HWDATA.randomize()

                dut.clockDomain.waitSampling(Random.nextInt(6))
            }

            dut.clockDomain.waitSampling(10)

            // Do a stream read.
            val haddr = Random.nextInt(256)
            val hsize = Random.nextInt(3)
            val count = Random.nextInt(4) + 4
            for (i <- 0 until count) {
                dut.io.ahb.HREADY #= true
                dut.io.ahb.HTRANS #= 3 // SEQ
                dut.io.ahb.HWRITE #= false
                dut.io.ahb.HSIZE #= hsize
                dut.io.ahb.HADDR #= (haddr + (i << hsize)) & 255
                dut.io.ahb.HWDATA.randomize()

                do {
                    dut.clockDomain.waitSampling()
                } while (!dut.io.ahb.HREADYOUT.toBoolean)
            }
            dut.io.ahb.HREADY #= false
            dut.io.ahb.HTRANS.randomize()
            dut.io.ahb.HWRITE.randomize()
            dut.io.ahb.HSIZE.randomize()
            dut.io.ahb.HADDR.randomize()
            dut.io.ahb.HWDATA.randomize()

            dut.clockDomain.waitSampling(10)

        }
}
