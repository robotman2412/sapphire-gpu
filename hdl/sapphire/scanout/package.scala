package sapphire

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import spinal.core._

package object scanout {
    object caps {

        /** Scanout capability: is a CRT controller. */
        val isCrtc = B(1 << 0, 32 bits)

        /** Scanout capability: is a serial controller. */
        val isSerial = B(1 << 1, 32 bits)

        /** Scanout capability: supports I2C display negotiation. */
        val negotiation = B(1 << 2, 32 bits)

        /** Scanout capability: supports sending in-band commands. */
        val commands = B(1 << 3, 32 bits)

        /** Scanout control: display reset (read-write; active-high). */
        val reset = B(1 << 4, 32 bits)
    }

    object control {

        /** Scanout control: enabled (read-write; serial type does not
          * automatically send video if enabled).
          */
        val enabled = B(1 << 0, 32 bits)

        /** Scanout status: display attached (read-only). */
        val attached = B(1 << 1, 32 bits)

        /** Scanout control: trigger one frame (trigger; only with
          * [[caps.isSerial]]).
          */
        val trigger = B(1 << 2, 32 bits)

        /** Scanout control: register select; 0: Command, 1: Data (read-write;
          * only with [[caps.isSerial]]).
          */
        val regsel = B(1 << 3, 32 bits)

        /** Scanout control: display reset (read-write; active-high). */
        val reset = B(1 << 4, 32 bits)
    }

    /** Register offsets in bytes for horizontal or vertical timings. */
    object crtTimingRegs {

        /** Front porch cycles (read-write; only with [[caps.isCrtc]]). */
        val front = 0

        /** Resolution / video cycles (read-write). */
        val resolution = 4

        /** Back porch cycles (read-write; only with [[caps.isCrtc]]). */
        val back = 8

        /** Sync pulse cycles (read-write; only with [[caps.isCrtc]]). */
        val sync = 12
    }

    /** Register offsets in bytes for scanout engines in the I/O space. */
    object scanoutRegs {

        /** Capabilities (read-only). */
        val caps = 0

        /** Control and status (read-write). */
        val control = 4

        /** GPU MMU context ID (read-write; only with SAPPHIRE_OPTF_HAS_MMU). */
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
          * SAPPHIRE_SCANOUT_CAP_IS_SERIAL).
          */
        val serialData = 60
    }
}
