package nebula.util

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._



/** Find the first set bit in a bit vector. */
object FirstSetBit {
    def apply(x: Bits): Bits = {
        val tmp = Bits(x.getBitsWidth bits)
        tmp(0) := x(0)
        for (i <- 1 until x.getBitsWidth) {
            tmp(i) := x(i-1 downto 0) === B(0, i bits) && x(i)
        }
        return tmp
    }
    def apply(x: UInt): UInt = {
        val tmp = UInt(x.getBitsWidth bits)
        tmp(0) := x(0)
        for (i <- 1 until x.getBitsWidth) {
            tmp(i) := x(i-1 downto 0) === U(0, i bits) && x(i)
        }
        return tmp
    }
    def apply(x: SInt): SInt = {
        val tmp = SInt(x.getBitsWidth bits)
        tmp(0) := x(0)
        for (i <- 1 until x.getBitsWidth) {
            tmp(i) := x(i-1 downto 0) === S(0, i bits) && x(i)
        }
        return tmp
    }
}
