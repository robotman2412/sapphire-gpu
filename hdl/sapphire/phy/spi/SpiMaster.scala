package sapphire.phy.spi

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

/// Runtime-configurable SPI master PHY that transfers one byte at a time.
/// Supports 1 to 4 bits and half- or full-duplex operation.
/// Output pins are first, inputs second; may re-use pins for inputs with certain settings.
/// Always uses CPOL=0, CPHA=0 (mode 0).
case class SpiMaster() extends Component {
    val io = new Bundle {
        /// Settings to use starting next byte transfer.
        val settings = in port SpiSettings()
        /// Data to transmit.
        val txData   = slave Stream (Bits(8 bits))
        /// Data received.
        val rxData   = master Stream (Bits(8 bits))
        /// Data outputs.
        val mosi     = out port Bits(4 bits)
        /// Output enables.
        val mosiEn   = out port Bits(4 bits)
        /// Data inputs.
        val miso     = in port Bits(8 bits)
    }
}
