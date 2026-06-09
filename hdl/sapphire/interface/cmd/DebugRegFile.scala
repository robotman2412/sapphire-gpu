package sapphire.interface.cmd

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

/** Aggregates a set of debug taps and serves them to a [[CmdEngine]] over a
  * [[DebugBus]]. Each tap is provided as an input port at its own width; the
  * bus index selects one, zero-extended to the bus data width. Out-of-range
  * indices read as zero.
  */
case class DebugRegFile(taps: Seq[BitCount]) extends Component {
    val io = new Bundle {

        /** Debug bus toward the command engine. */
        val debug = slave port DebugBus()

        /** Debug tap inputs, one per register index. */
        val regs = Vec(taps.map(w => in port Bits(w)))
    }

    io.debug.data := B(0)
    switch(io.debug.index) {
        for (i <- taps.indices) {
            is(i) { io.debug.data := io.regs(i).resized }
        }
    }
}
