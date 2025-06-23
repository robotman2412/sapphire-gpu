package sapphire.dma

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

/** Multiplexes DMA access between multiple controllers and one target. */
case class DmaMux(nMasters: Int, abits: BitCount) extends Component {
    val io = new Bundle {

        /** Outgoing interface to DMA target. */
        val target = master port DmaBus(abits)

        /** Incoming interfaces from DMA controllers. */
        val controllers = Vec.fill(nMasters)(slave(DmaBus(abits)))
    }

    /** Active controller bitmask. */
    val select = RegInit(U(0, nMasters bits))

    // Stream connection logic.
    io.target.rdata.ready := False
    io.target.wdata.valid := False
    io.target.wdata.payload.assignDontCare()
    for (i <- 0 until nMasters) {
        io.controllers(i).rdata.valid := False
        io.controllers(i).rdata.payload.assignDontCare()
        io.controllers(i).wdata.ready := False
    }
    for (i <- 0 until nMasters) {
        when(select(i)) {
            io.target.rdata >> io.controllers(i).rdata
            io.target.wdata << io.controllers(i).wdata
        }
    }
}
