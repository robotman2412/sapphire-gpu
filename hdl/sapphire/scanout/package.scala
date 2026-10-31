package sapphire

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import spinal.core._

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
        val front = 0

        /** Resolution / video cycles (read-write). */
        val resolution = 4

        /** Back porch cycles (read-write). If unimplemented, read-only 0.
          */
        val back = 8

        /** Sync pulse cycles (read-write). If unimplemented, read-only 0.
          */
        val sync = 12
    }

    /** Register offsets in bytes for scanout engines in the I/O space. */
    object scanoutRegs {

        /** Capabilities (read-only). */
        val caps = 0

        /** Control and status (read-write). */
        val control = 4

        /** GPU MMU context ID (read-write; only with
          * [[OptionalFeatDesc.hasMmu]]).
          */
        val mmuCtx = 8

        /** Source virtual address (read-write). */
        val fbAddrLo = 12

        /** Source virtual address (read-write; only with
          * SAPPHIRE_REQF_IS_64BIT).
          */
        val fbAddrHi = 16

        /** Pixel color format (read-write). */
        val pixfmt = 20

        /** Horizontal timings (read-write; resolution only for non-CRTC). */
        val htiming = 28

        /** Vertical timings (read-write; resolution only for non-CRTC). */
        val vtiming = 44

        /** Command/data passthough port (read-write; only with
          * [[caps.isSerial]]).
          */
        val serialData = 60
    }
}
