package sapphire.dma

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._
import sapphire.phy.spi._

/** SPI memory controller state. */
object SpiMemCtrl {
    object State extends SpinalEnum(binaryOneHot) {

        /** Resetting the chip. */
        val RESET = newElement()

        /** Idle; waiting for DMA setup. */
        val IDLE = newElement()

        /** Sending command. */
        val CMD = newElement()

        /** Sending command to address dummy cycles. */
        val PRE_ADDR = newElement()

        /** Sending address. */
        val ADDR = newElement()

        /** Sending address to data dummy cycles. */
        val PRE_DATA = newElement()

        /** Ready to transfer data. */
        val DATA = newElement()
    }
}

/** SPI memory access settings. */
case class SpiMemSettings() extends Bundle {

    /** SPI settings for command. */
    val cmdSettings = SpiSettings()

    /** SPI settings for addr/data. */
    val dataSettings = SpiSettings()

    /** Number of address bytes. */
    val addrLen = UInt(3 bits)

    /** Write command. */
    val writeCmd = Bits(8 bits)

    /** Read command. */
    val readCmd = Bits(8 bits)

    /** Number of command to address dummy cycles. */
    val preAddrCycles = UInt(5 bits)

    /** Number of address to write dummy cycles. */
    val preWriteCycles = UInt(5 bits)

    /** Number of address to read dummy cycles. */
    val preReadCycles = UInt(5 bits)

    /** Number of cycles to keep chip select inactive between commands. */
    val csResetCycles = UInt(5 bits)
}

/** SPI memory controller. */
case class SpiMemCtrl(abits: BitCount) extends Component {
    import SpiMemCtrl._
    val io = new Bundle {

        /** Enable; while false, will never become ready for DMA setup and
          * therefor never do anything. WARNING: Does not initiate DMA teardown;
          * make sure to initiate or wait for teardown before directly using the
          * SPI controller.
          */
        val enable = in port Bool()

        /** DMA slave port. */
        val dma = slave port DmaBus(abits)

        /** SPI memory access settings. */
        val settings = in port SpiMemSettings()

        /** Assert memory's chip select (active high). */
        val chipSelect = out port Bool()

        /** SPI master PHY interface. */
        val spi = master port SpiMaster.Bus()
    }
    io.chipSelect.setAsReg()
    io.chipSelect.init(False)

    /** Settings latched at the start of DMA setup. */
    val settings = Reg(SpiMemSettings())

    /** Remaining number of cycles before transition to next state. */
    val cycles = RegInit(U(0, 5 bits))

    /** Current FSM state. */
    val state = RegInit(State.IDLE)

    /** Command / address buffer. */
    val buffer = Reg(Bits(abits.value.max(8) bits))

    /** Current access is a write. */
    val isWrite = Reg(Bool())

    /** Address buffered at DMA setup time. */
    val addr = Reg(UInt(abits))

    /** Size of the read buffer. */
    val readBufDepth = 3

    /** How much capacity for reads is in the read buffer. */
    val readCap = RegInit(U(readBufDepth, log2Up(readBufDepth) bits))

    /** Read data buffer. */
    val fifo = StreamFifo(Bits(8 bits), depth = readBufDepth)
    fifo.io.flush := False
    fifo.io.push << io.spi.rxData.toStream
    io.dma.rdata << fifo.io.pop

    // Ready to accept setup in IDLE state.
    io.dma.setup.setupReady    := state === State.IDLE && io.enable
    // Ready to accept teardown in DATA state.
    io.dma.setup.teardownReady := state === State.DATA && !io.spi.action.fire && !io.spi.busy

    // Read capacity logic.
    when(io.dma.setup.teardown && io.dma.setup.teardownReady) {
        readCap := U(readBufDepth, 2 bits)
    } elsewhen (!isWrite && state === State.DATA) {
        when(io.dma.rdata.fire && !io.spi.action.fire) {
            readCap := readCap + 1
        } elsewhen (!io.dma.rdata.fire && io.spi.action.fire) {
            readCap := readCap - 1
        }
    }

    // SPI controller command logic.
    io.dma.wdata.ready := False
    when(state === State.DATA) {
        when(isWrite) {
            // Connect DMA stream for write.
            io.dma.wdata.ready          := io.spi.action.ready
            io.spi.action.valid         := io.dma.wdata.valid
            io.spi.action.payload.data  := io.dma.wdata.payload
            io.spi.action.payload.atype := SpiMaster.ActionType.SEND_BYTE
        } otherwise {
            // Connect DMA stream for read.
            io.spi.action.valid         := readCap =/= 0
            io.spi.action.payload.data.assignDontCare
            io.spi.action.payload.atype := SpiMaster.ActionType.RECV_BYTE
        }
        io.spi.action.payload.settings := settings.dataSettings

    } elsewhen (state =/= State.IDLE && state =/= State.RESET) {
        // Set-up states.
        io.spi.action.valid        := True
        when(state === State.CMD) {
            io.spi.action.payload.settings := settings.cmdSettings
        } otherwise {
            io.spi.action.payload.settings := settings.dataSettings
        }
        when(state === State.PRE_ADDR || state === State.PRE_DATA) {
            io.spi.action.payload.atype := SpiMaster.ActionType.DUMMY_CLOCK
        } otherwise {
            io.spi.action.payload.atype := SpiMaster.ActionType.SEND_BYTE
        }
        io.spi.action.payload.data := buffer(7 downto 0)

    } otherwise {
        // Nothing to do when idle.
        io.spi.action.valid := False
        io.spi.action.payload.assignDontCare
    }

    // FSM update logic.
    when(io.dma.setup.setup && io.dma.setup.setupReady) {
        // DMA setup initiated.
        state         := State.CMD
        cycles        := U(1, 5 bits)
        settings      := io.settings
        isWrite       := io.dma.setup.write
        addr          := io.dma.setup.addr.resized
        io.chipSelect := True
        when(io.dma.setup.write) {
            buffer := io.settings.writeCmd.resized
        } otherwise {
            buffer := io.settings.readCmd.resized
        }

    } elsewhen (io.dma.setup.teardown && io.dma.setup.teardownReady) {
        // DMA teardown initiated.
        state         := State.RESET
        cycles        := settings.csResetCycles
        io.chipSelect := False
        fifo.io.flush := True

    } elsewhen (state === State.IDLE || state === State.DATA) {
        // No state change when in idle or data state.

    } elsewhen (state === State.RESET) {
        when(cycles > 1) {
            // Wait for enough cycles to pass.
            cycles := cycles - 1
        } otherwise {
            // Reset done; switch to idle.
            state := State.IDLE
            cycles.assignDontCare
        }

    } elsewhen (io.spi.action.ready) {
        when(cycles > 1) {
            // Switch to next byte in buffer.
            buffer := buffer |>> 8;
            cycles := cycles - 1
        } elsewhen (state === State.CMD && settings.preAddrCycles =/= 0) {
            // Switch to command to address dummy cycles.
            cycles := settings.preAddrCycles
            state  := State.PRE_ADDR
        } elsewhen (state === State.CMD || state === State.PRE_ADDR) {
            // Switch to address.
            cycles := settings.addrLen.resized
            state  := State.ADDR
            buffer := addr.asBits.resized
        } elsewhen (state === State.ADDR && isWrite && settings.preWriteCycles =/= 0) {
            // Switch to address to write dummy cycles.
            cycles := settings.preWriteCycles
            state  := State.PRE_DATA
        } elsewhen (state === State.ADDR && !isWrite && settings.preReadCycles =/= 0) {
            // Switch to address to read dummy cycles.
            cycles := settings.preReadCycles
            state  := State.PRE_DATA
        } elsewhen (state === State.ADDR || state === State.PRE_DATA) {
            // Switch to data.
            state := State.DATA
        }
    }
}
