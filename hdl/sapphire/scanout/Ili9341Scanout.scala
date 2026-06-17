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

    /** Helper component that streams out the data from a [[Stream]] of
      * [[Packet]] at core clock.
      */
    case class DirectSink() extends Component {
        val io = new Bundle {
            val in     = slave port Stream(Packet())
            val strobe = out port Bool()
            val regSel = out port Bool()
            val data   = out port Bits(8 bits)
        }

        val strobe = RegInit(False)
        val regSel = Reg(Bool())
        val data   = Reg(Bits(8 bits))

        io.in.ready := True
        strobe      := io.in.valid
        regSel      := io.in.payload.regSel
        data        := io.in.payload.data

        /** Clock that falls in phase with this component's clock. Rises at a
          * 180 degree offset from the active component clock edge.
          */
        val offsetClk = ClockDomain.current.readClockWire ^ Bool(
            ClockDomain.current.config.clockEdge == RISING
        )

        io.strobe := offsetClk && strobe
        io.regSel := regSel
        io.data   := data
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

        /** Display command and data stream. */
        val dispStream = master port Stream(Packet())
    }
    io.resetOut         := False
    io.dispStream.valid := False
    io.dispStream.payload.assignDontCare

    val fsm      = RegInit(Fsm.IDLE)
    val cmdIndex = RegInit(U(0, 2 bits))
    val highByte = RegInit(False)

    val fsmUpdate = Bool()
    val dataValid = Bool()

    // Status register.
    val enabled = RegInit(False)
    val trigger = RegInit(False)
    val regSel  = RegInit(False) // Register select for pass-through interface.
    val reset   = RegInit(False) // Display reset line.

    // APB-inferred write trigger for pass-through interface.
    val ptTrig = RegInit(False)

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
            SetBit(enabled, control.enabled),
        address = scanoutRegs.control
    )
    busCtrl.onWrite(address = scanoutRegs.control) {
        enabled := TestBit(io.apb.PWDATA, control.enabled)
        reset   := TestBit(io.apb.PWDATA, control.reset)
        when(TestBit(io.apb.PWDATA, control.trigger)) {
            trigger := True
        }
    }

    busCtrl.read(
        caps.isSerial | caps.commands | caps.reset,
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
        io.dispStream.ready          := ptTrig
        io.dispStream.payload.regSel := regSel
        io.dispStream.payload.data   := io.apb.PWDATA(7 downto 0)
        busCtrl.onWrite(address = scanoutRegs.serialData) {
            ptTrig := True
        }
        when(io.dispStream.ready) {
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
