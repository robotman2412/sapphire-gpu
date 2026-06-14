package sapphire.scanout

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.color._
import sapphire.dma._
import spinal.core._
import spinal.lib._

/** A scanout engine for use with ILI9341-based displays using the 8-bit
  * parallel interface.
  */
case class Ili9341Scanout(cfg: SapphireCfg) extends Component {
    val io = new Bundle {

        /** DMA bus that pixel data is read from. */
        val dma = master port DmaBus(cfg.vaddrBits bits)

        /** Framebuffer pixel format. */
        val pixfmt = in port PixelFormat(cfg)
    }
}
