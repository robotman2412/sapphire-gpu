package sapphire.util

// Copyright (c) 2025 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._

object ByteReverse {
    def apply(data: Bits): Bits = {
        val bytes     = (data.getBitsWidth + 7) / 8
        val dataBytes = data.resize(bytes * 8)
        val revBytes  = Bits(bytes * 8 bits)
        for (i <- 0 until bytes) {
            val x = bytes - i - 1
            revBytes(i * 8 + 7 downto i * 8) := dataBytes(
                x * 8 + 7 downto x * 8
            )
        }
        revBytes
    }
}
