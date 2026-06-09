package sapphire.interface.cmd

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

/** Debug register read bus. The master drives a register index; the slave
  * returns the selected register value combinationally. There is no handshake:
  * all sources are stable registers / wires, and the master latches the
  * response into its own response machinery.
  */
case class DebugBus(indexBits: BitCount = 16 bits, dataBits: BitCount = 32 bits)
    extends Bundle with IMasterSlave {

    /** Requested debug register index. */
    val index = UInt(indexBits)

    /** Value of the selected debug register, zero-extended. */
    val data = Bits(dataBits)

    override def asMaster() = {
        out(index); in(data)
    }
}
