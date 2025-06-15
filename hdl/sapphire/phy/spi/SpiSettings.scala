package sapphire.phy.spi

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._

/// Settings representing type of SPI.
case class SpiSettings() extends Bundle {
    /// Is full-duplex; uses separate MOSI and MISO lines.
    val fullDuplex = Bool()
    /// Log2 of data bits sent per clock cycle.
    val log2Bits   = UInt(2 bits)
}
