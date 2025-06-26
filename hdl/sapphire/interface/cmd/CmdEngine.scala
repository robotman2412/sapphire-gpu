package sapphire.interface.cmd

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import sapphire._
import sapphire.util._
import scala.collection.mutable
import spinal.core._
import spinal.lib._
import sapphire.dma.DmaBus

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
    }

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
                    params.assignFromBits(data.resized)
                }
                val tmp    = function(params)
                if (tmp != null) tmp.asBits else null
            }
        )
    }

    /** Interrupt status register. */
    val irqStatus = RegInit(B(0, 32 bits))

    /** Interrupt status latched when a command that reads it is run. */
    val irqStatusLatched = Reg(Bits(32 bits))

    /** Interrupt enable register. */
    val irqEnable = RegInit(B(0, 32 bits))
    io.irqOut := (irqStatus & irqEnable) =/= 0

    /** Whether the receive data fired last cycle. */
    val pRxdValid = RegNext(io.rxd.valid, False)

    /** Assert dma_ready interrupt when the DMA bus becomes ready. */
    val irqOnDmaReady = RegInit(False)
    when(irqOnDmaReady && (io.dma.rdata.valid || io.dma.wdata.ready)) {
        irqOnDmaReady := False
        irqStatus(0)  := True // dma_ready interrupt.
    }

    // NOP: No Operation
    addCommand(0) { _ => null }
    // STATUS: Read Status Registers.
    addCommand(1) { _ => irqStatus ## irqEnable }
    // DESC: Get GPU Description Structure.
    addCommand(2) { _ => cfg.descStruct }
    // IRQ CLEAR: Clear Pending Interrupts.
    addCommand(3, Bits(32 bits)) { clear =>
        irqStatusLatched := irqStatus
        irqStatus        := irqStatus & ~clear
        irqStatusLatched
    }
    // IRQ ENABLE: Select Enabled Interrupts.
    addCommand(4, Bits(32 bits)) { mask =>
        irqEnable := mask
        null
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

    private val paramBits =
        commands.map(x => if (x._2._1 == null) 0 else x._2._1.getBitsWidth).max

    /** Parameter buffer. */
    val param = Reg(Bits(paramBits bits))

    /** How many parameter bytes have been received so far. */
    val paramLen = RegInit(U(0, log2Up(param.getBitsWidth / 8 + 1) bits))
    when(!io.chipSelect) {
        paramLen := U(0)
    }

    /** Current command. */
    val curCmd = RegInit(B(0, 8 bits))
    when(
        !io.chipSelect && isDmaSetup && (curCmd === 9 || curCmd === 11)
    ) {
        // After READ PAYLOAD or WRITE PAYLOAD, tear down DMA.
        io.dma.setup.teardown := True
    }

    /** Previous command. */
    val prevCmd = RegInit(B(0, 8 bits))
    when(!io.chipSelect) {
        prevCmd := curCmd
    }

    /** Currently receiving the command. */
    val isCmd = RegInit(True)
    when(!io.chipSelect) {
        isCmd := True
    }

    /** Set of commands' return values. */
    val commandRet = for ((code, cmd) <- commands) yield {
        var retval: Data = null
        when(pRxdValid && curCmd === code) {
            if (cmd._1 == null) {
                when(paramLen === 0) {
                    retval = cmd._2(null)
                }
            } else {
                when(paramLen === (cmd._1.getBitsWidth + 7) / 8) {
                    retval = cmd._2(param)
                }
            }
        }
        (code, retval)
    }

    val respBits =
        commandRet.map(x => if (x._2 == null) 0 else x._2.getBitsWidth).max

    /** Combined command return value. */
    val resp = Bits(respBits bits)

    /** How many response bytes have been sent so far. */
    val respIndex = Reg(UInt(log2Up((resp.getBitsWidth + 7) / 8) bits))
    when(!io.chipSelect) {
        respIndex := U(0)
    }

    /** How many response bytes there are to send. */
    val respLen = Reg(UInt(respIndex.getBitsWidth bits))

    /** Whether there is any response data left to send. */
    val hasResp = Reg(Bool())

    resp := B(0, respBits bits)
    for ((code, cmdResp) <- commandRet) {
        when(prevCmd === code) {
            if (cmdResp != null) {
                resp(cmdResp.getBitsWidth - 1 downto 0) := cmdResp.asBits
                respLen                                 := U((cmdResp.getBitsWidth + 7) / 8)
                hasResp                                 := True
            }
        }
    }

    // Receive data logic.
    io.dma.wdata.valid := False
    io.dma.wdata.payload.assignDontCare()
    when(io.rxd.valid && isCmd) {
        curCmd := io.rxd.payload
        isCmd  := False
    } elsewhen (curCmd === 11) {
        io.dma.wdata.valid   := io.rxd.valid
        io.dma.wdata.payload := io.rxd.payload
        when(!io.dma.wdata.ready && io.chipSelect) {
            // Error if the DMA bus can't keep up.
            irqStatus(1) := True // dma_error interrupt.
        }
    } elsewhen (io.rxd.valid) {
        param(paramLen * 8, 8 bits) := io.rxd.payload
        when(paramLen =/= param.getBitsWidth / 8 + 1) {
            paramLen := paramLen + 1
        }
    }

    // Response data logic.
    io.txd.payload     := B(0)
    io.dma.rdata.ready := False
    when(prevCmd === 9) {
        // READ PAYLOAD command.
        io.dma.rdata.ready := io.txd.ready
        io.txd.payload     := io.dma.rdata.payload
        when(!io.dma.rdata.valid && io.chipSelect) {
            // Error if the DMA bus can't keep up.
            irqStatus(1) := True // dma_error interrupt.
        }
    } elsewhen (hasResp) {
        // Other commands' response data.
        when(io.txd.ready && respIndex === respLen) {
            hasResp := False
        } elsewhen (io.txd.ready) {
            respIndex := respIndex + 1
        }
        io.txd.payload := resp(respIndex * 8, 8 bits)
    }
}
