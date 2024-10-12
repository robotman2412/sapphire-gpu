package sapphire.color

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._
import sapphire._

case class Color() extends Bundle {
    val r = UInt(8 bits)
    val g = UInt(8 bits)
    val b = UInt(8 bits)
    val a = UInt(8 bits)
}

object Color {
    def RGBA(r: UInt, g: UInt, b: UInt, a: UInt): Color = {
        val col    = Color()
        col.r := r
        col.g := g
        col.b := b
        col.a := a
        col
    }
    
    def RGB(r: UInt, g: UInt, b: UInt, a: UInt): Color = RGBA(r, g, b, U"8'xff")
    
    def GREY(v: UInt): Color = RGBA(v, v, v, U"8'xff")
}
