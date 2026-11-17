package sapphire.mem

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._

object AhbLite3Cache {
    object Management extends SpinalEnum {
        val IDLE, SYNC, FLUSH, CLEAR = newElement()
    }

    object Fsm extends SpinalEnum {
        val IDLE, READ, WRITE, FILL, SYNC = newElement()
    }

    case class Tag(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Bundle {

        /** This tag describes a resident line of data. */
        val valid = Bool()

        /** Eviction policy: oldest: ordering of most recent access. */
        val age = (cacheCfg.evictionPolicy == CacheCfg.oldest) generate UInt(log2Up(cacheCfg.ways) bits)

        /** Upper address fragment. */
        val lineAddress = UInt(ahbCfg.addressWidth - log2Up(cacheCfg.lineSize) bits)
    }

    case class RowTags(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Bundle {

        /** Eviction policy: round-robin: next entry to evict. */
        val next = (cacheCfg.evictionPolicy == CacheCfg.roundRobin) generate UInt(log2Up(cacheCfg.lineSize) bits)

        val ways = Vec.fill(cacheCfg.ways)(Tag(cacheCfg, ahbCfg))

        def apply(way: UInt): Tag = ways(way)

        def touchWay(way: UInt): RowTags = {
            if (cacheCfg.evictionPolicy != CacheCfg.oldest || cacheCfg.ways == 1) {
                return this
            }
            val that = RowTags(cacheCfg, ahbCfg)

            for (i <- 0 until cacheCfg.ways) {
                that(i).age := this(i).age + (this(i).age < this(way).age).asUInt.resized
            }
            that(way).age := 0

            that
        }

        def selectEviction(): UInt = {
            val which = UInt(log2Up(cacheCfg.ways) bits)

            if (cacheCfg.evictionPolicy == CacheCfg.oldest) {
                which.assignDontCare()

                def recursiveMax(of: Seq[UInt]): UInt = {
                    if (of.length == 0) {
                        throw new IllegalArgumentException("recursiveMax with no arguments")
                    } else if (of.length == 1) {
                        of(0)
                    } else {
                        val split = of.splitAt(of.length / 2)
                        val left  = recursiveMax(split._1)
                        val right = recursiveMax(split._2)
                        val res   = UInt(left.getBitsWidth bits)
                        when(left > right) {
                            res := left
                        } otherwise {
                            res := right
                        }
                        res
                    }
                }

                val age = recursiveMax(ways.map(_.age))

                switch(ways.map(_.valid).foldLeft(B(0, 0 bits))((a, b) => a ## b)) {
                    for (i <- 0 until cacheCfg.ways) {
                        is(1 << i) {
                            which := i
                        }
                    }
                }

            } else if (cacheCfg.evictionPolicy == CacheCfg.roundRobin) {
                which := next
            }

            switch(ways.map(_.valid).foldLeft(B(0, 0 bits))((a, b) => a ## b)) {
                for (i <- 0 until cacheCfg.ways) {
                    is(1 << i) {
                        which := i
                    }
                }
            }

            which
        }
    }
}

case class AhbLite3Cache(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Component {
    import AhbLite3Cache._
    assert(cacheCfg.lineSize >= ahbCfg.dataWidth, "Cache line size must be at least as large as AHB data width")
    assert(
        ahbCfg.addressWidth >= log2Up(cacheCfg.lines * cacheCfg.lineSize),
        s"${cacheCfg.lines}x${cacheCfg.lineSize}B cache needs at least ${log2Up(cacheCfg.lines * cacheCfg.lineSize)}-bit AHB address width"
    )

    val io = new Bundle {

        /** Cached side. */
        val slave = spinal.lib.slave(AhbLite3(ahbCfg))

        /** Uncached side. */
        val master = spinal.lib.master(AhbLite3(ahbCfg))

        /** Trigger full write-back of dirty lines. */
        val sync = in port Bool()

        /** Trigger full flush (with write-back). */
        val flush = in port Bool()

        /** Trigger full invalidation (without write-back). */
        val clear = in port Bool()

        /** Cache-management operation in progress. */
        val busy = out port Bool()
    }

    /** Active cache management operation type. */
    val management = RegInit(Management.CLEAR)

    /** Which row will next be operated on for cache management operations. */
    val mangeRow = RegInit(U(0, log2Up(cacheCfg.lines) bits))

    // Action being requested of the cache memories by either AHB or management.
    val request      = Fsm()
    val reqAddr      = UInt(ahbCfg.addressWidth bits)
    val reqWriteData = Bits(cacheCfg.lineSize * 8 bits)
    val reqWriteMask = Bits(cacheCfg.lineSize bits)

    /** What the cache memories are currently doing. */
    val fsm = RegInit(Fsm.IDLE)

    // Cache memories.
    val tags = Mem(RowTags(cacheCfg, ahbCfg), cacheCfg.lines)
    val data = Mem(Vec.fill(cacheCfg.ways)(Bits(cacheCfg.lineSize * 8 bits)), cacheCfg.lines)

}
