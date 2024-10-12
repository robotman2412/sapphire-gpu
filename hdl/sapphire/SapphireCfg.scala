package sapphire

import spinal.core._

// Copyright © 2024, Julian Scheffers, see LICENSE for info

case class SapphireCfg(
    /** Pixel coordinate bit width. */
    coordBits: Int           = 12,
    /** Virtual address bit width. */
    vaddrBits: Int           = 32,
    /** Pipeline topology configuration. */
    plCfg:     SapphirePlCfg = SapphirePlCfg()
)

case class SapphirePlCfg(
    /** Use a stage for f2i / i2f. */
    fconvStage:     Boolean = true,
    /** Normal multiply stage count. */
    normMulStages:  Int     = 3,
    /** Small (8-bit) multiply stage count. */
    smallMulStages: Int     = 2,
    /** Use a stage for address calculation. */
    acalcStage:     Boolean = true
)
