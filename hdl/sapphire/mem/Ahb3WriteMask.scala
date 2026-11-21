package sapphire.mem

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._

object Ahb3WriteMask {
    def apply(hsize: Bits, haddr: UInt, config: AhbLite3Config): Bits = {
        assert(hsize.getBitsWidth == 3)

        val mask = Bits(config.bytePerWord bits)
        mask := B(0)

        for (sizeExp <- 0 to log2Up(config.bytePerWord)) {
            when(hsize === sizeExp) {
                val size = 1 << sizeExp

                for (offset <- 0 until config.bytePerWord) {
                    mask(offset) := haddr(
                        config.symboleRange
                    ) === (offset & ~(size - 1))
                }
            }
        }

        mask
    }
}
