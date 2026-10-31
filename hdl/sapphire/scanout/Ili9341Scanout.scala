package sapphire.scanout

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.color._
import sapphire.dma._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import sapphire.util.SetBit
import sapphire.util.TestBit

object Ili9341Scanout {
    object Fsm extends SpinalEnum {
        val IDLE      = newElement()
        val H_RES_CMD = newElement()
        val H_RES     = newElement()
        val V_RES_CMD = newElement()
        val V_RES     = newElement()
        val WRITE_CMD = newElement()
        val DATA      = newElement()
    }

    /** A byte to transmit to an ILI9341-based display. */
    case class Packet() extends Bundle {

        /** Register select; 0: Command, 1: Data. */
        val regSel = out port Bool()

        /** Data or command byte. */
        val data = out port Bits(8 bits)
    }

    object SlowSink {
        object State extends SpinalEnum {
            val IDLE, SETUP, STROBE, HOLD = newElement()
        }
    }

    /** Streams out the data with configurable setup and hold time and strobe
      * width.
      */
    case class SlowSink(setup: Int = 10, strobe: Int = 10, hold: Int = 0)
        extends Component {
        import SlowSink._
        assert(setup >= 1)
        assert(strobe >= 1)
        assert(hold >= 0)

        val io = new Bundle {
            val in     = slave port Stream(Packet())
            val strobe = out port Bool()
            val regSel = out port Bool()
            val data   = out port Bits(8 bits)
        }

        val state   = RegInit(State.IDLE)
        val counter = RegInit(U(0, log2Up(setup.max(strobe).max(hold)) bits))

        io.in.ready := state === State.IDLE
        io.strobe   := state === State.STROBE
        io.regSel.setAsReg()
        io.data.setAsReg()

        when(counter === 0) {
            switch(state) {
                is(State.SETUP) {
                    state   := State.STROBE
                    counter := strobe - 1
                }
                is(State.STROBE) {
                    if (hold > 0) {
                        state   := State.HOLD
                        counter := hold - 1
                    } else {
                        state := State.IDLE
                    }
                }
                is(State.HOLD) {
                    state := State.IDLE
                }
            }
        } otherwise {
            counter := counter - 1
        }

        when(io.in.fire) {
            state     := State.SETUP
            counter   := setup - 1
            io.regSel := io.in.regSel
            io.data   := io.in.data
        }
    }
}

/** A scanout engine for use with ILI9341-based displays using the 8-bit
  * parallel interface.
  */
case class Ili9341Scanout(cfg: SapphireCfg) extends Component {
    import Ili9341Scanout._
    val io = new Bundle {

        /** APB slave bus. */
        val apb = slave port Apb3(8, 32)

        /** DMA bus that pixel data is read from. */
        val dma = master port DmaBus(cfg.vaddrBits bits)

        /** Active-high display reset output. */
        val resetOut = out port Bool()

        /** Active-high display chip select output. */
        val chipSelect = out port Bool()

        /** Display command and data stream. */
        val dispStream = master port Stream(Packet())
    }
    io.dispStream.valid := False
    io.dispStream.payload.assignDontCare

    val fsm      = RegInit(Fsm.IDLE)
    val cmdIndex = RegInit(U(0, 2 bits))
    val highByte = RegInit(False)

    val fsmUpdate = Bool()
    val dataValid = Bool()

    // Status register.
    val enabled  = RegInit(False)
    val trigger  = RegInit(False)
    val regSel   = RegInit(False) // Register select for pass-through interface.
    val reset    = RegInit(False) // Display reset line.
    val forceSel = RegInit(False) // Force enable chip select.
    io.resetOut := reset

    // APB-inferred write trigger for pass-through interface.
    val ptTrig = RegInit(False)
    val ptData = Reg(Bits(8 bits))

    // The sink holds ready low through setup, strobe and hold, keeping the
    // display selected until the last accepted byte has finished transmitting.
    io.chipSelect := enabled || forceSel || !io.dispStream.ready

    // Configuration registers.
    val vaddr  = UInt(cfg.vaddrBits bits)
    val pixfmt = PixelFormat()
    val width  = UInt(cfg.coordBits bits)
    val height = UInt(cfg.coordBits bits)

    // I/O bus interface.
    val busCtrl = Apb3SlaveFactory(io.apb)

    busCtrl.read(
        SetBit(trigger, control.trigger) |
            control.attached |
            SetBit(enabled, control.enabled) |
            SetBit(reset, control.reset) |
            SetBit(regSel, control.regSel) |
            SetBit(forceSel, control.forceSel),
        address = scanoutRegs.control
    )
    busCtrl.onWrite(address = scanoutRegs.control) {
        enabled  := TestBit(io.apb.PWDATA, control.enabled)
        reset    := TestBit(io.apb.PWDATA, control.reset)
        regSel   := TestBit(io.apb.PWDATA, control.regSel)
        forceSel := TestBit(io.apb.PWDATA, control.forceSel)
        when(TestBit(io.apb.PWDATA, control.trigger)) {
            trigger := True
        }
    }

    busCtrl.read(
        B(caps.isSerial | caps.commands | caps.reset, 32 bits),
        address = scanoutRegs.caps
    )
    busCtrl.driveAndReadMultiWord(vaddr, address = scanoutRegs.fbAddrLo)
    busCtrl.driveAndReadMultiWord(pixfmt, address = scanoutRegs.pixfmt)
    busCtrl.driveAndRead(
        width,
        address = scanoutRegs.htiming + crtTimingRegs.resolution
    )
    busCtrl.driveAndRead(
        height,
        address = scanoutRegs.vtiming + crtTimingRegs.resolution
    )

    // Serial pass-through logic.
    when(!enabled) {
        io.dispStream.valid          := ptTrig
        io.dispStream.payload.regSel := regSel
        io.dispStream.payload.data   := ptData
        busCtrl.onWrite(address = scanoutRegs.serialData) {
            ptTrig := True
            ptData := io.apb.PWDATA(7 downto 0)
        }
        when(io.dispStream.ready && ptTrig) {
            ptTrig := False
        }

        fsm      := Fsm.IDLE
        cmdIndex := U(0)
        highByte := False
    }

    // Trigger condition.
    when(enabled && fsm === Fsm.IDLE) {
        when(trigger) {
            trigger := False
            fsm     := Fsm.H_RES
        }
    }

    dataValid := True
    fsmUpdate := False

    // Commands in sequence:
    when(enabled) {
        when(fsm === Fsm.H_RES_CMD) {
            io.dispStream.payload.regSel := False
            io.dispStream.payload.data   := B(0x2a, 8 bits) // CASET.
            when(fsmUpdate) {
                fsm := Fsm.H_RES
            }

        } elsewhen (fsm === Fsm.H_RES) {
            io.dispStream.payload.regSel := True
            io.dispStream.payload.data   := cmdIndex
                .mux(
                    0 -> U(0, 8 bits),
                    1 -> U(0, 8 bits),
                    2 -> (width >> 8).resized,
                    3 -> width(7 downto 0)
                )
                .asBits
            when(fsmUpdate && cmdIndex === U(3)) {
                fsm      := Fsm.V_RES_CMD
                cmdIndex := U(0)
            } elsewhen (fsmUpdate) {
                cmdIndex := cmdIndex + U(1)
            }

        } elsewhen (fsm === Fsm.V_RES_CMD) {
            io.dispStream.payload.regSel := False
            io.dispStream.payload.data   := B(0x2b, 8 bits) // RASET.
            when(fsmUpdate) {
                fsm := Fsm.V_RES
            }

        } elsewhen (fsm === Fsm.V_RES) {
            io.dispStream.payload.regSel := True
            io.dispStream.payload.data   := cmdIndex
                .mux(
                    0 -> U(0, 8 bits),
                    1 -> U(0, 8 bits),
                    2 -> (height >> 8).resized,
                    3 -> height(7 downto 0)
                )
                .asBits
            when(fsmUpdate && cmdIndex === U(3)) {
                fsm      := Fsm.WRITE_CMD
                cmdIndex := U(0)
            } elsewhen (fsmUpdate) {
                cmdIndex := cmdIndex + U(1)
            }

        } elsewhen (fsm === Fsm.WRITE_CMD) {
            io.dispStream.payload.regSel := False
            io.dispStream.payload.data   := B(0x2c, 8 bits) // RAMWR.
            when(fsmUpdate) {
                fsm := Fsm.DATA
            }

        } elsewhen (fsm === Fsm.DATA) {
            io.dispStream.payload.regSel := True
        }
    }
}
