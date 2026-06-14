package sapphire.scanout

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.color._
import sapphire.dma._
import spinal.core._
import spinal.lib._

/** A scanout engine for use with CRT-controlled displays.
  */
case class CrtScanout(cfg: SapphireCfg) extends Component {
    val io = new Bundle {

        /** DMA bus that pixel data is read from. */
        val dma = master port DmaBus(cfg.vaddrBits bits)

        /** Framebuffer pixel format. */
        val pixfmt = in port PixelFormat(cfg)
    }
}
