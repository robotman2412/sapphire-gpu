package sapphire.util

// Copyright (c) 2024 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import spinal.core._
import spinal.lib.experimental.math._

object FloatUtil {

    /** Convert a float to a UInt in the range [0,1), used for UV math. */
    def floatToUint01(num: Floating): UInt =
        floatToUint01(num, num.mantissaSize + 1)

    /** Convert a float to a UInt in the range [0,1), used for UV math. */
    def floatToUint01(num: Floating, width: Int): UInt = {
        val mantissa = Cat(
            num.exponent =/= B(0, num.exponentSize bits),
            num.mantissa,
            B(0, width bits)
        ).asUInt
        val shr      = UInt(num.exponentSize + 1 bits)
        shr := U(
            (1 << (num.exponentSize - 1)) + width - 2,
            num.exponentSize + 1 bits
        ) - num.exponent.asUInt
        (mantissa >> shr)(width - 1 downto 0)
    }
}
