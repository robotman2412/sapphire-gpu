package sapphire.util

import spinal.core._

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

object TestBit {

    /** Simple helper that tests if any bit in a mask is set. */
    def apply(a: Bits, b: Bits): Bool = {
        (a & b) =/= 0
    }

    /** Simple helper that tests if any bit in a mask is set. */
    def apply(a: UInt, b: UInt): Bool = {
        (a & b) =/= 0
    }

    /** Simple helper that tests if any bit in a mask is set. */
    def apply(a: SInt, b: SInt): Bool = {
        (a & b) =/= 0
    }
}
