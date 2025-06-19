package sapphire.phy.spi

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

object SpiMaster {
    /// Represents an action for the SPI master to take.
    object ActionType extends SpinalEnum {
        /// Send a single clock cycle, regardless of bit width.
        val DUMMY_CLOCK     = newElement()
        /// Send a single byte; OEs are set and rxData will not be valid.
        val SEND_BYTE       = newElement()
        /// Receive a single byte; OEs are not set and rxData will be valid.
        val RECV_BYTE       = newElement()
        /// Transcieve a single byte; OEs are set and rxData will be valid.
        val TRANSCEIVE_BYTE = newElement()
    }

    /// A command issued for the SPI master to perform.
    case class Action() extends Bundle {
        /// Action to perform.
        val atype    = ActionType()
        /// Send data, if applicable.
        val data     = Bits(8 bits)
        /// SPI settings to use for this action.
        val settings = SpiSettings()
    }

    /// A bus for interfacing with an SPI master PHY.
    case class Bus() extends Bundle with IMasterSlave {
        /// Action to perform and data to send.
        val action = Stream(Action())
        /// Data received.
        val rxData = Stream(Bits(8 bits))

        override def asMaster() = {
            master(action); slave(rxData)
        }
    }
}

/// Runtime-configurable SPI master PHY that transfers one byte at a time.
/// Supports 1 to 4 bits, both CPOL/CPHA modes and half- or full-duplex operation.
/// Output pins are first, inputs second; may re-use pins for inputs with certain settings.
/// Holds `sclk` at the level according to the last issued action's CPOL.
/// To change CPOL, issue a dummy clock action between deselecting the last and selecting the next chip.
case class SpiMaster(cfg: SpiCfg = SpiCfg()) extends Component {
    import SpiMaster._
    import SpiCfg._

    val io = new Bundle {
        /// A bus for interfacing with an SPI master PHY.
        val bus = slave port SpiMaster.Bus()

        /// Explicit SPI clock output that follows CPOL and CPHA rules.
        val sclk   = out port Bool()
        /// Data output pins.
        val mosi   = out port Bits(4 bits)
        /// Output enables.
        val mosiEn = out port Bits(4 bits)
        /// Data input pins.
        val miso   = in port Bits(8 bits)

        /// Is currently busy.
        val busy = out port Bool()
    }

    /// Start transaction trigger; is false if the settings are invalid.
    val trigger = io.bus.action.fire && io.bus.action.settings.log2Bits =/= 3

    /// Clock that rises in phase with this component's clock.
    /// Should fall at a 180 degree offset from the active component clock edge.
    val syncClk   = ClockDomain.current.readClockWire ^ Bool(
        ClockDomain.current.config.clockEdge == FALLING
    )
    /// Clock that falls in phase with this component's clock.
    /// Should rise at a 180 degree offset from the active component clock edge.
    val offsetClk = ClockDomain.current.readClockWire ^ Bool(
        ClockDomain.current.config.clockEdge == RISING
    )

    /// Enable register for SCLK.
    val sclkEn      = RegInit(False)
    /// Enable for `syncClk`.
    val syncClkEn   = (cfg.mode == SpiCfg.clkDyn) generate RegInit(False)
    /// Enable for `offsetClk`.
    val offsetClkEn = (cfg.mode == SpiCfg.clkDyn) generate RegInit(False)
    /// Latched value of CPOL.
    val lastCpol    = {
        if (cfg.mode == SpiCfg.clkDyn) {
            RegInit(False)
        } else {
            Bool(cfg.mode == cpol1cpha0 || cfg.mode == cpol1cpha1)
        }
    }
    /// Remaining number of cycles.
    val cycles      = RegInit(U(0, 4 bits))
    io.busy := cycles =/= 0
    /// Current settings.
    val settings           = Reg(SpiSettings())
    /// Inputs shifted to account for outputs.
    val shiftCorrectedMiso = Bits(4 bits)
    /// Whether the transaction is receiving data.
    val isRecv             = RegInit(False)
    /// Transmit shift register.
    val txBuf              = Reg(Bits(8 bits))
    /// Receive shift register.
    val rxBuf              = Reg(Bits(8 bits))
    io.bus.rxData.payload := rxBuf
    // This must be a register to ensure that the output is valid for one cycle after the next transaction potentially starts.
    io.bus.rxData.valid.setAsReg()
    /// Output enables is also a register.
    io.mosiEn.setAsReg()
    io.mosiEn.init(B(0, 4 bits))

    when(settings.fullDuplex && Bool(cfg.dynDuplex)) {
        // Shift amount depends on the number of bits.
        shiftCorrectedMiso.assignDontCare
        when(settings.log2Bits === 0 && Bool(cfg.with1Bit != SpiCfg.invalid)) { // 1 bit
            shiftCorrectedMiso := io.miso(1).asBits.resized
        }
        when(settings.log2Bits === 1 && Bool(cfg.with2Bit != SpiCfg.invalid)) { // 2 bits
            shiftCorrectedMiso := io.miso(3 downto 2).resized
        }
        when(settings.log2Bits === 2 && Bool(cfg.with4Bit != SpiCfg.invalid)) { // 4 bits
            shiftCorrectedMiso := io.miso(7 downto 4).resized
        }
    } otherwise {
        // No need to shift miso if not full-duplex.
        shiftCorrectedMiso := io.miso(3 downto 0)
    }

    when(cycles === 1 && isRecv) {
        io.bus.rxData.valid := True
    } elsewhen (io.bus.rxData.ready) {
        io.bus.rxData.valid := False
    }

    // This is pipelined a bit, so, to not waste cycles, we need to accept commands even if `cycles` is 1.
    io.bus.action.ready := cycles <= 1
    when(!io.bus.rxData.ready && io.bus.rxData.valid) {
        // If whatever is receiving is not ready, don't potentially overwrite it by starting a new action.
        io.bus.action.ready := False
    }

    // SCLK output mux.
    when(sclkEn) {
        if (cfg.mode == SpiCfg.cpol0cpha0 || cfg.mode == SpiCfg.cpol1cpha1) {
            io.sclk := offsetClk
        } else if (cfg.mode != SpiCfg.clkDyn) {
            io.sclk := syncClk
        } else {
            io.sclk := (syncClk && syncClkEn) || (offsetClk && offsetClkEn)
        }
    } otherwise {
        io.sclk := lastCpol
    }

    // Finite state machine to handle the SPI transaction.
    when(trigger) {
        // Save the settings.
        settings := io.bus.action.settings
        // Configure SCLK.
        if (cfg.mode == SpiCfg.clkDyn) {
            val doSync =
                io.bus.action.settings.cpol ^ io.bus.action.settings.cpha
            syncClkEn   := doSync
            offsetClkEn := !doSync
            lastCpol    := io.bus.action.settings.cpol
        }
        sclkEn   := True
        // Determine whether receiving data is valid.
        isRecv   := io.bus.action.payload.atype.asBits(1)
        // Set the number of cycles to send.
        when(io.bus.action.payload.atype === ActionType.DUMMY_CLOCK) {
            cycles := U(1, 4 bits)
        } otherwise {
            cycles := U"4'b1000" |>> io.bus.action.settings.log2Bits.resized
        }
        // Set the output enables.
        when(io.bus.action.payload.atype.asBits(0)) {
            // Sending data, so set the output enables.
            val log2Bits = io.bus.action.settings.log2Bits
            io.mosiEn.assignDontCare
            when(log2Bits === 0 && Bool(cfg.with1Bit != SpiCfg.invalid)) { // 1 bit
                io.mosiEn := B"4'b0001"
            }
            when(log2Bits === 1 && Bool(cfg.with2Bit != SpiCfg.invalid)) { // 2 bits
                io.mosiEn := B"4'b0011"
            }
            when(log2Bits === 2 && Bool(cfg.with4Bit != SpiCfg.invalid)) { // 4 bits
                io.mosiEn := B"4'b1111"
            }
        } otherwise {
            // Not sending data, so clear the output enables.
            io.mosiEn := B(0, 4 bits)
        }
    } elsewhen (cycles =/= 0) {
        when(cycles === 1) {
            // If we are in the last cycle, clear the output enables.
            io.mosiEn := B(0, 4 bits)
            // And turn off the clock.
            if (cfg.mode == SpiCfg.clkDyn) {
                syncClkEn   := False
                offsetClkEn := False
            }
            sclkEn    := False
        }
        // Decrement the cycles.
        cycles := cycles - 1
    }

    // MOSI outputs mux.
    io.mosi.assignDontCare()
    when(settings.log2Bits === 0 && Bool(cfg.with1Bit != SpiCfg.invalid)) { // 1 bit
        io.mosi := B"3'b000" ## txBuf(7)
    }
    when(settings.log2Bits === 1 && Bool(cfg.with2Bit != SpiCfg.invalid)) { // 2 bits
        io.mosi := B"2'b00" ## txBuf(7 downto 6)
    }
    when(settings.log2Bits === 2 && Bool(cfg.with4Bit != SpiCfg.invalid)) { // 4 bits
        io.mosi := txBuf(7 downto 4)
    }

    // Transmit shift register mux.
    when(trigger) {
        // Load the transmit buffer with the data to send.
        txBuf := io.bus.action.payload.data
    } otherwise {
        // Shift amount depends on the number of bits.
        switch(settings.log2Bits) {
            is(0) { // 1 bit
                txBuf := txBuf |<< 1
            }
            is(1) { // 2 bits
                txBuf := txBuf |<< 2
            }
            is(2) { // 4 bits
                txBuf := txBuf |<< 4
            }
            default {
                txBuf.assignDontCare()
            }
        }
    }

    // Receive shift register mux.
    when(cycles =/= 0) {
        // Shift amount depends on the number of bits.
        rxBuf.assignDontCare
        when(settings.log2Bits === 0 && Bool(cfg.with1Bit != SpiCfg.invalid)) { // 1 bit
            rxBuf := rxBuf(6 downto 0) ## shiftCorrectedMiso(0)
        }
        when(settings.log2Bits === 1 && Bool(cfg.with2Bit != SpiCfg.invalid)) { // 2 bits
            rxBuf := rxBuf(5 downto 0) ## shiftCorrectedMiso(1 downto 0)
        }
        when(settings.log2Bits === 2 && Bool(cfg.with4Bit != SpiCfg.invalid)) { // 4 bits
            rxBuf := rxBuf(3 downto 0) ## shiftCorrectedMiso(3 downto 0)
        }
    }
}
