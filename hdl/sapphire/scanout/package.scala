package sapphire

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._

package object scanout {
    object caps {

        /** Scanout capability: is a CRT controller. */
        val isCrtc: Int = 1 << 0

        /** Scanout capability: is a serial controller. */
        val isSerial: Int = 1 << 1

        /** Scanout capability: supports I2C display negotiation. */
        val negotiation: Int = 1 << 2

        /** Scanout capability: supports sending in-band commands. */
        val commands: Int = 1 << 3

        /** Scanout control: display reset (read-write; active-high). */
        val reset: Int = 1 << 4

        /** Scanout capability: supports pixel format conversion. If not
          * supported, the input buffer's format must exactly match the
          * display's format. If supported, the pixels' formats are always
          * converted according to [[scanoutRegs.pixfmt]].
          */
        val pixconv: Int = 1 << 5

        /** Scanout capability: supports partial refresh. For serial scanout,
          * [[crtTimingRegs.front]] is re-used as the start offset and, if
          * implemented, [[crtTimingRegs.back]] is padded to display actual
          * size.
          */
        val partialRefresh: Int = 1 << 6
    }

    object control {

        /** Scanout control: enabled (read-write; serial type does not
          * automatically send video if enabled).
          */
        val enabled: Int = 1 << 0

        /** Scanout status: display attached (read-only). */
        val attached: Int = 1 << 1

        /** Scanout control: trigger one frame (trigger; only with
          * [[caps.isSerial]]).
          */
        val trigger: Int = 1 << 2

        /** Scanout control: register select; 0: Command, 1: Data (read-write;
          * only with [[caps.isSerial]]).
          */
        val regSel: Int = 1 << 3

        /** Scanout control: display reset (read-write; active-high). */
        val reset: Int = 1 << 4

        /** Scanout control: force enable chip select. */
        val forceSel: Int = 1 << 5
    }

    /** Register offsets in bytes for horizontal or vertical timings. */
    object crtTimingRegs {

        /** Front porch cycles (read-write). If unimplemented, read-only 0.
          */
        val front = 0x00

        /** Resolution / video cycles (read-write). */
        val resolution = 0x04

        /** Back porch cycles (read-write). If unimplemented, read-only 0.
          */
        val back = 0x08

        /** Sync pulse cycles (read-write). If unimplemented, read-only 0.
          */
        val sync = 0x0c
    }

    /** Register offsets in bytes for scanout engines in the I/O space. */
    object scanoutRegs {

        /** Capabilities (read-only). */
        val caps = 0x00

        /** Control and status (read-write). */
        val control = 0x04

        /** GPU MMU context ID (read-write; only with
          * [[OptionalFeatDesc.hasMmu]]).
          */
        val mmuCtx = 0x08

        /** Source virtual address (read-write). */
        val fbAddrLo = 0x0c

        /** Source virtual address (read-write; only with
          * SAPPHIRE_REQF_IS_64BIT).
          */
        val fbAddrHi = 0x10

        /** Pixel color format (read-write). */
        val pixfmt = 0x14

        /** Horizontal timings (read-write; resolution only for non-CRTC). */
        val htiming = 0x1c

        /** Vertical timings (read-write; resolution only for non-CRTC). */
        val vtiming = 0x2c

        /** Command/data passthough port (read-write; only with
          * [[caps.isSerial]]).
          */
        val serialData = 0x3c
    }

    /** Address stride between each scanout engine. */
    val scanoutEngineStride = 0x80
}

object GenericScanout {
    abstract class IO extends Bundle {

        /** APB slave bus. */
        val apb: Apb3

        /** Externally visible I/O. The provided bundle must implement
          * [[IMasterSlave]] as master and be clonable.
          */
        val external: Bundle with IMasterSlave = null
    }
}

abstract class GenericScanout(cfg: SapphireCfg) extends Component {
    val io: GenericScanout.IO
}
