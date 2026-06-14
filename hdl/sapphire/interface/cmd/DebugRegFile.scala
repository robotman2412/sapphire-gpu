package sapphire.interface.cmd

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.lib._

/** Aggregates a set of debug taps and serves them to a [[CmdEngine]] over a
  * [[DebugBus]]. Each tap is provided as an input port at its own width; the
  * bus index selects one, zero-extended to the bus data width. Out-of-range
  * indices read as zero.
  *
  * Reads return a latched snapshot of every tap rather than its live value. The
  * snapshot is (re)taken whenever the bus master pulses `debug.latch`, so all
  * registers present a coherent view of the same instant.
  */
case class DebugRegFile(taps: Seq[BitCount]) extends Component {
    val io = new Bundle {

        /** Debug bus toward the command engine. */
        val debug = slave port DebugBus()

        /** Debug tap inputs, one per register index. */
        val regs = Vec(taps.map(w => in port Bits(w)))
    }

    /** Latched snapshot of every tap, captured on `debug.latch`. */
    val snapshot = Vec(taps.map(w => RegInit(B(0, w))))
    when(io.debug.latch) {
        (snapshot, io.regs).zipped.foreach(_ := _)
    }

    io.debug.data := B(0)
    switch(io.debug.index) {
        for (i <- taps.indices) {
            is(i) { io.debug.data := snapshot(i).resized }
        }
    }
}
