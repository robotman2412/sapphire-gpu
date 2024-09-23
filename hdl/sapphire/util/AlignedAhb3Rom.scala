package nebula.util

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import nebula._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._



/** AHB-lite-3 ROM that supports all naturally aligned accesses. */
class AlignedAhb3Rom(
    cfg:        AhbLite3Config,
    content: => Seq[Byte],
    endian:     Endianness = LITTLE
) extends Component {
    val io = new Bundle {
        val ahb = slave port AhbLite3(cfg)
    }
    val exp   = log2Up(cfg.dataWidth/8)
    val mem   = Mem(RomPacker(content, cfg.dataWidth bits, endian))
    val rdata = mem.readSync(
        (io.ahb.HADDR >> exp) resize mem.addressWidth,
        io.ahb.HTRANS(1)
    )
    val HADDR = RegNext(io.ahb.HADDR)
    val HSIZE = RegNext(io.ahb.HSIZE)

    // Defaults (when an unsupported size is used).
    io.ahb.HRESP        := True
    io.ahb.HRDATA.assignDontCare()
    
    io.ahb.HREADYOUT    := True
    for (i <- 0 until log2Up(cfg.dataWidth/8)) {
        when (HSIZE === B(i, 3 bits)) {
            // Set HRESP to ERROR if misaligned.
            io.ahb.HRESP  := HADDR(i-1 downto 0) =/= U(0, i bits)
            // Generate HRDATA mux.
            io.ahb.HRDATA(cfg.dataWidth-1 downto 8<<i) := B(0, cfg.dataWidth - (8<<i) bits)
            io.ahb.HRDATA((8<<i)-1 downto 0) := SpinalMap.list[UInt, Bits](
                HADDR(exp-1 downto i),
                for (j <- 0 until cfg.dataWidth / (8<<i)) yield {
                    j -> rdata((j+1)*(8<<i)-1 downto j*(8<<i))
                }
            )
        }
    }
}

