package sapphire.texture

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.experimental.math._
import spinal.lib.misc.pipeline._

/** Accepts floating-point UV coordinates and reads raw texture data. */
case class TextureReader(cfg: SapphireCfg) extends Component {
    import cfg.plCfg
    val io = new Bundle {
        /** Texture coordinate input stream. */
        val uv      = slave  port Stream(Vec.fill(2)(Floating32()))
        /** Texture data output stream. */
        val data    = master port Stream(Bits(32 bits))
        /** Texture reference. */
        val texture = in     port TextureRef(cfg)
        /** Memory interface. */
        val mem     = master port AhbLite3Master(AhbLite3Config(cfg.vaddrBits, 32))
    }
    
    val builder = new StageCtrlPipeline()
    
    /** Convert UVs to [0,1) fixed-point numbers. */
    val f2i = new builder.Ctrl(0) {
        up.valid    := io.uv.valid
        io.uv.ready := isReady
        val UF = insert(io.uv.payload(0))
        val VF = insert(io.uv.payload(1))
        val UI = insert(UF.toRecFloating.toUFix(0 exp, 24 bits))
        val VI = insert(VF.toRecFloating.toUFix(0 exp, 24 bits))
    }
    
    /** Multiply quantized UVs into X and Y coordinates. */
    val mul = new builder.Ctrl(plCfg.fconvStage.toInt) {
        val range = cfg.pixelBits + 23 downto 24
        val X = insert((io.texture.spec.width  * f2i.UI.raw)(range))
        val Y = insert((io.texture.spec.height * f2i.VI.raw)(range))
    }
    
    /** Compute texture index of pixel. */
    val icalc = new builder.Ctrl(plCfg.fconvStage.toInt + plCfg.normMulStages) {
        val IDX = insert(mul.X + mul.Y * io.texture.spec.width)
    }
    
    /** Compute bit and byte address of pixel. */
    val acalc = new builder.Ctrl(plCfg.fconvStage.toInt + 2 * plCfg.normMulStages) {
        val OFF  = insert(io.texture.spec.pixfmt.bpp.mux(
            M"00000-" -> (icalc.IDX >> 3)            .resize(26),
            M"00001-" -> (icalc.IDX >> 2)            .resize(26),
            M"0001--" -> (icalc.IDX >> 1)            .resize(26),
            M"001---" ->  icalc.IDX                  .resize(26),
            M"010---" -> (icalc.IDX << 1)            .resize(26),
            M"011---" -> (icalc.IDX + icalc.IDX << 1).resize(26),
            default   -> (icalc.IDX << 2)            .resize(26),
        ))
        val SHR  = insert(io.texture.spec.pixfmt.bpp.mux(
            M"00000-" -> icalc.IDX(2 downto 0),
            M"00001-" -> icalc.IDX(1 downto 0).resize(3),
            M"0001--" -> icalc.IDX(0 downto 0).resize(3),
            default   -> U"000",
        ))
        val ADDR = insert(io.texture.vaddr + OFF.resize(cfg.vaddrBits))
    }
    
    /** Memory request logic. */
    val mem0 = new builder.Ctrl(plCfg.fconvStage.toInt + 2 * plCfg.normMulStages + plCfg.acalcStage.toInt) {
        // Set constant memory bus signals.
        io.mem.HWDATA.assignDontCare()
        io.mem.HWRITE    := False
        io.mem.HSIZE     := B"010"
        io.mem.HPROT     := B"1111" // Cacheable bufferable privileged data
        io.mem.HMASTLOCK := False
        io.mem.HBURST    := B"000"
        
        // Set dynamic memory bus signals.
        io.mem.HADDR  := acalc.ADDR
        io.mem.HADDR(1 downto 0) := U"00"
        io.mem.HTRANS := AhbLite3.IDLE
        when (isValid) {
            io.mem.HTRANS := AhbLite3.NONSEQ
        }
        
        // Block if the memory is not ready.
        haltWhen(!io.mem.HREADY)
    }
    
    /** Memory response logic. */
    val mem1 = new builder.Ctrl(plCfg.fconvStage.toInt + 2 * plCfg.normMulStages + plCfg.acalcStage.toInt + 1) {
        val RDATA = insert(io.mem.HRDATA)
        io.data.payload(31 downto 8) := RDATA(31 downto 8)
        io.data.payload( 7 downto 0) := RDATA( 7 downto 0) >> acalc.SHR
        io.data.valid := io.mem.HREADY
        haltWhen(io.mem.HREADY && !io.data.ready)
    }
    
    // Build the pipeline.
    builder.build()
}
