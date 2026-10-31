package sapphire.dma

// Copyright (c) 2025 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire.util._
import spinal.core._
import spinal.lib._

/** Multiplexes DMA access between multiple controllers and one target. */
case class DmaMux(nMasters: Int, abits: Int) extends Component {
    val io = new Bundle {

        /** Outgoing interface to DMA target. */
        val target = master port DmaBus(abits bits)

        /** Incoming interfaces from DMA controllers. */
        val controllers = Vec.fill(nMasters)(slave(DmaBus(abits bits)))
    }

    val setup      = io.controllers.map(_.setup.setup).asBits()
    val teardown   = io.controllers.map(_.setup.teardown).asBits()
    val arbiter    = FirstSetBit(setup)
    val isDmaSetup = RegInit(False)
    val select     = RegInit(B(0, nMasters bits))

    // Reservation logic.
    when(!isDmaSetup) {
        // Selection happens in the setup phase.
        select := arbiter
    }
    when(select =/= 0) {
        // Detect DMA active conditions to lock in selection.
        when(io.target.setup.setup && io.target.setup.setupReady) {
            isDmaSetup := True
            select     := select // Cancel the update on setup firing.
        } elsewhen (io.target.setup.teardown && io.target.setup.teardownReady) {
            isDmaSetup := False
        }
    }

    // Stream connection logic.
    io.target.setup.setup    := False
    io.target.setup.teardown := False
    io.target.setup.write.assignDontCare()
    io.target.setup.addr.assignDontCare()
    io.target.rdata.ready    := False
    io.target.wdata.valid    := False
    io.target.wdata.payload.assignDontCare()
    for (i <- 0 until nMasters) {
        io.controllers(i).setup.setupReady    := False
        io.controllers(i).setup.teardownReady := False
        io.controllers(i).rdata.valid         := False
        io.controllers(i).rdata.payload.assignDontCare()
        io.controllers(i).wdata.ready         := False
    }
    for (i <- 0 until nMasters) {
        when(select(i)) {
            io.target <> io.controllers(i)
        }
    }
}
