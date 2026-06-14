package sapphire

import spinal.core._

// Copyright (c) 2025 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

object SapphireCfg {
    case class Desc() extends Bundle {

        /** SEMVER major revision of the GPU. */
        val gpuMajor = UInt(8 bits)

        /** SEMVER minor revision of the GPU. */
        val gpuMinor = UInt(8 bits)

        /** SEMVER patch revision of the GPU. */
        val gpuPatch = UInt(8 bits)

        /** Number of scanout engines. */
        val scanoutCount = UInt(8 bits)

        /** Bitset of implemented interrupts. */
        val irqImpl = Bits(32 bits)

        /** Byte size of the GPU's RAM. */
        val ramSize = UInt(64 bits)

        /** Bitset of features that the driver is required to support. */
        val requiredFeatures = RequiredFeatDesc()

        /** Bitset of features that the driver may optionally support. */
        val optionalFeatures = OptionalFeatDesc()

        /** Number of bits used for pixel coordinates. */
        val coordBits = UInt(8 bits)
    }

    case class RequiredFeatDesc() extends Bundle {

        /** The GPU uses 64-bit pointers instead of 32-bit ones. */
        val is64bit = Bool()

        /** Reserved; should be 0. */
        val _resvd0 = Bits(31 bits)
    }

    case class OptionalFeatDesc() extends Bundle {

        /** The GPU has support for 3D rendering. */
        val has3D = Bool()

        /** The GPU has support for vertex coloring. */
        val hasVCol = Bool()

        /** Reserved; should be 0. */
        val _resvd0 = Bits(30 bits)
    }
}

case class SapphireCfg(
    /** RAM size. */
    ramSize: Long,
    /** Pixel coordinate bit width. */
    coordBits: Int = 12,
    /** Pipeline topology configuration. */
    plCfg: SapphirePlCfg = SapphirePlCfg(),
    /** Enable 3D support. */
    has3D: Boolean = false,
    /** Enable vertex coloring. */
    hasVCol: Boolean = false
) {

    /** Virtual address bit width. */
    val vaddrBits = log2Up(ramSize)

    /** Number of bits used for pointers. */
    val ptrBits = if (vaddrBits > 32) 64 else 32

    /** Get GPU hardware description structure. */
    def descStruct = {
        val desc = SapphireCfg.Desc()
        desc.gpuMajor                 := U"8'd0"
        desc.gpuMinor                 := U"8'd0"
        desc.gpuPatch                 := U"8'd1"
        desc.scanoutCount             := U"8'd0"
        desc.irqImpl                  := B"32'b0011"
        desc.ramSize                  := U(ramSize, 64 bits)
        desc.requiredFeatures.is64bit := Bool(vaddrBits > 32)
        desc.requiredFeatures._resvd0 := B"31'b0"
        desc.optionalFeatures.has3D   := Bool(has3D)
        desc.optionalFeatures.hasVCol := Bool(hasVCol)
        desc.optionalFeatures._resvd0 := B"30'b0"
        desc.coordBits                := U(coordBits, 8 bits)
        desc
    }
}

case class SapphirePlCfg(
    /** Use a stage for f2i / i2f. */
    fconvStage: Boolean = true,
    /** Normal multiply stage count. */
    normMulStages: Int = 3,
    /** Small (8-bit) multiply stage count. */
    smallMulStages: Int = 2,
    /** Use a stage for address calculation. */
    acalcStage: Boolean = true
)
