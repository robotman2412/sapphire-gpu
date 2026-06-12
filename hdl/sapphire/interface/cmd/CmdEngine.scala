package sapphire.interface.cmd

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import sapphire._
import sapphire.util._
import sapphire.dma._
import scala.collection.mutable
import spinal.core._
import spinal.lib._

/** Serial interface command engine. */
case class CmdEngine(cfg: SapphireCfg) extends Component {
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

        /** GPU memory DMA bus. */
        val dma = master port DmaBus(cfg.vaddrBits bits)

        /** Debug register read bus. */
        val debug = master port DebugBus()
    }

    val pChipSelect       = RegNext(io.chipSelect)
    val chipSelectFalling = !io.chipSelect && pChipSelect

    // Default debug bus drive; overridden by the DEBUG READ command.
    io.debug.index := U(0).resized

    /** DMA is currently set up. */
    val isDmaSetup = RegInit(False)

    // DMA setup and teardown logic.
    io.dma.setup.setup.setAsReg().init(False)
    io.dma.setup.addr.setAsReg()
    io.dma.setup.write.setAsReg()
    io.dma.setup.teardown.setAsReg().init(False)
    when(io.dma.setup.setup && io.dma.setup.setupReady) {
        io.dma.setup.setup := False
        isDmaSetup         := True
    }
    when(io.dma.setup.teardown && io.dma.setup.teardownReady) {
        io.dma.setup.teardown := False
        isDmaSetup            := False
    }

    /** Set of commands to implement. */
    private var commands = mutable.Map[Int, (HardType[_], Bits => Bits)]()

    /** Helper for adding commands that take and return fixed data. */
    private def addCommand[P <: Data, R <: Data](
        code: Int,
        paramType: HardType[P] = null
    )(function: P => R): Unit = {
        commands(code) = (
            paramType,
            data => {
                var params = null.asInstanceOf[P]
                if (paramType != null) {
                    params = paramType()
                    params.assignFromBits(data.resize(params.getBitsWidth bits))
                }
                val tmp    = function(params)
                if (tmp != null) tmp.asBits else null
            }
        )
    }

    /** Interrupt status register. */
    val irqStatus = RegInit(B(0, 32 bits))

    /** Interrupt enable register. */
    val irqEnable = RegInit(B(0, 32 bits))
    io.irqOut := (irqStatus & irqEnable) =/= 0

    /** Whether the receive data fired last cycle. */
    val pRxdValid = RegNext(io.rxd.valid, False)

    /** Assert dma_ready interrupt when the DMA bus becomes ready. */
    val irqOnDmaReady = RegInit(False)
    when(
        irqOnDmaReady && !io.dma.setup.setup && (io.dma.rdata.valid || io.dma.wdata.ready)
    ) {
        irqOnDmaReady := False
        irqStatus(0)  := True // dma_ready interrupt.
    }

    // --- Debug latch triggers -------------------------------------------
    // Each event Bool pulses for one cycle when its condition occurs. The
    // enabled subset (latchTriggers) drives io.debug.latch, which snapshots
    // the whole debug register file. The bit order of the combined vector
    // matches the SAPPHIRE_DBG_LATCH_* macros in the C driver.

    /** DEBUG READ command byte was received. */
    val evtDbgCmd = Bool()

    /** A non-DEBUG-READ command byte was received. */
    val evtCmdByte = Bool()

    /** A DMA error was flagged. */
    val evtDmaErr = Bool()
    evtDbgCmd  := False
    evtCmdByte := False
    evtDmaErr  := False

    /** A DMA data byte was transferred. */
    val evtDmaByte = io.dma.wdata.fire || io.dma.rdata.fire

    /** An enabled interrupt was newly raised (rising edge of irqOut). */
    val evtIrq = io.irqOut && !RegNext(io.irqOut, False)

    /** Enabled debug-latch triggers; resets to DBGCMD. */
    val latchTriggers = RegInit(B(1, 5 bits))
    io.debug.latch :=
        ((evtIrq ## evtDmaByte ## evtDmaErr ## evtCmdByte ## evtDbgCmd) &
            latchTriggers).orR

    // NOP: No Operation
    addCommand(0) { _ => null }
    // STATUS: Read Status Registers.
    addCommand(1) { _ => irqStatus ## irqEnable }
    // IRQ CLEAR: Clear Pending Interrupts.
    addCommand(3, Bits(32 bits)) { clear =>
        irqStatus := irqStatus & ~clear
        irqStatus
    }
    // IRQ ENABLE: Select Enabled Interrupts.
    addCommand(4, Bits(32 bits)) { mask =>
        irqEnable := mask
        irqEnable
    }
    // READ DMA: Use DMA To Read GPU Memory
    addCommand(8, UInt(cfg.ptrBits bits)) { addr =>
        when(isDmaSetup && !io.dma.setup.setupReady) {
            io.dma.setup.teardown := True
        }
        io.dma.setup.setup := True
        io.dma.setup.write := False
        io.dma.setup.addr  := addr.resized
        irqOnDmaReady      := True
        null
    }
    // WRITE DMA: Use DMA To Write GPU Memory
    addCommand(10, UInt(cfg.ptrBits bits)) { addr =>
        when(isDmaSetup && !io.dma.setup.setupReady) {
            io.dma.setup.teardown := True
        }
        io.dma.setup.setup := True
        io.dma.setup.write := True
        io.dma.setup.addr  := addr.resized
        irqOnDmaReady      := True
        null
    }
    // DEBUG READ: Expose internal architectural state for debug purposes.
    addCommand(12, UInt(16 bits)) { reg =>
        io.debug.index := reg
        io.debug.data
    }
    // DEBUG TRIGGERS: Select which events latch the debug registers.
    addCommand(13, Bits(8 bits)) { mask =>
        latchTriggers := mask.resized
        null
    }
    // DMA END: Tear down DMA transfer.
    addCommand(14) { _ =>
        io.dma.setup.teardown := isDmaSetup
        null
    }

    private val paramBits =
        commands.map(x => if (x._2._1 == null) 0 else x._2._1.getBitsWidth).max

    /** Parameter buffer. */
    val param = Reg(Bits(paramBits bits))

    /** How many parameter bytes have been received so far. */
    val paramLen = RegInit(U(0, log2Up(param.getBitsWidth / 8 + 1) bits))
    when(chipSelectFalling) {
        paramLen := U(0)
    }

    /** Current command. */
    val curCmd = RegInit(B(0, 8 bits))

    /** Previous command. */
    val prevCmd = RegInit(B(0, 8 bits))
    when(chipSelectFalling) {
        prevCmd := curCmd
    }

    /** Currently receiving the command. */
    val isCmd = RegInit(True)
    when(chipSelectFalling) {
        isCmd := True
    }

    val latchResp = Bool()
    latchResp := False

    /** Set of commands' return values. */
    val commandRet = for ((code, cmd) <- commands) yield {
        var retval: Data = null
        when(pRxdValid && curCmd === code) {
            if (cmd._1 == null) {
                when(paramLen === 0) {
                    retval = cmd._2(null)
                    latchResp := True
                }
            } else {
                when(paramLen === (cmd._1.getBitsWidth + 7) / 8) {
                    retval = cmd._2(param)
                    latchResp := True
                }
            }
        }
        (code, retval)
    }

    val respBits =
        commandRet.map(x => if (x._2 == null) 0 else x._2.getBitsWidth).max

    /** Combined command return value. */
    val nextResp = Reg(Bits(respBits bits))
    val resp     = Reg(Bits(respBits bits))

    /** How many response bytes have been sent so far. */
    val respIndex = Reg(UInt(8 bits))
    when(!io.chipSelect) {
        resp      := nextResp
        respIndex := U(0)
    }

    when(latchResp) {
        nextResp := B(0, respBits bits)
        for ((code, cmdResp) <- commandRet) {
            if (cmdResp != null) {
                when(curCmd === code) {
                    nextResp := cmdResp.asBits.resized
                }
            }
        }
    }

    // Receive data logic.
    io.dma.wdata.valid := False
    io.dma.wdata.payload.assignDontCare()
    when(io.chipSelect) {
        when(io.rxd.valid && isCmd) {
            when(io.rxd.payload === 9 || io.rxd.payload === 11) {
                // Clear dma_ready interrupt on READ PAYLOAD or WRITE PAYLOAD command.
                irqStatus(0) := False
            }
            curCmd := io.rxd.payload
            isCmd  := False
            when(io.rxd.payload === 12) {
                evtDbgCmd := True
            } otherwise {
                evtCmdByte := True
            }
        } elsewhen (curCmd === 11) {
            io.dma.wdata.valid   := io.rxd.valid
            io.dma.wdata.payload := io.rxd.payload
            when(!io.dma.wdata.ready && io.rxd.valid && io.chipSelect) {
                // Error if the DMA bus can't keep up.
                irqStatus(1) := True // dma_error interrupt.
                evtDmaErr    := True
            }
        } elsewhen (io.rxd.valid) {
            param(paramLen * 8, 8 bits) := io.rxd.payload
            when(paramLen =/= param.getBitsWidth / 8 + 1) {
                paramLen := paramLen + 1
            }
        }
    }

    // Response data logic.
    io.txd.payload     := B(0)
    io.dma.rdata.ready := False
    when(io.chipSelect) {
        when(io.txd.ready) {
            respIndex := respIndex + 1
        }

        when(prevCmd === 9) {
            // READ PAYLOAD command.
            io.dma.rdata.ready := io.txd.ready
            io.txd.payload     := io.dma.rdata.payload
            when(!io.dma.rdata.valid && io.txd.peek && io.chipSelect) {
                // Error if the DMA bus can't keep up: the slave needs a byte to
                // send (peek) but the backend has none.
                irqStatus(1) := True // dma_error interrupt.
                evtDmaErr    := True
            }
        } elsewhen (prevCmd === 2) {
            // DESC command.
            val desc = cfg.descStruct.asBits
            io.txd.payload := desc(respIndex * 8, 8 bits)
        } otherwise {
            // Other commands' response data.
            io.txd.payload := resp(respIndex * 8, 8 bits)
        }
    }
}
