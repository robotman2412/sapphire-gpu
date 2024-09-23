package sapphire.texture

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._
import sapphire._
import spinal.lib.experimental.math._

/** Accepts floating-point UV coordinates and reads raw texture data. */
case class TextureReader(cfg: SapphireCfg) extends Component {
    val io = new Bundle {
        /** 0-1 texture X coordinate. */
        val u       = in port Floating32()
        /** 0-1 texture Y coordinate. */
        val v       = in port Floating32()
        /** Texture reference. */
        val texture = in port TextureRef(cfg)
    }
}
