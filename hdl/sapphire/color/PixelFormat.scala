package sapphire.color

// Copyright (c) 2024 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import sapphire._

case class ChannelFormat(cfg: SapphireCfg) extends Bundle {

    /** Bit width of this channel minus one. */
    val width = UInt(3 bits)

    /** Bit position of this channel. */
    val pos = UInt(5 bits)
}

object PixelFormatType extends SpinalEnum(binarySequential) {

    /** Greyscale; channel 0 defines brightness. */
    val GREY = newElement()

    /** Greyscale with alpha; channel 0 defines brightness, channel 3 defines
      * alpha.
      */
    val GREYA = newElement()

    /** RGB; channels 0-2 define red, green and blue respectively. */
    val RGB = newElement()

    /** RGBA; channels 0-3 define red, green, blue and alpha respectively. */
    val RGBA = newElement()
}

case class PixelFormat(cfg: SapphireCfg) extends Bundle {

    /** Number of bits used per pixel. Must always align to bytes or a
      * power-of-two number of bits.
      */
    val bpp = UInt(6 bits)

    /** Pixel format type. */
    val fmtType = PixelFormatType()

    /** Channel formats. */
    val channel = Vec.fill(4)(ChannelFormat(cfg))
}
