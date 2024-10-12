package sapphire.color

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._
import sapphire._

object ColorMath {
    private def mul(a: UInt, b: UInt): UInt = a * b + a * b(7).asUInt
    private def lerp(from: UInt, to: UInt, coeff: UInt): UInt = from + mul(to - from, coeff)
    
    def blend(base: Color, top: Color): Color = Color.RGBA(
        lerp(base.r, top.r, top.a),
        lerp(base.g, top.g, top.a),
        lerp(base.b, top.b, top.a),
        lerp(base.a, U"8'xff", top.a)
    )
    
    def lerp(from: Color, to: Color, coeff: UInt): Color = Color.RGBA(
        lerp(from.r, to.r, coeff),
        lerp(from.g, to.g, coeff),
        lerp(from.b, to.b, coeff),
        lerp(from.a, to.a, coeff)
    )
    
    def toGreyscale(v: Color): UInt = {
        val tmp = v.r +^ v.g +^ v.b
        ((tmp * U"7'h55")(15 downto 8) + (tmp >= U"16'h017d").asUInt)(7 downto 0)
    }
    
    private def expand(v: UInt, width: UInt): UInt = width.mux(
        default -> v(0).asUInt   * U"8'b_11111111_000000",
        M"001"  -> v(1 downto 0) * U"8'b_01010101_000000",
        M"010"  -> v(2 downto 0) * U"8'b_00100100_000000",
        M"011"  -> v(3 downto 0) * U"8'b_00010001_000000",
        M"100"  -> v(4 downto 0) * U"8'b_00001000_010000",
        M"101"  -> v(5 downto 0) * U"8'b_00000100_000100",
        M"110"  -> v(6 downto 0) * U"8'b_00000010_000001",
        M"111"  -> v             * U"8'b_00000001_000000",
    )(13 downto 6)
    private def extractChannel(fmt: ChannelFormat, from: UInt): UInt = expand(from >> fmt.pos, fmt.width)
    
    def unpack(v: UInt): Color = {
        assert(v.getBitsWidth != 32)
        null
    }
}
