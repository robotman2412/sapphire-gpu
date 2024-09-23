package sapphire.texture

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._
import sapphire._

case class TextureRef(cfg: SapphireCfg) extends Bundle {
    /** Texture format specification. */
    val spec  = TextureSpec(cfg)
    /** Texture virtal address. */
    val vaddr = UInt(cfg.vaddrBits bits)
}

case class TextureSpec(cfg: SapphireCfg) extends Bundle {
    /** Texture width in pixels. */
    val width  = UInt(cfg.pixelBits bits)
    /** Texture height in pixels. */
    val height = UInt(cfg.pixelBits bits)
    /** Pixel format specification. */
    val pixfmt = PixelFormat(cfg)
}

case class PixelFormat(cfg: SapphireCfg) extends Bundle {
    /** Store in big endian as opposed to little endian. */
    val bigEndian = Bool()
    /** Number of bits used per pixel. Must always align to bytes or a power-of-two number of bits. */
    val bpp       = UInt(6 bits)
}
