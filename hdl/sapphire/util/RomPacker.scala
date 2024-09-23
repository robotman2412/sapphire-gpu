package nebula.util

// Copyright © 2024, Julian Scheffers, see LICENSE for info

import spinal.core._



/** Packs a Seq[Byte] into a Seq[Bits] for use in SpinalHDL ROMs. */
object RomPacker {
    def apply(raw: Seq[Byte], width: BitCount, endian: Endianness = LITTLE): Seq[Bits] = {
        val iter = raw.iterator
        for (i <- 0 to (raw.length * 8 - 1) / width.value) yield {
            if (endian == LITTLE) {
                readWordLE(iter, width)
            } else {
                readWordBE(iter, width)
            }
        }
    }
    
    /** Reads a little-endian word from an iterator. */
    private def readWordLE(iter: Iterator[Byte], width: BitCount): Bits = {
        var tmp = BigInt(0)
        val bytes = (width.value + 7) / 8
        for (i <- 0 until bytes) {
            if (iter.hasNext) {
                tmp |= BigInt(iter.next() & 255) << (i*8)
            }
        }
        return B(tmp, width)
    }
    
    /** Reads a big-endian word from an iterator. */
    private def readWordBE(iter: Iterator[Byte], width: BitCount): Bits = {
        var tmp = BigInt(0)
        val bytes = (width.value + 7) / 8
        for (i <- 0 until bytes) {
            if (iter.hasNext) {
                val byte = iter.next() & 255
                tmp |= byte << ((bytes-i-1)*8)
            }
        }
        return B(tmp, width)
    }
}
