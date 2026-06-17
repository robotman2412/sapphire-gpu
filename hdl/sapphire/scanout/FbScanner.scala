package sapphire.scanout

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.color._
import sapphire.dma._
import spinal.core._
import spinal.lib._

/** Raw pixel data version of a [[FbScanner]]. */
case class RawFbScanner(cfg: SapphireCfg) extends Component {
    val io = new Bundle {

        /** DMA bus that pixel data is read from. */
        val dma = master port DmaBus(cfg.vaddrBits bits)

        /** Framebuffer virtual address. */
        val vaddr = in port UInt(cfg.vaddrBits bits)

        /** Enable; internal state reset when false. */
        val enable = in port Bool()

        /** Trigger scan. Resets existing if in progress. */
        val trigger = in port Bool()

        /** Horizontal resolution minus one. */
        val width = in port UInt(cfg.coordBits bits)

        /** Vertical resolution minus one. */
        val height = in port UInt(cfg.coordBits bits)

        /** Bits per pixel. */
        val bpp = in port UInt(6 bits)

        /** Stream of extracted pixel data. */
        val pixels = master port Stream(Bits(32 bits))
    }
    io.dma.setup.setup.setAsReg.init(False)
    io.dma.setup.teardown.setAsReg.init(False)
    io.dma.wdata.valid := False
    io.dma.wdata.payload.assignDontCare

    val active     = RegInit(False)
    val isDmaSetup = RegInit(False)
    val x          = RegInit(U(0, cfg.coordBits bits))
    val y          = RegInit(U(0, cfg.coordBits bits))
    val buffer     = Reg(Bits(32 bits))
    val bitOff     = RegInit(U(0, 3 bits))
    val byteCount  = RegInit(U(0, 3 bits))
    val bpp        = Reg(UInt(6 bits))
    val byteNeeded = Reg(UInt(3 bits))
    val advancePx  = Bool() // Advance one pixel; may be less than 8 bits.
    val advanceBuf = Bool() // Advance the buffer; consume the data.
    advancePx  := False
    advanceBuf := False

    // Setup logic.
    io.dma.setup.write := False
    io.dma.setup.addr  := io.vaddr
    when(!io.enable) {
        io.dma.setup.setup := False
    }
    when(io.enable && io.trigger) {
        active    := True
        x         := U(0)
        y         := U(0)
        bitOff    := U(0)
        byteCount := U(0)
        bpp       := io.bpp
        when(io.bpp < U(8)) {
            byteNeeded := U(1)
        } otherwise {
            byteNeeded := io.bpp >> 3
        }

        io.dma.setup.teardown := isDmaSetup
        io.dma.setup.setup    := True
    }
    when(io.dma.setup.teardown && io.dma.setup.teardownReady) {
        isDmaSetup            := False
        io.dma.setup.teardown := False
    }
    when(io.dma.setup.setup && io.dma.setup.setupReady) {
        isDmaSetup         := True
        io.dma.setup.setup := False
    }

    // Read a sufficient amount of bytes.
    io.dma.rdata.ready := False
    when(active) {
        when(advanceBuf) {
            io.dma.rdata.ready := True
            when(io.dma.rdata.valid) {
                buffer(7 downto 0) := io.dma.rdata.payload
                byteCount          := U(1)
            } otherwise {
                byteCount := U(0)
            }
        } elsewhen (byteCount =/= byteNeeded) {
            io.dma.rdata.ready := True
            when(io.dma.rdata.valid) {
                switch(byteCount) {
                    is(0) { buffer(7 downto 0) := io.dma.rdata.payload }
                    is(1) { buffer(15 downto 8) := io.dma.rdata.payload }
                    is(2) { buffer(23 downto 16) := io.dma.rdata.payload }
                    is(3) { buffer(31 downto 24) := io.dma.rdata.payload }
                }
                byteCount := byteCount + 1
            }
        }
    }

    // Divide up sub-byte pixels.
    io.pixels.valid   := False
    // io.pixels.payload.assignDontCare
    io.pixels.payload := B(0)
    when(active && byteCount === byteNeeded) {
        // Negotiate available pixels with output stream.
        advancePx       := io.pixels.ready
        io.pixels.valid := True

        when(bpp < U(8)) {
            val next = bitOff +^ bpp(2 downto 0)
            bitOff := next(2 downto 0)
            when(next(3)) {
                advanceBuf := advancePx
            }
            // Mux out the right bits from the input.
            switch(bitOff) {
                is(0) { io.pixels.payload(3 downto 0) := buffer(3 downto 0) }
                is(1) { io.pixels.payload(0) := buffer(1) }
                is(2) { io.pixels.payload(1 downto 0) := buffer(3 downto 2) }
                is(3) { io.pixels.payload(0) := buffer(3) }
                is(4) { io.pixels.payload(3 downto 0) := buffer(7 downto 4) }
                is(5) { io.pixels.payload(0) := buffer(5) }
                is(6) { io.pixels.payload(1 downto 0) := buffer(7 downto 6) }
                is(7) { io.pixels.payload(0) := buffer(7) }
            }

        } otherwise {
            // >= byte per pixel, just send the entire buffer.
            io.pixels.payload := buffer
            advanceBuf        := advancePx
        }
    }

    // Keeps track of current position so we know when to stop.
    when(advancePx) {
        when(x === io.width) {
            x := 0
            y := y + 1
        } otherwise {
            x := x + 1
        }
    }

    // Stop when everything is scanned, or enable goes low.
    when(!io.enable || (advancePx && x === io.width && y === io.height)) {
        when(isDmaSetup) {
            io.dma.setup.teardown := True
        }
        active := False
    }
}

/** Scans in pixels for scanout engine implementations. */
case class FbScanner(cfg: SapphireCfg) extends Component {
    val io = new Bundle {

        /** DMA bus that pixel data is read from. */
        val dma = master port DmaBus(cfg.vaddrBits bits)

        /** Framebuffer virtual address. */
        val vaddr = in port UInt(cfg.vaddrBits bits)

        /** Enable; internal state reset when false. */
        val enable = in port Bool()

        /** Trigger scan. Resets existing if in progress. */
        val trigger = in port Bool()

        /** Horizontal resolution minus one. */
        val width = in port UInt(cfg.coordBits bits)

        /** Vertical resolution minus one. */
        val height = in port UInt(cfg.coordBits bits)

        /** Pixel format to decode. */
        val pixfmt = in port PixelFormat()

        /** Stream of extracted pixel data. */
        val pixels = master port Stream(Color())
    }

    // Use a raw scanner to get raw color data.
    val raw = RawFbScanner(cfg)
    raw.io.dma <> io.dma
    raw.io.vaddr   := io.vaddr
    raw.io.enable  := io.enable
    raw.io.trigger := io.trigger
    raw.io.width   := io.width
    raw.io.height  := io.height
    raw.io.bpp     := io.pixfmt.bpp

    // Buffer to improve fmax.
    val buffer = StreamFifo(Bits(32 bits), 1, 1)
    buffer.io.push << raw.io.pixels

    // Unpack the raw color data.
    io.pixels.valid     := buffer.io.pop.valid
    io.pixels.payload   := ColorMath.unpack(
        io.pixfmt,
        buffer.io.pop.payload.asUInt
    )
    buffer.io.pop.ready := io.pixels.ready
}
