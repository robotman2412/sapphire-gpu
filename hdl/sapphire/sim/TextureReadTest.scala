package sapphire.sim

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import sapphire._
import sapphire.texture._
import sapphire.util._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.experimental.math._
import java.lang.Float.floatToIntBits

case class TextureReadTestBench(cfg: SapphireCfg) extends Component {
    val io = new Bundle {
        /** Texture coordinate input stream. */
        val uv      = slave  port Stream(Vec.fill(2)(Bits(32 bits)))
        /** Texture data output stream. */
        val data    = master port Stream(Bits(32 bits))
    }
    
    val rom = new AlignedAhb3Rom(
        AhbLite3Config(cfg.vaddrBits, 32),
        Seq(
            0,  1,  2,  3,
            4,  5,  6,  7,
            8,  9,  10, 11,
            12, 13, 14, 15,
        )
    )
    val reader = TextureReader(cfg)
    
    reader.io.texture.vaddr           := U"32'h_0000_0000"
    reader.io.texture.spec.width      := U"12'd_4"
    reader.io.texture.spec.height     := U"12'd_4"
    reader.io.texture.spec.pixfmt.bpp := U"6'd_8"
    
    reader.io.uv.payload(0).sign     := io.uv.payload(0)(31)
    reader.io.uv.payload(0).exponent := io.uv.payload(0)(30 downto 23)
    reader.io.uv.payload(0).mantissa := io.uv.payload(0)(22 downto 0)
    reader.io.uv.payload(1).sign     := io.uv.payload(1)(31)
    reader.io.uv.payload(1).exponent := io.uv.payload(1)(30 downto 23)
    reader.io.uv.payload(1).mantissa := io.uv.payload(1)(22 downto 0)
    
    reader.io.uv.valid       := io.uv.valid
    io.uv.ready              := reader.io.uv.ready
    reader.io.data           >> io.data
    reader.io.mem.toAhbLite3 <> rom.io.ahb
}

object TextureReadTest extends App {
    Config.sim.compile(TextureReadTestBench(SapphireCfg())).doSim(this.getClass.getSimpleName) { dut =>
        // Fork a process to generate the reset and the clock on the dut
        dut.io.uv.valid #= false
        dut.clockDomain.forkStimulus(period = 10)
        
        // Sequence of pixels to read.
        val read: Seq[(Float,Float)] = Seq(
            (0.125f, 0.125f), // 0, 0 -> 0
            (0.375f, 0.125f), // 1, 0 -> 1
            (0.125f, 0.375f), // 0, 1 -> 4
            (0.375f, 0.375f), // 1, 1 -> 5
        )
        var pos = 0
        
        // Stream some reads.
        while (pos < read.length) {
            dut.io.uv.valid      #= true
            dut.io.uv.payload(1) #= floatToIntBits(read(pos)._1)
            dut.io.uv.payload(0) #= floatToIntBits(read(pos)._1)
            dut.clockDomain.waitSampling(1)
            if (dut.io.uv.ready.toBoolean) {
                pos += 1
            }
        }
        dut.io.uv.valid #= false
        
        // Let it run for a little while
        dut.clockDomain.waitSampling(10)
    }
}