package sapphire.sim

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import sapphire._
import sapphire.interface.cmd.CmdEngine
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import sapphire.util.Vacuum
import sapphire.dma.DmaBus

case class CmdEngineDut(cfg: SapphireCfg) extends Component {
    val io = new Bundle {

        /** Active-high chip select input. */
        val chipSelect = in port Bool()

        /** Serial receive data. */
        val rxd = slave port Flow(Bits(8 bits))

        /** Serial transmit data. */
        val txd = master port Vacuum(Bits(8 bits))

        /** Interrupt output. */
        val irqOut = out port Bool()

        /** Internal interrupt inputs. */
        val irqIn = in port Bits(32 bits)
    }

    val cmdEngine = CmdEngine(cfg)
    val dma       = DmaBus(cfg.vaddrBits bits)
    cmdEngine.io.dma <> dma
    cmdEngine.io.rxd << io.rxd
    cmdEngine.io.txd >> io.txd
    cmdEngine.io.chipSelect := io.chipSelect
    io.irqOut               := cmdEngine.io.irqOut
    cmdEngine.io.irqIn      := io.irqIn

    // Stub debug bus: return the requested index as the register value.
    cmdEngine.io.debug.data := cmdEngine.io.debug.index.asBits.resized

    // Simple dummy implementation of DMA bus.
    val dmaBusy  = RegInit(False)
    val dmaWrite = Reg(Bool())
    dma.setup.setupReady    := !dmaBusy
    dma.setup.teardownReady := dmaBusy
    val dmaAddr = Reg(UInt(cfg.vaddrBits bits))
    dma.rdata.payload := dmaAddr.asBits.resized
    dma.rdata.valid   := !dmaWrite && dmaBusy
    dma.wdata.ready   := dmaWrite && dmaBusy
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

object CmdEngineTest extends App {
    Config.sim
        .compile(CmdEngineDut(SapphireCfg(0x100000000L)))
        .doSim(this.getClass.getSimpleName) { dut =>
            dut.io.chipSelect #= false
            dut.io.rxd.valid #= false
            dut.io.txd.ready #= false
            dut.io.irqIn #= 0

            // Fork a process to generate the reset and the clock on the dut
            dut.clockDomain.forkStimulus(period = 10)

            def runCmd(data: Seq[Int]) = {
                dut.clockDomain.waitSampling()
                dut.io.chipSelect #= true
                // dut.clockDomain.waitSampling(2)
                dut.io.rxd.valid #= true
                for (byte <- data) {
                    dut.io.rxd.payload #= byte
                    dut.clockDomain.waitSampling()
                }
                dut.io.rxd.valid #= false
                dut.io.rxd.payload.randomize()
                dut.io.chipSelect #= false
                dut.clockDomain.waitSampling()
            }

            def getResp(len: Int) = {
                dut.clockDomain.waitSampling()
                dut.io.chipSelect #= true
                // dut.clockDomain.waitSampling(2)
                dut.io.txd.ready #= true
                val resp = for (_ <- 0 until len) yield {
                    dut.clockDomain.waitSampling()
                    dut.io.txd.payload.toInt
                }
                dut.io.txd.ready #= false
                dut.io.chipSelect #= false
                dut.clockDomain.waitSampling()
                resp
            }

            // DESC
            runCmd(Seq(0x02))
            val desc = getResp(32)
            println("Ver: %d.%d.%d".format(desc(0), desc(1), desc(2)))
            println("#Scanout: %d".format(desc(3)))
            println(
                "Irq impl: 0x%02x%02x%02x%02x"
                    .format(desc(7), desc(6), desc(5), desc(4))
            )
            println(
                "Ram size: 0x%02x%02x%02x%02x%02x%02x%02x%02x"
                    .format(
                        desc(15),
                        desc(14),
                        desc(13),
                        desc(12),
                        desc(11),
                        desc(10),
                        desc(9),
                        desc(8)
                    )
            )
            println(
                "Reqd feat: 0x%02x%02x%02x%02x"
                    .format(desc(19), desc(18), desc(17), desc(16))
            )
            println(
                "Opt feat: 0x%02x%02x%02x%02x"
                    .format(desc(23), desc(22), desc(21), desc(20))
            )
            println("#Coord: %d".format(desc(24)))
            // IRQ ENABLE: 0x00000003
            runCmd(Seq(0x04, 0x03, 0x00, 0x00, 0x00))
            // WRITE DMA: 0xf00dbabe
            runCmd(Seq(0x0a, 0xbe, 0xba, 0x0d, 0xf0))
            // Wait for interrupt.
            dut.clockDomain.waitSamplingWhere(dut.io.irqOut.toBoolean)
            // IRQ CLEAR: 0x00000003
            runCmd(Seq(0x03, 0x03, 0x00, 0x00, 0x00))
            val irq  = getResp(4)
            println(
                "Irq status: 0x%02x%02x%02x%02x"
                    .format(irq(3), irq(2), irq(1), irq(0))
            )
            // WRITE PAYLOAD: {0x01, 0x02, 0x03, 0x04, 0x05}
            runCmd(Seq(0x0b, 0x01, 0x02, 0x03, 0x04, 0x05))
            dut.clockDomain.waitSampling(10)
        }
}
