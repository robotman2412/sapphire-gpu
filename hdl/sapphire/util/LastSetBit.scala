package nebula.util

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._



/** Find the first set bit in a bit vector. */
object LastSetBit {
    def apply(x: Bits): Bits = {
        val tmp = Bits(x.getBitsWidth bits)
        tmp(x.getBitsWidth-1) := x(x.getBitsWidth-1)
        for (i <- 0 until x.getBitsWidth-1) {
            tmp(i) := x(x.getBitsWidth-1 downto i+1) === B(0, x.getBitsWidth-i-1 bits) && x(i)
        }
        return tmp
    }
    def apply(x: UInt): UInt = {
        val tmp = UInt(x.getBitsWidth bits)
        tmp(x.getBitsWidth-1) := x(x.getBitsWidth-1)
        for (i <- 0 until x.getBitsWidth-1) {
            tmp(i) := x(x.getBitsWidth-1 downto i+1) === U(0, x.getBitsWidth-i-1 bits) && x(i)
        }
        return tmp
    }
    def apply(x: SInt): SInt = {
        val tmp = SInt(x.getBitsWidth bits)
        tmp(x.getBitsWidth-1) := x(x.getBitsWidth-1)
        for (i <- 0 until x.getBitsWidth-1) {
            tmp(i) := x(x.getBitsWidth-1 downto i+1) === S(0, x.getBitsWidth-i-1 bits) && x(i)
        }
        return tmp
    }
}
