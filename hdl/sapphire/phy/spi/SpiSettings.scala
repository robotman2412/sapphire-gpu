package sapphire.phy.spi

// Copyright (c) 2025 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._

/** Settings representing a type of SPI. CPOL and CPHA should not be changed
  * during a transfer.
  */
case class SpiSettings() extends Bundle {

    /** Is full-duplex; uses separate MOSI and MISO lines. */
    val fullDuplex = Bool()

    /** Log2 of data bits sent per clock cycle. */
    val log2Bits = UInt(2 bits)

    /** Clock polarity; 0 -> active-high, 1 -> active-low. */
    val cpol = Bool()

    /** Clock phase; 0 -> activating edge, 1 -> deactivating edge. */
    val cpha = Bool()
}
