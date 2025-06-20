package sapphire.dma

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

/** Direct Memory Access setup bus. */
case class DmaSetupBus(abits: BitCount) extends Bundle with IMasterSlave {

    /** Trigger DMA setup if ready. */
    val setup = Bool()

    /** Ready for DMA to be set up. */
    val setupReady = Bool()

    /** Trigger DMA teardown if ready. */
    val teardown = Bool()

    /** Ready for DMA to be torn down. */
    val teardownReady = Bool()

    /** Is a write access (instead of read). */
    val write = Bool()

    /** DMA start address. */
    val addr = UInt(abits)

    override def asMaster() = {
        out(setup, teardown, write, addr); in(setupReady, teardownReady)
    }
}

/** Direct Memory Access bus. */
case class DmaBus(abits: BitCount) extends Bundle with IMasterSlave {

    /** DMA setup. */
    val setup = DmaSetupBus(abits)

    /** Write data. */
    val wdata = Stream(Bits(8 bits))

    /** Read data. */
    val rdata = Stream(Bits(8 bits))

    override def asMaster() = {
        master(setup, wdata); slave(rdata)
    }
}
