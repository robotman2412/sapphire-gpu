package sapphire.sim

// Copyright (c) 2025-2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.dma._
import sapphire.interface.cmd._
import sapphire.util._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.apb._

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

        /** Driveable debug taps, exposed at debug register indices 0 and 1. */
        val dbgTap0 = in port Bits(32 bits)
        val dbgTap1 = in port Bits(8 bits)

        val regRw = out port Bits(32 bits)
        val regRo = in port Bits(32 bits)
    }

    val cmdEngine = CmdEngine(cfg)
    val dma       = DmaBus(cfg.vaddrBits bits)
    cmdEngine.io.dma <> dma
    cmdEngine.io.rxd << io.rxd
    cmdEngine.io.txd >> io.txd
    cmdEngine.io.chipSelect := io.chipSelect
    io.irqOut               := cmdEngine.io.irqOut
    cmdEngine.io.irqIn      := io.irqIn

    // Control registers APB bus.
    val apb     = Apb3(16, 32)
    cmdEngine.io.apb <> apb
    val apbRegs = Apb3SlaveFactory(apb)
    val regRw   = Reg(Bits(32 bits))
    io.regRw := regRw
    apbRegs.driveAndRead(regRw, address = 1)
    apbRegs.read(io.regRo, address = 6)

    // Real debug register file so the latch behaviour can be exercised.
    val debugRegs = DebugRegFile(Seq(32 bits, 8 bits))
    debugRegs.io.regs(0) := io.dbgTap0
    debugRegs.io.regs(1) := io.dbgTap1
    cmdEngine.io.debug <> debugRegs.io.debug

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
            dut.io.txd.peek #= false
            dut.io.txd.ready #= false
            dut.io.irqIn #= 0
            dut.io.dbgTap0 #= 0
            dut.io.dbgTap1 #= 0
            dut.io.regRo #= 0

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
                dut.io.txd.peek #= true
                val resp = for (_ <- 0 until len) yield {
                    dut.clockDomain.waitSampling()
                    dut.io.txd.payload.toInt
                }
                dut.io.txd.peek #= false
                dut.io.txd.ready #= false
                dut.io.chipSelect #= false
                dut.clockDomain.waitSampling()
                resp
            }

            // IOREAD
            dut.io.regRo #= 0xcafebabeL;
            runCmd(Seq(15, 0x06, 0x00))
            dut.clockDomain.waitSampling(
                2
            ) // The command interface is very fast; give APB time to catch up.
            val regRo = getResp(4)
            println(
                "IOREAD: 0x%02x%02x%02x%02x"
                    .format(regRo(3), regRo(2), regRo(1), regRo(0))
            )
            assert(
                regRo(3) == 0xca && regRo(2) == 0xfe
                    && regRo(1) == 0xba && regRo(0) == 0xbe
            )

            // IOWRITE
            runCmd(Seq(16, 0x01, 0x00, 0x11, 0x22, 0x33, 0x44))
            dut.clockDomain.waitSampling(
                4
            ) // The command interface is very fast; give APB time to catch up.
            println("IOWRITE: 0x%08x".format(dut.io.regRw.toInt))
            assert(dut.io.regRw.toInt == 0x44332211)

            runCmd(Seq(16, 0x01, 0x00, 0xde, 0xc0, 0xd0, 0xba))
            dut.clockDomain.waitSampling(
                4
            ) // The command interface is very fast; give APB time to catch up.
            println("IOWRITE: 0x%08x".format(dut.io.regRw.toInt))
            assert(dut.io.regRw.toInt == 0xbad0c0de)

            // DESC
            runCmd(Seq(2))
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

            // ---- Vacuum peek/commit contract (split-read fix) --------------
            // The dummy DMA streams an incrementing byte (low 8 bits of addr).
            // `peek` must make data valid without consuming it; only `ready`
            // (commit) advances the stream. This is what lets a split read keep
            // a prefetched-but-unsent byte instead of dropping it.
            runCmd(Seq(0x08, 0x40, 0x00, 0x00, 0x00)) // READ DMA @ 0x40
            runCmd(Seq(0x09))                         // READ PAYLOAD -> prevCmd = 9

            dut.clockDomain.waitSampling()
            dut.io.chipSelect #= true
            dut.io.txd.peek #= true
            dut.io.txd.ready #= false
            dut.clockDomain.waitSampling(3)
            val held     = dut.io.txd.payload.toInt
            dut.clockDomain.waitSampling(3)
            assert(
                dut.io.txd.payload.toInt == held,
                "peek must not advance the read stream (would drop a byte)"
            )
            // Commit advances the stream by exactly one.
            dut.io.txd.ready #= true
            dut.clockDomain.waitSampling()
            dut.io.txd.peek #= false
            dut.io.txd.ready #= false
            dut.clockDomain.waitSampling(2)
            val advanced = dut.io.txd.payload.toInt
            assert(
                advanced == ((held + 1) & 0xff),
                "commit must advance the read stream by one"
            )
            println(
                "Peek held 0x%02x, commit advanced to 0x%02x"
                    .format(held, advanced)
            )
            dut.io.chipSelect #= false
            dut.clockDomain.waitSampling()
            runCmd(Seq(14)) // DMA TEARDOWN

            // IRQ ENABLE: 0x00000003
            runCmd(Seq(4, 0x03, 0x00, 0x00, 0x00))
            // WRITE DMA: 0xf00dbabe
            runCmd(Seq(10, 0xbe, 0xba, 0x0d, 0xf0))
            // Wait for interrupt.
            dut.clockDomain.waitSamplingWhere(dut.io.irqOut.toBoolean)
            // IRQ CLEAR: 0x00000003
            runCmd(Seq(3, 0x03, 0x00, 0x00, 0x00))
            val irq = getResp(4)
            println(
                "Irq status: 0x%02x%02x%02x%02x"
                    .format(irq(3), irq(2), irq(1), irq(0))
            )
            // WRITE PAYLOAD: {0x01, 0x02, 0x03, 0x04, 0x05}
            runCmd(Seq(0x0b, 0x01, 0x02, 0x03, 0x04, 0x05))
            dut.clockDomain.waitSampling(10)

            // ---- Debug latch feature ---------------------------------------

            // Read 32-bit debug register `reg` (DEBUG READ command).
            def readDbg(reg: Int): Long = {
                runCmd(Seq(0x0c, reg & 0xff, (reg >> 8) & 0xff))
                val r = getResp(4)
                (r(0).toLong | (r(1).toLong << 8) |
                    (r(2).toLong << 16) | (r(3).toLong << 24)) & 0xffffffffL
            }
            // Select debug latch triggers (DEBUG TRIGGERS command).
            def setTriggers(mask: Int)  = runCmd(Seq(0x0d, mask & 0xff))

            // Default trigger is DBGCMD: a DEBUG READ snapshots the live taps,
            // so the read returns whatever the tap held at command time.
            dut.io.dbgTap0 #= 0x11223344L
            dut.clockDomain.waitSampling(2)
            val a = readDbg(0)
            println("Latch A (DBGCMD): 0x%08x".format(a))
            assert(
                a == 0x11223344L,
                "DBGCMD should snapshot the current tap value"
            )

            // Disable all triggers: the snapshot must now freeze. Changing the
            // tap and reading again must still return the previously latched value.
            setTriggers(0x00)
            dut.io.dbgTap0 #= 0x55667788L
            dut.clockDomain.waitSampling(2)
            val b = readDbg(0)
            println("Latch B (frozen): 0x%08x".format(b))
            assert(
                b == 0x11223344L,
                "with no triggers the snapshot must stay frozen"
            )

            // Event trigger: snapshot must update when the selected event fires
            // and then stay frozen across the subsequent DEBUG READ. CMDBYTE is
            // used here (deterministic); every event shares the same combine
            // logic (events & latchTriggers), so this also covers DMABYTE/DMAERR/IRQ.
            setTriggers(0x02) // SAPPHIRE_DBG_LATCH_CMDBYTE
            dut.io.dbgTap0 #= 0x99aabbccL
            dut.clockDomain.waitSampling(2)
            // A NOP is a non-DEBUG command byte, so it latches the current tap.
            runCmd(Seq(0))
            // Change the tap afterwards; the frozen value must survive (the
            // following DEBUG READ is command 12, which CMDBYTE does not match).
            dut.io.dbgTap0 #= 0x0badf00dL
            dut.clockDomain.waitSampling(2)
            val c = readDbg(0)
            println("Latch C (CMDBYTE): 0x%08x".format(c))
            assert(
                c == 0x99aabbccL,
                "CMDBYTE should latch the tap when a command byte is read"
            )

            println("Debug latch tests passed.")
            dut.clockDomain.waitSampling(10)
        }
}
