package sapphire

// Copyright (c) 2024 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

package object texture {
    object flags {

        /** Texture slot: stored in big-endian. */
        val bigEndian: Int = 1 << 0
    }

    /** Offset in bytes for texture slot registers. */
    object regs {

        /** Texture width in pixels. */
        val width = 0x00

        /** Texture height in pixels. */
        val height = 0x04

        /** Texture storage flags. */
        val flags = 0x0c

        /** Texture pixel format (bits per pixel and type). */
        val pixfmtA = 0x10

        /** Texture pixel format (channel layotu). */
        val pixfmtB = 0x14
    }

    /** Address stride between each texture slot. */
    val textureSlotStride = 0x20
}
