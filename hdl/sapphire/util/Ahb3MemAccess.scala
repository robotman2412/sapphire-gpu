package sapphire.util

// Copyright (c) 2024 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._

object Ahb3MemAccess {

    /** An AHB-lite-3 port for accessing an existing memory. */
    def apply(
        /** AHB-lite-3 configuration. */
        cfg: AhbLite3Config,
        /** Memory to access. */
        mem: Mem[Bits],
        /** Whether to allow write accesses. */
        writeable: Boolean = true,
        /** If writeable, use independent write port. If not writeable, raise
          * HRESP on write access.
          */
        writeMode: Boolean = false
    ): AhbLite3 = {
        val ahb = AhbLite3(cfg)
        val exp = log2Up(cfg.dataWidth / 8)

        val HADDR  = Reg(UInt(cfg.addressWidth bits))
        val HSIZE  = Reg(Bits(3 bits))
        val HWRITE = RegInit(False)
        val HTRANS = RegInit(False)
        when(ahb.HREADYOUT) {
            HADDR  := ahb.HADDR
            HSIZE  := ahb.HSIZE
            HWRITE := ahb.HWRITE
            HTRANS := ahb.HTRANS =/= AhbLite3.IDLE
        }

        // Write interface.
        val wmask = writeable generate Bits(cfg.dataWidth bits)
        val wdata = writeable generate Bits(cfg.dataWidth bits)
        if (writeable) {
            wmask := B(0, cfg.dataWidth bits)
            wdata.assignDontCare
            if (writeMode) {
                mem.write(HADDR, wdata, HWRITE, wmask)
            }
        }

        // Read interface.
        val rdata = if (writeable && !writeMode) {
            val we   = HWRITE && HTRANS
            val addr = we.mux(
                False -> ahb.HADDR,
                True  -> HADDR
            )
            mem.readWriteSync(addr >> exp, wdata, True, we, wmask)
        } else {
            mem.readSync(
                (ahb.HADDR >> exp) resize mem.addressWidth,
                ahb.HTRANS(1)
            )
        }

        ahb.HRESP     := True
        ahb.HRDATA.assignDontCare
        ahb.HREADYOUT := True

        for (i <- 0 to exp) {
            val width = 8 << i
            when(HSIZE === B(i, 3 bits)) {
                // Set HRESP to ERROR if misaligned.
                if (i > 0) {
                    ahb.HRESP := HADDR(i - 1 downto 0) =/= U(0, i bits)
                } else {
                    ahb.HRESP := False
                }
                // Generate wdata mux.
                if (writeable) {
                    for (j <- 0 until cfg.dataWidth / width) {
                        when(HADDR(exp - 1 downto i) === U(j, exp - i bits)) {
                            wmask(width * (j + 1) - 1 downto width * j) := B(
                                (1 << width) - 1,
                                width bits
                            )
                        }
                        wdata(width * (j + 1) - 1 downto width * j) := ahb
                            .HWDATA(width - 1 downto 0)
                    }
                }
                // Generate HRDATA mux.
                ahb.HRDATA(cfg.dataWidth - 1 downto 8 << i).assignDontCare
                ahb.HRDATA((8 << i) - 1 downto 0) := SpinalMap.list[UInt, Bits](
                    HADDR(exp - 1 downto i),
                    for (j <- 0 until cfg.dataWidth / (8 << i)) yield {
                        j -> rdata((j + 1) * (8 << i) - 1 downto j * (8 << i))
                    }
                )
            }
        }

        when(!HTRANS) {
            ahb.HREADYOUT := True
            ahb.HRESP     := False
        }

        ahb
    }
}
