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
    
    val nodes = Array.fill(
        plCfg.fconvStage.toInt + 2*plCfg.normMulStages + plCfg.acalcStage.toInt + 1
    )(Node())
    val f2iNode   = nodes(0)
    val mulNode   = nodes(plCfg.fconvStage.toInt)
    val icalcNode = nodes(plCfg.fconvStage.toInt + plCfg.normMulStages)
    val acalcNode = nodes(plCfg.fconvStage.toInt + 2*plCfg.normMulStages)
    val memNode0  = acalcNode
    val memNode1  = nodes(plCfg.fconvStage.toInt + 2*plCfg.normMulStages + plCfg.acalcStage.toInt)
    
    /** Convert UVs to [0,1) fixed-point numbers. */
    val f2i = new f2iNode.Area {
        arbitrateFrom(io.uv)
        val UF = insert(io.uv.payload(0))
        val VF = insert(io.uv.payload(1))
        val UI = insert(UF.toRecFloating.toUFix(24 exp, 24 bits))
        val VI = insert(VF.toRecFloating.toUFix(24 exp, 24 bits))
    }
    
    /** Multiply quantized UVs into X and Y coordinates. */
    val mul = new mulNode.Area {
        val range = cfg.pixelBits + 23 downto 24
        val X = insert((io.texture.spec.width  * f2i.UI.raw)(range))
        val Y = insert((io.texture.spec.height * f2i.VI.raw)(range))
    }
    
    /** Compute texture index of pixel. */
    val icalc = new icalcNode.Area {
        val IDX = insert(mul.X + mul.Y * io.texture.spec.width)
    }
    
    /** Compute bit and byte address of pixel. */
    val acalc = new acalcNode.Area {
        val OFF  = insert(io.texture.spec.pixfmt.bpp.mux(
            M"00000-" -> (icalc.IDX >> 3)            .resize(26),
            M"00001-" -> (icalc.IDX >> 2)            .resize(26),
            M"0001--" -> (icalc.IDX >> 1)            .resize(26),
            M"001---" ->  icalc.IDX                  .resize(26),
            M"010---" -> (icalc.IDX << 1)            .resize(26),
            M"011---" -> (icalc.IDX + icalc.IDX << 1).resize(26),
            M"1-----" -> (icalc.IDX << 2)            .resize(26),
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
    val mem0 = new memNode0.Area {
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
        ready := io.mem.HREADY
    }
    
    /** Memory response logic. */
    val mem1 = new memNode1.Area {
        val RDATA = insert(io.mem.HRDATA)
        io.data.payload(8 downto 0) := RDATA(8 downto 0) >> acalc.SHR
        valid := io.mem.HREADY
        arbitrateTo(io.data)
    }
}
