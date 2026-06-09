package sapphire.phy.spi

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._
import sapphire.util.Vacuum

/** A simple 1-bit full duplex SPI slave implementation. Uses CPOL=0, CPHA=0. It
  * is implied that the MISO output enable is connected to the chip select
  * externally.
  */
case class SimpleSpiSlave() extends Component {
    val io = new Bundle {

        /** Received data flow. */
        val rxd = master port Flow(Bits(8 bits))

        /** Sent data vacuum. */
        val txd = slave port Vacuum(Bits(8 bits))

        /** Active-high chip select input. */
        val chipSelect = in port Bool()

        /** Serial clock input. */
        val sclk = in port Bool()

        /** Serial receive data. */
        val mosi = in port Bool()

        /** Serial transmit data. */
        val miso = out port Bool()
    }

    // The SPI bus signals are asynchronous to the FPGA clock domain (the whole
    // design is one clock domain and these come straight off the master). They
    // must be synchronized before use; sampling the raw pins and doing edge
    // detection against a single flip-flop allows metastability to manifest as
    // doubled/missed clock edges (shifted data) or corrupt samples (zeroes).
    val sclk        = BufferCC(io.sclk, False)
    val mosi        = BufferCC(io.mosi, False)
    val chipSelect  = BufferCC(io.chipSelect, False)

    /** Transmit shift register. */
    val txbuf = Reg(Bits(8 bits))
    io.miso := txbuf(7)

    /** SPI cycle count. */
    val cycle = RegInit(U(0, 3 bits))

    /** Previous (synchronized) state of `sclk`. */
    val pSclk = RegNext(sclk, False)

    /** Previous (synchronized) state of `chipSelect`. */
    val pChipSelect = RegNext(chipSelect, False)

    /** Rising edge of the serial clock; the master samples MISO here. */
    val sclkRise = sclk && !pSclk

    /** Falling edge of the serial clock; the slave must update MISO here. */
    val sclkFall = !sclk && pSclk

    /** Rising edge of chip select. */
    val csRise = chipSelect && !pChipSelect

    io.rxd.payload.setAsReg()
    io.rxd.valid.setAsReg()
    io.rxd.valid := False
    io.txd.ready := False
    when(!chipSelect) {
        cycle := U(0, 3 bits)
        io.rxd.payload.assignDontCare()
    } elsewhen (csRise) {
        // Load the first byte so its MSB is presented on MISO before the first
        // rising edge (CPHA=0 requires the leading bit to be valid up front).
        io.txd.ready := True
        txbuf        := io.txd.payload
    } otherwise {
        // CPHA=0: sample MOSI on the rising edge...
        when(sclkRise) {
            io.rxd.payload := io.rxd.payload(6 downto 0) ## mosi
            cycle          := cycle + U(1, 3 bits)
            when(cycle === 7) {
                io.rxd.valid := True
            }
        }
        // ...and change MISO on the falling edge so the master samples a stable
        // bit. When a byte boundary has just wrapped (cycle === 0) load the next
        // byte instead of shifting, so its MSB appears on MISO.
        when(sclkFall) {
            when(cycle === 0) {
                io.txd.ready := True
                txbuf        := io.txd.payload
            } otherwise {
                txbuf := txbuf(6 downto 0) ## False
            }
        }
    }
}
