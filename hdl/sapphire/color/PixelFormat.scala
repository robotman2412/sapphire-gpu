package sapphire.color

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._
import sapphire._

case class ChannelFormat(cfg: SapphireCfg) extends Bundle {
    /** Bit width of this channel minus one. */
    val width = UInt(3 bits)
    /** Bit position of this channel. */
    val pos   = UInt(5 bits)
}

case class PixelFormatType(cfg: SapphireCfg) extends SpinalEnum(binarySequential) {
    /** Greyscale; channel 0 defines brightness. */
    val GREYSCALE = newElement()
    /** RGB; channels 0-2 define red, green and blue respectively. */
    val RGB       = newElement()
    /** RGBA; channels 0-3 define red, green, blue and alpha respectively. */
    val RGBA      = newElement()
}

case class PixelFormat(cfg: SapphireCfg) extends Bundle {
    /** Number of bits used per pixel. Must always align to bytes or a power-of-two number of bits. */
    val bpp     = UInt(6 bits)
    /** Pixel format type. */
    val fmtType = PixelFormatType(cfg)
    /** Channel formats. */
    val red     = Vec.fill(4)(ChannelFormat(cfg))
}
