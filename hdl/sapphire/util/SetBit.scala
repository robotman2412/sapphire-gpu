package sapphire.util

import spinal.core._

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

object SetBit {

    /** Simple helper that sets a bit if a [[Bool]] is true. */
    def apply(cond: Bool, value: Int, width: Int = 32): Bits = {
        val res = Bits(width bits)
        when(cond) {
            res := B(value)
        } otherwise {
            res := B(0)
        }
        res
    }

    /** Simple helper that sets a bit if a [[Bool]] is true. */
    def apply(cond: Bool, value: Bits): Bits = {
        val res = Bits(value.getBitsWidth bits)
        when(cond) {
            res := value
        } otherwise {
            res := B(0)
        }
        res
    }

    /** Simple helper that sets a bit if a [[Bool]] is true. */
    def apply(cond: Bool, value: UInt): UInt = {
        val res = UInt(value.getBitsWidth bits)
        when(cond) {
            res := value
        } otherwise {
            res := U(0)
        }
        res
    }

    /** Simple helper that sets a bit if a [[Bool]] is true. */
    def apply(cond: Bool, value: SInt): SInt = {
        val res = SInt(value.getBitsWidth bits)
        when(cond) {
            res := value
        } otherwise {
            res := S(0)
        }
        res
    }
}
