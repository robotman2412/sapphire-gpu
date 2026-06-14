package sapphire.mem

// Copyright (c) 2025 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import sapphire.dma.DmaBus

object Ahb3ToDma {
    object State extends SpinalEnum {

        /** Not in any transfer. */
        val IDLE = newElement()

        /** Blocked while setting up a new transfer. */
        val SETUP = newElement()

        /** Currently transferring data. */
        val DATA = newElement()

        /** Blocked while tearing down an old transfer. */
        val TEARDOWN = newElement()
    }
}

/** Translates AHB-lite 3 accesses into DMA streaming accesses. */
case class Ahb3ToDma(
    /** AHB bus configuration. */
    cfg: AhbLite3Config,
    /** How long to keep DMA active after a transfer finishes. */
    holdCycles: Int = 3
) extends Component {
    import Ahb3ToDma._
    val io = new Bundle {

        /** AHB-lite 3 memory port. */
        val ahb = slave port AhbLite3(cfg)

        /** DMA bus. */
        val dma = master port DmaBus(cfg.addressWidth bits)
    }

    /** Current/next transfer address. */
    val dmaAddr = Reg(UInt(cfg.addressWidth bits))

    /** This/next transfer is for writing. */
    val isWrite = Reg(Bool())

    /** Remaining transfer bytes. */
    val bytesLeft = Reg(UInt(log2Up(cfg.bytePerWord + 1) bits))

    /** Log2 of number of transfer bytes. */
    val xferSize = Reg(UInt(log2Up(log2Up(cfg.bytePerWord) + 1) bits))

    /** Write data buffer. */
    val wdata = Reg(Bits(cfg.dataWidth bits))

    /** Current state. */
    val state = RegInit(State.IDLE)

    /** Whether the AHB master is making a request. */
    val ahbReq = io.ahb.HREADY &&
        (io.ahb.HTRANS === AhbLite3.NONSEQ || io.ahb.HTRANS === AhbLite3.SEQ)

    // DMA read/write logic.
    io.ahb.HRDATA.setAsReg()
    io.ahb.HRESP         := False
    io.dma.rdata.ready   := !isWrite && state === State.DATA && bytesLeft =/= 0
    io.dma.wdata.payload := wdata(7 downto 0)
    io.dma.wdata.valid   := isWrite && state === State.DATA && bytesLeft =/= 0

    // Logic that calculates next value for HRDATA.
    val rdataHsize = UInt(xferSize.getBitsWidth bits)
    rdataHsize := xferSize
    val nextRdata = Bits(cfg.dataWidth bits)
    nextRdata := io.ahb.HRDATA |>> U(8, 4 bits)
    for (i <- 0 until log2Up(cfg.bytePerWord) + 1) {
        when(xferSize === i) {
            nextRdata((8 << i) - 8, 8 bits) := io.dma.rdata.payload
        }
    }

    when(io.dma.rdata.fire || io.dma.wdata.fire) {
        // Proceed to next byte in transfer.
        dmaAddr       := dmaAddr + 1
        bytesLeft     := bytesLeft - 1
        rdataHsize    := xferSize
        io.ahb.HRDATA := nextRdata
        wdata         := wdata |>> U(8, 4 bits)
    }

    // DMA inactive by default.
    io.dma.setup.setup    := False
    io.dma.setup.write.assignDontCare()
    io.dma.setup.addr.assignDontCare()
    io.dma.setup.teardown := False

    // AHB-lite 3 request/response logic.
    io.ahb.HREADYOUT.setAsReg()
    io.ahb.HREADYOUT.init(True)
    when(io.ahb.HREADYOUT && ahbReq) {
        // Start of transfer.
        io.ahb.HREADYOUT := False
        isWrite          := io.ahb.HWRITE
        dmaAddr          := io.ahb.HADDR
        xferSize         := io.ahb.HSIZE.asUInt.resized
        wdata            := io.ahb.HWDATA
        val nextXferSize =
            U(1, bytesLeft.getBitsWidth bits) |<< io.ahb.HSIZE.asUInt
        bytesLeft := nextXferSize

        when(
            state === State.DATA && io.ahb.HADDR === dmaAddr && io.ahb.HWRITE === isWrite
        ) {
            // Can access over DMA immediately.
            val isReady = Bool()
            when(io.ahb.HWRITE) {
                // Try write early.
                io.dma.wdata.valid   := True
                io.dma.wdata.payload := io.ahb.HWDATA(7 downto 0)
                isReady              := io.dma.wdata.ready
            } otherwise {
                // Try read early.
                io.dma.rdata.ready := True
                rdataHsize         := io.ahb.HSIZE.asUInt.resized
                io.ahb.HRDATA      := nextRdata
                isReady            := io.dma.rdata.valid
            }

            when(isReady && io.ahb.HSIZE === 0) {
                // Single byte means ready again next cycle.
                io.ahb.HREADYOUT := True
                dmaAddr          := dmaAddr + 1
                bytesLeft        := 0

            } elsewhen (isReady) {
                // One byte was already done but more have yet to come.
                dmaAddr   := dmaAddr + 1
                bytesLeft := nextXferSize - 1
            }

        } elsewhen (state === State.DATA) {
            // Perform DMA teardown, then setup.
            io.dma.setup.teardown := True
            state                 := State.TEARDOWN
            when(io.dma.setup.teardownReady) {
                state := State.SETUP
            }

        } elsewhen (state === State.IDLE) {
            // Perform DMA setup.
            io.dma.setup.setup := True
            io.dma.setup.write := io.ahb.HWRITE
            io.dma.setup.addr  := io.ahb.HADDR
            state              := State.SETUP
            when(io.dma.setup.setupReady) {
                state := State.DATA
            }
        }
        // In SETUP state here is impossible because SETUP is triggered by this.
        // TODO: Check TEARDOWN correctness here, currently unreachable due to no idle timeout.

    } elsewhen (state === State.TEARDOWN) {
        // Perform DMA teardown.
        io.dma.setup.teardown := True
        when(io.dma.setup.teardownReady) {
            when(!io.ahb.HREADY) {
                // Then setup (implied by HREADY being false indicating active transfer).
                state := State.SETUP
            } otherwise {
                // Then become idle (teardown because of idle timeout).
                state := State.IDLE
            }
        }

    } elsewhen (state === State.SETUP) {
        // Perform DMA setup.
        io.dma.setup.setup := True
        io.dma.setup.write := isWrite
        io.dma.setup.addr  := dmaAddr
        when(io.dma.setup.setupReady) {
            state := State.DATA
        }

    } elsewhen (bytesLeft === 1 && (io.dma.rdata.fire || io.dma.wdata.fire)) {
        // End of transfer.
        io.ahb.HREADYOUT := True
    }
}
