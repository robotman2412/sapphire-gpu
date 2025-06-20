package sapphire.phy.spi

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

object SpiCfg {

    /** Possible SPI clock modes. */
    sealed trait ClkMode;

    /** Dynamic CPOL and CPHA. */
    object clkDyn extends ClkMode;

    /** CPOL=0 and CPHA=0. */
    object cpol0cpha0 extends ClkMode;

    /** CPOL=1 and CPHA=0. */
    object cpol1cpha0 extends ClkMode;

    /** CPOL=0 and CPHA=1. */
    object cpol0cpha1 extends ClkMode;

    /** CPOL=1 and CPHA=1. */
    object cpol1cpha1 extends ClkMode;

    /** Possible support for a particular bit width. */
    sealed trait BitsMode;

    /** Unsupported. */
    object invalid extends BitsMode;

    /** Always half-duplex. */
    object halfDuplex extends BitsMode;

    /** Always full-duplex. */
    object fullDuplex extends BitsMode;

    /** Dynamically half- or full-duplex. */
    object any extends BitsMode;

    /** Configuration that only supports SPI. */
    val SPI_ONLY =
        SpiCfg(with1Bit = fullDuplex, with2Bit = invalid, with4Bit = invalid)

    /** Configuration that supports SPI and QIO. */
    val SPI_DIO =
        SpiCfg(with1Bit = fullDuplex, with2Bit = halfDuplex, with4Bit = invalid)

    /** Configuration that supports SPI and QIO. */
    val SPI_QIO =
        SpiCfg(with1Bit = fullDuplex, with2Bit = invalid, with4Bit = halfDuplex)

    /** Configuration that supports SPI, DIO and QIO. */
    val SPI_DIO_QIO = SpiCfg(
        with1Bit = fullDuplex,
        with2Bit = halfDuplex,
        with4Bit = halfDuplex
    )
}

/** Elaborate-time configuration for SPI PHYs. */
case class SpiCfg(
    /** Possible values for CPOL and CPHA. */
    val mode: SpiCfg.ClkMode = SpiCfg.clkDyn,
    /** Supports 1-bit. */
    val with1Bit: SpiCfg.BitsMode = SpiCfg.any,
    /** Supports 2-bit. */
    val with2Bit: SpiCfg.BitsMode = SpiCfg.any,
    /** Supports 4-bit. */
    val with4Bit: SpiCfg.BitsMode = SpiCfg.any
) {
    val dynDuplex = {
        val init = if (with1Bit != SpiCfg.invalid) { with1Bit }
        else if (with2Bit != SpiCfg.invalid) { with2Bit }
        else { with4Bit }
        if (with1Bit != init && with1Bit != SpiCfg.invalid) {
            true
        } else if (with2Bit != init && with2Bit != SpiCfg.invalid) {
            true
        } else if (with4Bit != init && with4Bit != SpiCfg.invalid) {
            true
        }
        false
    }
}
