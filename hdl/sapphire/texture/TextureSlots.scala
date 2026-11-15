package sapphire.texture

import sapphire._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

case class TextureSlots(cfg: SapphireCfg) extends Component {
    val io = new Bundle {

        /** APB-3 slave bus over which the host configures textures. */
        val apb = slave port Apb3(Apb3Config(cfg.vaddrBits, 32))

        /** Texture slot outputs for the GPU to use. */
        val textures = out port Vec.fill(cfg.textureSlots)(TextureRef(cfg))
    }

    io.textures.setAsReg()
}
