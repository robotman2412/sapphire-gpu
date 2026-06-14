package sapphire.scanout

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.color._
import sapphire.dma._
import spinal.core._
import spinal.lib._

/** Describes the video timings for a scanout engine. */
case class VideoTimings(cfg: SapphireCfg) extends Bundle {

    /** Core to pixel clock divider ratio. Should be at least 2. */
    val clkDiv = UInt(8 bits)

    /** Horizontal timing settings. */
    val horizontal = VideoAxisTimings(cfg)

    /** Vertical timing settings. */
    val vertical = VideoAxisTimings(cfg)
}

/** Describes the horizontal or vertical timings for VideoTimings. */
case class VideoAxisTimings(cfg: SapphireCfg) extends Bundle {

    /** Front porch (blanking before). */
    val front = UInt(cfg.vaddrBits bits)

    /** Video length. */
    val video = UInt(cfg.vaddrBits bits)

    /** Back porch (blanking after). */
    val back = UInt(cfg.vaddrBits bits)

    /** Sync pulse length. */
    val sync = UInt(cfg.vaddrBits bits)
}
