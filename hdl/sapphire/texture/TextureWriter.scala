package sapphire.texture

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.experimental.math._
import spinal.lib.misc.pipeline._


case class Point(cfg: SapphireCfg) extends Bundle {
    val x    = UInt(cfg.coordBits bits)
    val y    = UInt(cfg.coordBits bits)
    val data = Bits(32 bits)
}

/** Accepts integer coordinates and writes raw texture data. */
case class TextureWriter(cfg: SapphireCfg) extends Component {
    // TODO: 24BPP support.
    import cfg.plCfg
    val io = new Bundle {
        /** Texture data input stream. */
        val data    = master port Stream(Point(cfg))
        /** Texture reference. */
        val texture = in     port TextureRef(cfg)
        /** Memory interface. */
        val mem     = master port AhbLite3Master(AhbLite3Config(cfg.vaddrBits, 32))
        /** Clear the pipeline and cache. Use before changing texture being written to. */
        val clear   = in     port Bool()
        /** Whether the pipeline is still busy writing the data. */
        val busy    = out    port Bool()
    }
    
    val builder = new StageCtrlPipeline()
    
    /** Compute texture index of pixel. */
    val icalc = new builder.Ctrl(0) {
        terminateWhen(io.clear)
        
        val IDX   = insert(io.data.payload.x + io.data.payload.y * io.texture.spec.width)
        val WDATA = insert(io.data.payload.data)
    }
    
    /** Compute bit and byte address of pixel. */
    val acalc = new builder.Ctrl(plCfg.normMulStages) {
        terminateWhen(io.clear)
        
        val OFF  = insert(io.texture.spec.pixfmt.bpp.mux(
            M"00000-" -> (icalc.IDX >> 3)            .resize(26),
            M"00001-" -> (icalc.IDX >> 2)            .resize(26),
            M"0001--" -> (icalc.IDX >> 1)            .resize(26),
            M"001---" ->  icalc.IDX                  .resize(26),
            M"010---" -> (icalc.IDX << 1)            .resize(26),
            M"011---" -> (icalc.IDX + icalc.IDX << 1).resize(26),
            default   -> (icalc.IDX << 2)            .resize(26),
        ))
        val BPOS = insert(io.texture.spec.pixfmt.bpp.mux(
            M"00000-" -> icalc.IDX(2 downto 0),
            M"00001-" -> icalc.IDX(1 downto 0).resize(3),
            M"0001--" -> icalc.IDX(0 downto 0).resize(3),
            default   -> U"000",
        ))
        val ADDR = insert(io.texture.vaddr + OFF.resize(cfg.vaddrBits))
    }
    
    /** Whether the cache has data. */
    val cacheValid = RegInit(False)
    /** Address of cached byte. */
    val cacheAddr  = Reg(UInt(cfg.vaddrBits bits))
    /** Cached byte. */
    val cacheByte  = Bits(8 bits)
    
    /** Memory request logic. */
    val mem0 = new builder.Ctrl(plCfg.normMulStages + plCfg.acalcStage.toInt) {
        terminateWhen(io.clear)
        
        // Set constant memory bus signals.
        io.mem.HWDATA.assignDontCare()
        io.mem.HPROT     := B"1111" // Cacheable bufferable privileged data.
        io.mem.HMASTLOCK := False
        io.mem.HBURST    := B"000" // SINGLE.
        
        // Set dynamic memory bus signals.
        io.mem.HSIZE     := io.texture.spec.pixfmt.bpp.mux(
            default   -> B"000",
            M"010---" -> B"001",
            M"011---" -> B"010",
            M"1-----" -> B"010",
        )
        io.mem.HADDR  := acalc.ADDR
        io.mem.HTRANS := AhbLite3.IDLE
        
        io.mem.HWRITE := False
        io.mem.HWDATA.assignDontCare()
        val wdata = Bits(32 bits)
        when (io.texture.spec.pixfmt.bpp < 8 && (!cacheValid || cacheAddr =/= acalc.ADDR)) {
            // Get unmodified bits for sub-byte write.
            io.mem.HTRANS := AhbLite3.NONSEQ
            wdata.assignDontCare()
            haltIt()
            
        } elsewhen(io.texture.spec.pixfmt.bpp < 8) {
            // It be a sub-byte write.
            val mask = ((U"8'b1" << io.texture.spec.pixfmt.bpp(2 downto 0)) - 1).asBits
            io.mem.HWRITE := True
            io.mem.HTRANS := AhbLite3.NONSEQ
            wdata         := icalc.WDATA & ~(mask << acalc.BPOS) | (mask << acalc.BPOS)
            
        } otherwise {
            // It be a naturally aligned write.
            io.mem.HWRITE := True
            io.mem.HTRANS := AhbLite3.NONSEQ
            wdata         := icalc.WDATA
        }
        
        val TRANS = insert(io.mem.HTRANS =/= AhbLite3.IDLE)
        val WRITE = insert(io.mem.HWRITE)
        val WDATA = insert(wdata)
        val ADDR  = insert(io.mem.HADDR)
    }
    
    /** Memory response logic. */
    val mem1 = new builder.Ctrl(plCfg.normMulStages + plCfg.acalcStage.toInt + 1) {
        terminateWhen(io.clear)
        
        // AHB write data comes one cycle after the address.
        io.mem.HWDATA := mem0.WDATA
        
        // If a transaction was initiated and memory is not ready, the pipeline must be stalled.
        haltWhen(mem0.TRANS && !io.mem.HREADY)
        
        when (io.clear) {
            // Invalidate cache if clear is requested.
            cacheByte.assignDontCare()
            cacheAddr.assignDontCare()
            cacheValid := False
        } elsewhen (mem0.TRANS && !mem0.WRITE && io.mem.HREADY && isValid) {
            // If a read transaction was initiated, cache its data.
            cacheByte  := io.mem.HRDATA(7 downto 0)
            cacheAddr  := mem0.ADDR
            cacheValid := True
        }
    }
    
    // Build the pipeline.
    builder.build()
}
