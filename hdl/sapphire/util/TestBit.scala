package sapphire.util

import spinal.core._

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

object TestBit {

    /** Simple helper that tests if any bit in a mask is set. */
    def apply[T <: Data](a: T, b: T): Bool = {
        (a.asBits & b.asBits) =/= 0
    }

    /** Simple helper that tests if any bit in a mask is set. */
    def apply[T <: Data](a: T, b: Int): Bool = {
        (a.asBits & B(b)) =/= 0
    }
}
