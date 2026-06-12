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

    /** Transmit shift register. */
    val txbuf = Reg(Bits(8 bits))
    io.miso := txbuf(7)

    /** SPI cycle count. */
    val cycle = RegInit(U(0, 3 bits))

    /** Previous (synchronized) state of `sclk`. */
    val pSclk = RegNext(io.sclk, False)

    /** Previous (synchronized) state of `chipSelect`. */
    val pChipSelect = RegNext(io.chipSelect, False)

    /** Rising edge of the serial clock; the master samples MISO here. */
    val sclkRise = io.sclk && !pSclk

    /** Falling edge of the serial clock; the slave must update MISO here. */
    val sclkFall = !io.sclk && pSclk

    /** Rising edge of chip select. */
    val csRise = io.chipSelect && !pChipSelect

    io.rxd.payload.setAsReg()
    io.rxd.valid.setAsReg()
    io.rxd.valid := False
    io.txd.peek  := False
    io.txd.ready := False
    when(!io.chipSelect) {
        cycle := U(0, 3 bits)
        io.rxd.payload.assignDontCare()
    } elsewhen (csRise) {
        // Load the first byte so its MSB is presented on MISO before the first
        // rising edge (CPHA=0 requires the leading bit to be valid up front).
        // Peek only: the byte is not consumed until it is actually clocked out,
        // so a transfer that ends here does not drop it.
        io.txd.peek := True
        txbuf       := io.txd.payload
    } otherwise {
        // CPHA=0: sample MOSI on the rising edge...
        when(sclkRise) {
            io.rxd.payload := io.rxd.payload(6 downto 0) ## io.mosi
            cycle          := cycle + U(1, 3 bits)
            when(cycle === 7) {
                io.rxd.valid := True
            }
            // First bit of the byte now in `txbuf` is being clocked out; commit
            // to consuming it (chip select is confirmed active for this byte).
            when(cycle === 0) {
                io.txd.ready := True
            }
        }
        // ...and change MISO on the falling edge so the master samples a stable
        // bit. When a byte boundary has just wrapped (cycle === 0) load the next
        // byte instead of shifting, so its MSB appears on MISO. Peek only; the
        // commit happens above when this byte's first bit is clocked out.
        when(sclkFall) {
            when(cycle === 0) {
                io.txd.peek := True
                txbuf       := io.txd.payload
            } otherwise {
                txbuf := txbuf(6 downto 0) ## False
            }
        }
    }
}
