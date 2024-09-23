package sapphire

import spinal.core._

// Copyright © 2024, Julian Scheffers, see LICENSE for info

case class SapphireCfg(
    /** Pixel coordinate bit width. */
    pixelBits: Int = 12,
    /** Virtual address bit width. */
    vaddrBits: Int = 32
)
