package sapphire.util

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

case class Vacuum[T <: Data](DType: HardType[T])
    extends Bundle
    with IMasterSlave {

    /** Data to be received when ready. */
    val payload = DType()

    /** Will now receive data. */
    val ready = Bool()

    override def asMaster() = {
        out(ready); in(payload)
    }
}
