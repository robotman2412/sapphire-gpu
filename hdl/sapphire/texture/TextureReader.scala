package sapphire.texture

// Copyright (c) 2024 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.experimental.math._
import spinal.lib.misc.pipeline._
import sapphire.util.FloatUtil.floatToUint01

/** Accepts floating-point UV coordinates and reads raw texture data. */
case class TextureReader(cfg: SapphireCfg) extends Component {
    import cfg.plCfg
    val io = new Bundle {

        /** Texture coordinate input stream. */
        val uv = slave port Stream(Vec.fill(2)(Floating32()))

        /** Texture data output stream. */
        val data = master port Stream(Bits(32 bits))

        /** Texture reference. */
        val texture = in port TextureRef(cfg)

        /** Memory interface. */
        val ahb = master port AhbLite3Master(AhbLite3Config(cfg.vaddrBits, 32))

        /** Is currently processing any requests. */
        val busy = out port Bool()
    }

    val builder     = new StageCtrlPipeline()
    private var pos = 0
    var busy        = False

    // Set constant memory bus signals.
    io.ahb.HWDATA.assignDontCare()
    io.ahb.HWRITE    := False
    io.ahb.HPROT     := B"1111" // Cacheable bufferable privileged data.
    io.ahb.HMASTLOCK := False
    io.ahb.HBURST    := B"000"  // SINGLE.
    io.ahb.HSIZE     := io.texture.spec.pixfmt.bpp.mux(
        default   -> B"000",
        M"010---" -> B"001",
        M"011---" -> B"010",
        M"1-----" -> B"010"
    )

    /** Convert UVs to [0,1) fixed-point numbers. */
    val f2i = new builder.Ctrl(pos) {
        busy |= isValid
        up.valid    := io.uv.valid
        io.uv.ready := isReady
        val UI = insert(floatToUint01(io.uv.payload(0), 24))
        val VI = insert(floatToUint01(io.uv.payload(1), 24))
    }
    pos += plCfg.fconvStage.toInt

    /** Multiply quantized UVs into X and Y coordinates. */
    val mul = new builder.Ctrl(pos) {
        busy |= isValid
        val range = cfg.coordBits + 23 downto 24
        val X     = insert((io.texture.spec.width * f2i.UI)(range))
        val Y     = insert((io.texture.spec.height * f2i.VI)(range))
    }
    pos += plCfg.normMulStages

    /** Compute texture index of pixel. */
    val icalc = new builder.Ctrl(pos) {
        busy |= isValid
        val IDX = insert(mul.X + mul.Y * io.texture.spec.width)
    }
    pos += plCfg.normMulStages

    /** Compute bit and byte address of pixel. */
    val acalc = new builder.Ctrl(pos) {
        busy |= isValid
        val OFF  = insert(
            io.texture.spec.pixfmt.bpp.mux(
                M"00000-" -> (icalc.IDX >> 3).resize(26),
                M"00001-" -> (icalc.IDX >> 2).resize(26),
                M"0001--" -> (icalc.IDX >> 1).resize(26),
                M"001---" -> icalc.IDX.resize(26),
                M"010---" -> (icalc.IDX << 1).resize(26),
                M"011---" -> (icalc.IDX + icalc.IDX << 1).resize(26),
                default   -> (icalc.IDX << 2).resize(26)
            )
        )
        val BPOS = insert(
            io.texture.spec.pixfmt.bpp.mux(
                M"00000-" -> icalc.IDX(2 downto 0),
                M"00001-" -> (icalc.IDX(1 downto 0) << 1),
                M"0001--" -> (icalc.IDX(0 downto 0) << 2),
                default   -> U"000"
            )
        )
        val ADDR = insert(io.texture.vaddr + OFF.resize(cfg.vaddrBits))
    }
    pos += plCfg.acalcStage.toInt

    /** Memory access logic. */
    val asyncMem = new Area {
        // Pipeline handshaking.
        val request = Bool()
        val addr    = UInt(cfg.vaddrBits bits)

        val ready = Bool()
        val data  = Bits(32 bits)

        // Memory request tracking.
        val inflightFrac = Reg(Bool) // Inflight is the first half of a split 24BPP load.
        val inflight     = RegInit(False)
        val inflightAddr = Reg(UInt(cfg.vaddrBits bits))
        val buffer       = Reg(Bits(32 bits))

        when(inflightFrac && io.ahb.HREADY) {
            buffer := io.ahb.HRDATA
        }

        // Addresses of fragments for 24BPP.
        val loAddr = addr & U((BigInt(1) << cfg.vaddrBits) - 3)
        val hiAddr = 4 + (inflightAddr & U((BigInt(1) << cfg.vaddrBits) - 3))

        // Memory request logic.
        when(inflightFrac) {
            io.ahb.HTRANS := AhbLite3.NONSEQ
            io.ahb.HADDR  := hiAddr
            when(io.ahb.HREADY) {
                inflight     := True
                inflightFrac := False
            }
        } elsewhen (request) {
            io.ahb.HTRANS := AhbLite3.NONSEQ
            when(io.texture.spec.pixfmt.bpp === 24) {
                io.ahb.HADDR := loAddr
                when(io.ahb.HREADY) {
                    inflightFrac := addr(1)
                }
            } otherwise {
                io.ahb.HADDR := addr
            }
            when(io.ahb.HREADY) {
                inflight     := True
                inflightAddr := addr
            }
        } otherwise {
            io.ahb.HTRANS := AhbLite3.IDLE
            io.ahb.HADDR.assignDontCare()
        }

        // Memory response logic.
        when(io.texture.spec.pixfmt.bpp === 24) {
            switch(inflightAddr(1 downto 0)) {
                is(0) {
                    data := io.ahb.HRDATA
                }
                is(1) {
                    data := io.ahb.HRDATA |>> 8
                }
                is(2) {
                    data(23 downto 16) := io.ahb.HRDATA(7 downto 0)
                    data(15 downto 0)  := buffer(31 downto 16)
                }
                is(3) {
                    data(23 downto 8) := io.ahb.HRDATA(15 downto 0)
                    data(7 downto 0)  := buffer(31 downto 24)
                }
            }
            data(31 downto 24).assignDontCare()
        } otherwise {
            data := io.ahb.HRDATA >> (8 * inflightAddr(1 downto 0))
        }
        ready := inflight && !inflightFrac && io.ahb.HREADY
    }

    /** Memory request stage. */
    val mem0 = new builder.Ctrl(pos) {
        busy |= isValid
        asyncMem.request := isValid && isReady
        asyncMem.addr    := acalc.ADDR
    }
    pos += 1

    /** Memory response stage. */
    val mem1 = new builder.Ctrl(pos) {
        busy |= isValid
        haltWhen(isValid && !asyncMem.ready)

        val RDATA = insert(io.ahb.HRDATA)
        io.data.payload(31 downto 8) := asyncMem.data(31 downto 8)
        io.data.payload(7 downto 0)  := asyncMem.data(7 downto 0) >> acalc.BPOS
        io.data.valid                := isValid && asyncMem.ready
        haltWhen(io.data.isStall)
    }
    pos += 1

    // Build the pipeline.
    builder.build()
    io.busy := busy
}
