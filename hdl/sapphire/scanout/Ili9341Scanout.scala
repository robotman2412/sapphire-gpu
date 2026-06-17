package sapphire.scanout

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.color._
import sapphire.dma._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._

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

        /** Register select; 0: Command, 1: Data. */
        val regSel = out port Bool()

        /** Write strobe. */
        val strobe = out port Bool()

        /** Data bus. */
        val data = out port Bits(8 bits)
    }
    io.resetOut := False
    io.regSel   := False
    io.strobe   := False
    io.data     := B(0)

    val fsm      = RegInit(Fsm.IDLE)
    val cmdIndex = RegInit(U(0, 2 bits))
    val highByte = RegInit(False)

    val fsmUpdate = Bool()
    val dataValid = Bool()

    // Status register.
    val enabled = RegInit(False)
    val trigger = RegInit(False)
    val regSel  = RegInit(False) // Value of `io.regSel` if `enabled` is false

    // Configuration registers.
    val vaddr  = UInt(cfg.vaddrBits bits)
    val pixfmt = PixelFormat()
    val width  = UInt(cfg.coordBits bits)
    val height = UInt(cfg.coordBits bits)

    // I/O bus interface.
    val busCtrl = Apb3SlaveFactory(io.apb)

    busCtrl.read(trigger ## True ## enabled, address = scanoutRegs.control)
    busCtrl.onWrite(address = scanoutRegs.control) {
        enabled := io.apb.PWDATA(0)
        when(io.apb.PWDATA(2)) {
            trigger := True
        }
    }

    busCtrl.read(caps.isSerial | caps.commands, address = scanoutRegs.caps)
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
        io.regSel := regSel
        io.data   := io.apb.PWDATA(7 downto 0)
        busCtrl.onWrite(address = scanoutRegs.serialData) {
            io.strobe := True
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
    when(fsm === Fsm.H_RES_CMD) {
        io.regSel := False
        io.data   := B(0x2a, 8 bits) // CASET.
        when(fsmUpdate) {
            fsm := Fsm.H_RES
        }

    } elsewhen (fsm === Fsm.H_RES) {
        io.regSel := True
        io.data   := cmdIndex
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
        io.regSel := False
        io.data   := B(0x2b, 8 bits) // RASET.
        when(fsmUpdate) {
            fsm := Fsm.V_RES
        }

    } elsewhen (fsm === Fsm.V_RES) {
        io.regSel := True
        io.data   := cmdIndex
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
        io.regSel := False
        io.data   := B(0x2c, 8 bits) // RAMWR.
        when(fsmUpdate) {
            fsm := Fsm.DATA
        }

    } elsewhen (fsm === Fsm.DATA) {
        io.regSel := True

    }

}
