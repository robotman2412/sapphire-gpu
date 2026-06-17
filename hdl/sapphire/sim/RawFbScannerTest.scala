package sapphire.sim

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.scanout._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import scala.collection.mutable.ArrayBuffer

/** Test bench for [[RawFbScanner]].
  *
  * Wraps the scanner together with a tiny synthesizable DMA slave that streams
  * bytes sequentially out of a backing memory, starting from the address given
  * at DMA setup time. This lets the simulation drive the scanner exactly like
  * the real GPU memory subsystem would: byte-by-byte, little-endian.
  *
  * The memory is intentionally a power-of-two so the read address can wrap
  * harmlessly; a buggy scanner that reads too many bytes will still get a
  * defined (if wrong) value rather than crashing the simulator.
  */
case class RawFbScannerDut(cfg: SapphireCfg, memInit: Seq[Int]) extends Component {
    val io = new Bundle {
        val vaddr   = in port UInt(cfg.vaddrBits bits)
        val enable  = in port Bool()
        val trigger = in port Bool()
        val width   = in port UInt(cfg.coordBits bits)
        val height  = in port UInt(cfg.coordBits bits)
        val bpp     = in port UInt(6 bits)
        val pixels  = master port Stream(Bits(32 bits))
    }

    val scanner = RawFbScanner(cfg)
    scanner.io.vaddr   := io.vaddr
    scanner.io.enable  := io.enable
    scanner.io.trigger := io.trigger
    scanner.io.width   := io.width
    scanner.io.height  := io.height
    scanner.io.bpp     := io.bpp
    scanner.io.pixels >> io.pixels

    // Backing memory for the DMA slave.
    val mem      = Mem(Bits(8 bits), memInit.length) init (memInit.map(v => B(v, 8 bits)))
    val memAbits = log2Up(memInit.length)

    // Synthesizable DMA slave: always has a byte ready while busy and streams
    // sequentially from the latched setup address.
    val busy = RegInit(False)
    val addr = Reg(UInt(cfg.vaddrBits bits)) init (0)

    scanner.io.dma.setup.setupReady    := !busy
    scanner.io.dma.setup.teardownReady := busy
    scanner.io.dma.rdata.valid         := busy
    scanner.io.dma.rdata.payload       := mem.readAsync(addr.resize(memAbits))
    scanner.io.dma.wdata.ready         := False

    when(scanner.io.dma.setup.teardown && busy) {
        busy := False
    } elsewhen (scanner.io.dma.setup.setup && !busy) {
        busy := True
        addr := scanner.io.dma.setup.addr
    } elsewhen (scanner.io.dma.rdata.fire) {
        addr := addr + 1
    }
}

object RawFbScannerTest extends App {
    // 12-bit coords, 64 KiB address space; plenty for the test patterns.
    val cfg = SapphireCfg(0x10000)

    // Deterministic, well-mixed byte pattern. Power-of-two length so the DMA
    // read address wraps cleanly if the scanner over-reads.
    val mem: Seq[Int] = Seq(
        0x59, 0x06, 0xb3, 0x60, 0x0d, 0xba, 0x67, 0x14, 0xc1, 0x6e, 0x1b, 0xc8, 0x75, 0x22, 0xcf, 0x7c,
        0x29, 0xd6, 0x83, 0x30, 0xdd, 0x8a, 0x37, 0xe4, 0x91, 0x3e, 0xeb, 0x98, 0x45, 0xf2, 0x9f, 0x4c,
        0xf9, 0xa6, 0x53, 0x00, 0xad, 0x5a, 0x07, 0xb4, 0x61, 0x0e, 0xbb, 0x68, 0x15, 0xc2, 0x6f, 0x1c,
        0xc9, 0x76, 0x23, 0xd0, 0x7d, 0x2a, 0xd7, 0x84, 0x31, 0xde, 0x8b, 0x38, 0xe5, 0x92, 0x3f, 0xec,
        0x99, 0x46, 0xf3, 0xa0, 0x4d, 0xfa, 0xa7, 0x54, 0x01, 0xae, 0x5b, 0x08, 0xb5, 0x62, 0x0f, 0xbc,
        0x69, 0x16, 0xc3, 0x70, 0x1d, 0xca, 0x77, 0x24, 0xd1, 0x7e, 0x2b, 0xd8, 0x85, 0x32, 0xdf, 0x8c,
        0x39, 0xe6, 0x93, 0x40, 0xed, 0x9a, 0x47, 0xf4, 0xa1, 0x4e, 0xfb, 0xa8, 0x55, 0x02, 0xaf, 0x5c,
        0x09, 0xb6, 0x63, 0x10, 0xbd, 0x6a, 0x17, 0xc4, 0x71, 0x1e, 0xcb, 0x78, 0x25, 0xd2, 0x7f, 0x2c,
        0xd9, 0x86, 0x33, 0xe0, 0x8d, 0x3a, 0xe7, 0x94, 0x41, 0xee, 0x9b, 0x48, 0xf5, 0xa2, 0x4f, 0xfc,
        0xa9, 0x56, 0x03, 0xb0, 0x5d, 0x0a, 0xb7, 0x64, 0x11, 0xbe, 0x6b, 0x18, 0xc5, 0x72, 0x1f, 0xcc,
        0x79, 0x26, 0xd3, 0x80, 0x2d, 0xda, 0x87, 0x34, 0xe1, 0x8e, 0x3b, 0xe8, 0x95, 0x42, 0xef, 0x9c,
        0x49, 0xf6, 0xa3, 0x50, 0xfd, 0xaa, 0x57, 0x04, 0xb1, 0x5e, 0x0b, 0xb8, 0x65, 0x12, 0xbf, 0x6c,
        0x19, 0xc6, 0x73, 0x20, 0xcd, 0x7a, 0x27, 0xd4, 0x81, 0x2e, 0xdb, 0x88, 0x35, 0xe2, 0x8f, 0x3c,
        0xe9, 0x96, 0x43, 0xf0, 0x9d, 0x4a, 0xf7, 0xa4, 0x51, 0xfe, 0xab, 0x58, 0x05, 0xb2, 0x5f, 0x0c,
        0xb9, 0x66, 0x13, 0xc0, 0x6d, 0x1a, 0xc7, 0x74, 0x21, 0xce, 0x7b, 0x28, 0xd5, 0x82, 0x2f, 0xdc,
        0x89, 0x36, 0xe3, 0x90, 0x3d, 0xea, 0x97, 0x44, 0xf1, 0x9e, 0x4b, 0xf8, 0xa5, 0x52, 0xff, 0xac,
    )

    /** Reference model: the framebuffer is a little-endian bit stream; pixel
      * `k` is the `bpp` bits starting at bit `k*bpp`, least-significant bit
      * first. This covers sub-byte (1/2/4 bpp), byte (8) and multi-byte
      * little-endian (16/24/32) packing with one formula.
      */
    def refPixels(bpp: Int, count: Int): Seq[Long] = {
        (0 until count).map { k =>
            var v = 0L
            for (b <- 0 until bpp) {
                val bitIndex  = k.toLong * bpp + b
                val byteIndex = (bitIndex / 8).toInt % mem.length
                val bitInByte = (bitIndex % 8).toInt
                val bit       = (mem(byteIndex) >> bitInByte) & 1
                v |= bit.toLong << b
            }
            v
        }
    }

    Config.sim
        .compile(RawFbScannerDut(cfg, mem))
        .doSim(this.getClass.getSimpleName) { dut =>
            dut.clockDomain.forkStimulus(period = 10)

            // Sensible idle defaults.
            dut.io.vaddr #= 0
            dut.io.enable #= false
            dut.io.trigger #= false
            dut.io.width #= 0
            dut.io.height #= 0
            dut.io.bpp #= 8
            dut.io.pixels.ready #= true
            dut.clockDomain.waitSampling(2)

            /** Drop enable so the scanner resets and tears down any DMA, then
              * leave it low for a few cycles before the next scan.
              */
            def reset(): Unit = {
                dut.io.enable #= false
                dut.io.trigger #= false
                dut.clockDomain.waitSampling(8)
            }

            /** Configure and trigger a fresh scan of `w`x`h` (minus-one) pixels
              * at the given bpp, starting from address 0.
              */
            def startScan(bpp: Int, w: Int, h: Int): Unit = {
                dut.io.vaddr #= 0
                dut.io.width #= w
                dut.io.height #= h
                dut.io.bpp #= bpp
                dut.io.enable #= true
                dut.io.trigger #= true
                dut.clockDomain.waitSampling()
                dut.io.trigger #= false
            }

            /** Collect up to `count` pixels with the output stream always ready,
              * masking the payload to the meaningful low `bpp` bits.
              */
            def collect(bpp: Int, count: Int, maxCycles: Int = 4000): Seq[Long] = {
                val mask = if (bpp >= 32) 0xffffffffL else (1L << bpp) - 1
                val got  = ArrayBuffer[Long]()
                dut.io.pixels.ready #= true
                var cyc = 0
                while (got.length < count && cyc < maxCycles) {
                    dut.clockDomain.waitSampling()
                    if (dut.io.pixels.valid.toBoolean) {
                        got += (dut.io.pixels.payload.toLong & mask)
                    }
                    cyc += 1
                }
                got.toSeq
            }

            /** Full single-row scan of `count` pixels at `bpp`, checked against
              * the little-endian reference, including that the stream stops
              * after exactly `count` pixels.
              */
            def checkBpp(bpp: Int, count: Int): Unit = {
                reset()
                startScan(bpp, count - 1, 0)
                val got = collect(bpp, count)
                val exp = refPixels(bpp, count)
                assert(
                    got.length == count,
                    s"$bpp bpp: expected $count pixels, only got ${got.length} " +
                        "before timeout (scanner stalled or never produced)"
                )
                for (i <- 0 until count) {
                    assert(
                        got(i) == exp(i),
                        s"$bpp bpp: pixel $i mismatch: got 0x${got(i).toHexString}, " +
                            s"expected 0x${exp(i).toHexString} (little-endian, LSB first)"
                    )
                }
                // Must not keep producing after the frame is done.
                dut.io.pixels.ready #= true
                dut.clockDomain.waitSampling(6)
                assert(
                    !dut.io.pixels.valid.toBoolean,
                    s"$bpp bpp: scanner produced extra pixels after the full frame"
                )
                println(s"$bpp bpp: OK ($count pixels)")
            }

            // ---- 1) Per-bpp correctness ------------------------------------
            // Each count is chosen to span several bytes so multi-byte and
            // sub-byte packing are both exercised across byte boundaries.
            checkBpp(1, 40)
            checkBpp(2, 20)
            checkBpp(4, 18)
            checkBpp(8, 12)
            checkBpp(16, 10)
            checkBpp(24, 9)
            checkBpp(32, 8)

            // ---- 2) Multi-row scan -----------------------------------------
            // Exercises the y counter and confirms the total count is
            // (width+1)*(height+1). Rows are packed contiguously in the bit
            // stream (no per-row byte alignment), matching the reference.
            {
                reset()
                val w = 3; val h = 2; val bpp = 8
                val count = (w + 1) * (h + 1)
                startScan(bpp, w, h)
                val got = collect(bpp, count)
                val exp = refPixels(bpp, count)
                assert(
                    got.length == count,
                    s"multi-row: expected $count pixels, got ${got.length}"
                )
                for (i <- 0 until count) {
                    assert(
                        got(i) == exp(i),
                        s"multi-row: pixel $i mismatch: got 0x${got(i).toHexString}, " +
                            s"expected 0x${exp(i).toHexString}"
                    )
                }
                dut.clockDomain.waitSampling(6)
                assert(
                    !dut.io.pixels.valid.toBoolean,
                    "multi-row: extra pixels produced after the full frame"
                )
                println(s"multi-row: OK ($count pixels)")
            }

            // ---- 3) Early stop when enable is deasserted -------------------
            // Start a large scan, consume a handful of pixels, then drop enable
            // mid-scan. The stream must go (and stay) idle promptly.
            {
                reset()
                val bpp = 8
                startScan(bpp, 255, 0) // 256-pixel row, far more than we read
                // Consume a few pixels to make sure it is mid-scan.
                val partial = collect(bpp, 5)
                assert(
                    partial.length == 5,
                    s"early-stop: scanner did not produce initial pixels (got ${partial.length})"
                )
                // Deassert enable; keep draining the stream.
                dut.io.enable #= false
                dut.io.pixels.ready #= true
                // Allow a cycle or two for the in-flight handshake to settle.
                dut.clockDomain.waitSampling(3)
                // From here on, no further pixels may appear.
                var leaked = 0
                for (_ <- 0 until 30) {
                    dut.clockDomain.waitSampling()
                    if (dut.io.pixels.valid.toBoolean) leaked += 1
                }
                assert(
                    leaked == 0,
                    s"early-stop: scanner kept producing $leaked pixels after enable was deasserted"
                )
                println("early-stop: OK")
            }

            // ---- 4) Recovery after an illegal config while disabled --------
            // Legal bpp must be a power of two or byte-aligned. Feed an illegal
            // bpp while enable is low (and even pulse trigger), which must have
            // no lasting effect, then run a normal legal scan and verify it is
            // bit-for-bit correct.
            {
                reset()
                val illegalBpp = 13 // neither a power of two nor byte-aligned
                // "Run" the illegal configuration, but with enable low the
                // whole time so the scanner never actually starts.
                dut.io.enable #= false
                dut.io.bpp #= illegalBpp
                dut.io.width #= 100
                dut.io.height #= 5
                dut.io.vaddr #= 0
                dut.io.trigger #= true
                dut.clockDomain.waitSampling(4)
                dut.io.trigger #= false
                dut.clockDomain.waitSampling(4)
                // Nothing should have been emitted while disabled.
                assert(
                    !dut.io.pixels.valid.toBoolean,
                    "recovery: scanner emitted pixels while disabled with an illegal config"
                )

                // Now run a perfectly legal scan; it must work correctly.
                reset()
                val bpp = 16; val count = 10
                startScan(bpp, count - 1, 0)
                val got = collect(bpp, count)
                val exp = refPixels(bpp, count)
                assert(
                    got.length == count,
                    s"recovery: expected $count pixels, got ${got.length}"
                )
                for (i <- 0 until count) {
                    assert(
                        got(i) == exp(i),
                        s"recovery: pixel $i mismatch: got 0x${got(i).toHexString}, " +
                            s"expected 0x${exp(i).toHexString}"
                    )
                }
                println("recovery-after-illegal: OK")
            }

            println("All RawFbScanner tests passed.")
        }
}
