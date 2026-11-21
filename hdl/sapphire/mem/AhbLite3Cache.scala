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
        val IDLE, BUSY   = newElement()
        val ACCESS, SYNC = newElement() // Direct effect of request.
        val FILL, EVICT  = newElement() // Secondary effect of request.
    }

    object Request extends SpinalEnum {
        val IDLE               = newElement()
        val READ, WRITE        = newElement()
        val SYNC, FLUSH, CLEAR = newElement()
    }

    case class Tag(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Bundle {

        /** This tag describes a resident line of data. */
        val valid = Bool()

        /** This line is dirty. */
        val dirty = Bool()

        /** Upper address fragment. */
        val upperAddr = UInt(ahbCfg.addressWidth - log2Up(cacheCfg.lineSize) - log2Up(cacheCfg.lines) bits)
    }

    case class RowTags(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Bundle {

        /** Eviction policy: round-robin: next entry to evict. */
        val next = UInt(log2Up(cacheCfg.ways) bits)

        val ways = Vec.fill(cacheCfg.ways)(Tag(cacheCfg, ahbCfg))

        def apply(way: UInt): Tag   = ways(way)
        def apply(way: Int): Tag    = ways(way)
        def apply(way: Long): Tag   = ways(way)
        def apply(way: BigInt): Tag = ways(way)

        def selectEviction(): UInt = {
            val which = UInt(log2Up(cacheCfg.ways) bits)
            which := next

            switch(ways.map(_.valid).foldLeft(B(0, 0 bits))((a, b) => a ## b)) {
                for (i <- 0 until cacheCfg.ways) { is(1 << i) { which := i } }
            }

            which
        }
    }
}

case class AhbLite3Cache(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Component {
    import AhbLite3Cache._
    assert(cacheCfg.lineSize >= ahbCfg.bytePerWord, "Cache line size must be at least as large as AHB data width")
    assert(
        ahbCfg.addressWidth >= log2Up(cacheCfg.lines * cacheCfg.lineSize),
        s"${cacheCfg.lines}x${cacheCfg.lineSize}B cache needs at least ${log2Up(cacheCfg.lines * cacheCfg.lineSize)}-bit AHB address width"
    )
    val wordPerLine = cacheCfg.lineSize / ahbCfg.bytePerWord

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
    io.busy := management =/= Management.IDLE

    /** Which row will next be operated on for cache management operations. */
    val manageRow = RegInit(U(0, log2Up(cacheCfg.lines) bits))

    // Action being requested of the cache memories by either AHB or management.
    val request      = Request()
    val reqAddr      = UInt(ahbCfg.addressWidth bits)
    val reqWriteData = Vec.fill(wordPerLine)(Bits(ahbCfg.dataWidth bits))
    val reqWriteMask = Bits(cacheCfg.lineSize bits)
    val reqReadData  = Vec.fill(wordPerLine)(Bits(ahbCfg.dataWidth bits))
    val ackRequest   = Bool()
    val finRequest   = Bool()
    reqWriteData.assignDontCare()
    reqWriteMask := 0

    // Simplified AHB master interface.
    val ucRead      = Bool()
    val ucWrite     = Bool()
    val ucAddr      = UInt(ahbCfg.addressWidth - log2Up(cacheCfg.lineSize) bits)
    val ucWriteData = Vec.fill(wordPerLine)(Bits(ahbCfg.dataWidth bits))
    ucRead  := False
    ucWrite := False
    ucAddr.assignDontCare()
    ucWriteData.assignDontCare()

    val ucReadData = Vec.fill(wordPerLine)(Bits(ahbCfg.dataWidth bits))
    val ucReady    = Bool()
    // TODO: add ucError for HRESP handling?
    ucReady := False

    /** What the cache memories are currently doing. */
    val fsm = RegInit(Fsm.IDLE)

    // Cache memories.
    val tag = new Area {
        val mem = Mem(RowTags(cacheCfg, ahbCfg), cacheCfg.lines)

        val read      = Bool()
        val write     = Bool()
        val addr      = UInt(log2Up(cacheCfg.lines) bits)
        val writeData = mem.wordType()
        val readData  = mem.wordType()
        readData := mem.readWriteSync(addr, writeData, read || write, write, readUnderWrite = dontCare)

        read  := False
        write := False
        addr.assignDontCare()
        writeData.assignDontCare()
    }

    val data = new Area {
        val mem = Mem(Vec.fill(cacheCfg.ways)(Vec.fill(wordPerLine)(Bits(ahbCfg.dataWidth bits))), cacheCfg.lines)

        val read      = Bool()
        val write     = Bool()
        val addr      = UInt(log2Up(cacheCfg.lines) bits)
        val writeData = mem.wordType()
        val writeMask = Vec.fill(cacheCfg.ways)(Bits(cacheCfg.lineSize bits))
        val readData  = mem.wordType()
        readData :=
            mem.readWriteSync(addr, writeData, read || write, write, mask = writeMask.asBits, readUnderWrite = dontCare)

        read  := False
        write := False
        for (i <- 0 until cacheCfg.ways) { writeMask(i) := 0 }
        addr.assignDontCare()
        writeData.assignDontCare()
    }

    // Idle states.
    request    := Request.IDLE
    reqAddr.assignDontCare()
    ackRequest := False

    val managementLogic = new Area {
        when(management === Management.IDLE) {
            manageRow := 0
            when(io.clear) { management := Management.CLEAR } elsewhen (io.sync) {
                management := Management.SYNC
            } elsewhen (io.flush) { management := Management.FLUSH }
        } otherwise {
            switch(management) {
                is(Management.CLEAR) { request := Request.CLEAR }
                is(Management.FLUSH) { request := Request.FLUSH }
                is(Management.SYNC) { request := Request.SYNC }
            }
            reqAddr := (manageRow << log2Up(cacheCfg.lineSize)).resized
            when(ackRequest) {
                when(manageRow === manageRow.maxValue) { management := Management.IDLE }
                manageRow := manageRow + 1
            }
        }
    }

    val ahbSlave = new Area {
        // AHB-lite 3 transfer buffer.
        val htrans = RegInit(False) // Combined with HSEL
        val hwrite = Reg(Bool())
        val hsize  = Reg(Bits(3 bits))
        val haddr  = Reg(UInt(ahbCfg.addressWidth bits))

        val nextHtrans = Bool() // Combined with HSEL
        val nextHwrite = Bool()
        val nextHsize  = Bits(3 bits)
        val nextHaddr  = UInt(ahbCfg.addressWidth bits)

        nextHtrans := htrans
        nextHwrite := hwrite
        nextHsize  := hsize
        nextHaddr  := haddr
        htrans     := nextHtrans
        hwrite     := nextHwrite
        hsize      := nextHsize
        haddr      := nextHaddr

        val writeMask     = Ahb3WriteMask(hsize, haddr, ahbCfg)
        val reqInProgress = RegInit(False)

        when(io.slave.HREADY) {
            // Finish request.
            nextHtrans    := False
            reqInProgress := False
        }
        when((!htrans || io.slave.HREADY) && io.slave.HSEL && io.slave.HTRANS(1)) {
            // Start request.
            nextHtrans := True
            nextHwrite := io.slave.HWRITE
            nextHsize  := io.slave.HSIZE
            nextHaddr  := io.slave.HADDR
        }

        when(management === Management.IDLE && (!reqInProgress || io.slave.HREADY) && nextHtrans) {
            // Request to cache memory.
            request := nextHwrite.mux(Request.WRITE, Request.READ)
            reqAddr := nextHaddr
            when(ackRequest) { reqInProgress := True }
        }

        when(reqInProgress) {
            val ahbExp  = log2Up(ahbCfg.bytePerWord)
            val multExp = log2Up(wordPerLine)

            io.slave.HREADYOUT := finRequest

            // Splat write data.
            for (i <- 0 until wordPerLine) {
                reqWriteData(i) := io.slave.HWDATA
            }

            // Demux write mask.
            val word = haddr(multExp + ahbExp - 1 downto ahbExp)
            reqWriteMask := writeMask.resize(cacheCfg.lineSize) |<< (word * ahbCfg.bytePerWord)
        } otherwise {
            io.slave.HREADYOUT := !htrans
        }

        io.slave.HRESP  := False
        io.slave.HRDATA := reqReadData((haddr >> log2Up(ahbCfg.bytePerWord)).resize(log2Up(wordPerLine)))
    }

    val ahbMaster = if (wordPerLine == 1) {
        new Area {
            // TODO.
        }
    } else {
        new Area {
            val readBuffer = Reg(Vec.fill(wordPerLine - 1)(Bits(ahbCfg.dataWidth bits)))
            for (i <- 0 until wordPerLine - 1) {
                ucReadData(i) := readBuffer(i)
            }
            ucReadData(wordPerLine - 1) := io.master.HRDATA

            // State machine.
            val active     = RegInit(False)
            val nextActive = Bool()
            active     := nextActive
            nextActive := active
            val word     = RegInit(U(0, log2Up(wordPerLine) + 1 bits))
            val lastWord = word(log2Up(wordPerLine))

            when(!active) {
                nextActive := ucRead || ucWrite
            }
            when(active && lastWord) {
                io.master.HTRANS := AhbLite3.IDLE
            } elsewhen (active) {
                io.master.HTRANS := AhbLite3.SEQ
            } elsewhen (nextActive) {
                io.master.HTRANS := AhbLite3.NONSEQ
            } otherwise {
                io.master.HTRANS := AhbLite3.IDLE
            }
            when(active && io.master.HREADYOUT) {
                if (wordPerLine > 2) {
                    readBuffer(word.trim(1) - 1) := io.master.HRDATA
                } else {
                    readBuffer(0) := io.master.HRDATA
                }
            }
            when(nextActive && io.master.HREADYOUT) {
                when(lastWord) {
                    ucReady := True
                    active  := False
                    word    := 0
                } otherwise {
                    word := word + 1
                }
            }

            if (wordPerLine == 16) {
                io.master.HBURST := 6 // WRAP16
            } else if (wordPerLine == 8) {
                io.master.HBURST := 4 // WRAP8
            } else if (wordPerLine == 4) {
                io.master.HBURST := 2 // WRAP4
            } else {
                io.master.HBURST := 1 // INCR
            }

            // AHB connections.
            io.master.HADDR     := (ucAddr ## word.trim(1) ## U(0, log2Up(ahbCfg.bytePerWord) bits)).asUInt
            io.master.HMASTLOCK := False
            io.master.HPROT     := B"1111"
            io.master.HSIZE     := B(log2Up(ahbCfg.bytePerWord))
            io.master.HWRITE    := ucWrite
            io.master.HWDATA    := ucWriteData(word.trim(1) - 1)
            io.master.HSEL      := True
            io.master.HREADY    := io.master.HREADYOUT
        }
    }

    val memoryLogic = new Area {
        // Request being processed.
        val process       = RegInit(Request.IDLE)
        val nextProcess   = Request()
        val procAddr      = Reg(UInt(ahbCfg.addressWidth bits))
        val procUpperAddr = procAddr >> (log2Up(cacheCfg.lineSize) + log2Up(cacheCfg.lines))
        val nextProcAddr  = UInt(ahbCfg.addressWidth bits)
        nextProcess  := process
        process      := nextProcess
        nextProcAddr := procAddr
        procAddr     := nextProcAddr

        val nextLine = (nextProcAddr >> log2Up(cacheCfg.lineSize)).resize(log2Up(cacheCfg.lines))
        val line     = (procAddr >> log2Up(cacheCfg.lineSize)).resize(log2Up(cacheCfg.lines))
        tag.addr  := nextLine
        data.addr := nextLine

        // Set matching logic.
        val matchMask  = Bits(cacheCfg.ways bits)
        val matchIndex = UInt(log2Up(cacheCfg.ways) bits)
        val hasMatch   = Bool()
        val matchDirty = Bool()
        for (i <- 0 until cacheCfg.ways) {
            matchMask(i) := (tag.readData(i).valid && tag.readData(i).upperAddr === procUpperAddr)
        }
        hasMatch := False
        matchDirty.assignDontCare()
        matchIndex.assignDontCare()
        reqReadData.assignDontCare()
        switch(matchMask) {
            for (i <- 0 until cacheCfg.ways) {
                is(1 << i) {
                    hasMatch    := True
                    matchIndex  := i
                    reqReadData := data.readData(i)
                    matchDirty  := tag.readData(i).dirty
                }
            }
        }

        // Evict/fill selection.
        val fillIndex      = tag.readData.selectEviction()
        val afterFillIndex = UInt(log2Up(cacheCfg.ways) bits)
        afterFillIndex := fillIndex + 1
        if ((cacheCfg.ways & (cacheCfg.ways - 1)) != 0) {
            when(fillIndex === cacheCfg.ways - 1) {
                afterFillIndex := 0
            }
        }

        ucWriteData := data.readData(fillIndex)
        when(ucWrite) {
            ucAddr := (tag.readData(fillIndex).upperAddr ## line).asUInt
        } otherwise {
            ucAddr := procAddr >> log2Up(cacheCfg.lineSize)
        }

        // Request cycle 1.
        finRequest := False
        switch(fsm) {
            is(Fsm.IDLE) {
                ackRequest := True
            }
            is(Fsm.ACCESS) {
                when(!hasMatch) {
                    // Not resident.
                    when(tag.readData(fillIndex).dirty) {
                        fsm       := Fsm.EVICT
                        data.read := True
                        ucWrite   := True
                    } otherwise {
                        fsm    := Fsm.FILL
                        ucRead := True
                    }
                } elsewhen (process === Request.WRITE) {
                    // Write to resident line.
                    fsm                        := Fsm.IDLE
                    data.write                 := True
                    data.addr                  := line
                    data.writeData(matchIndex) := reqWriteData
                    data.writeMask(matchIndex) := reqWriteMask
                    finRequest                 := True
                    when(!matchDirty) {
                        // Must mark as dirty.
                        tag.write                       := True
                        tag.writeData                   := tag.readData
                        tag.writeData(matchIndex).dirty := True
                    } elsewhen (request =/= Request.READ) {
                        // Read is the only request to immediately need data mem.
                        // So it is incompatible with this write, which is still using it.
                        ackRequest := True
                    }
                } otherwise {
                    // Other (usually read) access to resident line.
                    fsm        := Fsm.IDLE
                    ackRequest := True
                    finRequest := True
                }
            }
            is(Fsm.FILL) {
                ucRead := True
                when(ucReady) {
                    data.write                         := True
                    data.writeData(fillIndex)          := ucReadData
                    data.writeMask(fillIndex)          := B((BigInt(1) << cacheCfg.lineSize) - 1)
                    tag.write                          := True
                    if (cacheCfg.ways > 1) {
                        tag.writeData.ways := tag.readData.ways
                    }
                    tag.writeData.next                 := afterFillIndex
                    tag.writeData(fillIndex).valid     := True
                    tag.writeData(fillIndex).dirty     := process === Request.WRITE
                    tag.writeData(fillIndex).upperAddr := procUpperAddr
                    // TODO: Read/write forwarding.
                    fsm                                := Fsm.BUSY
                } otherwise {
                    tag.read := True
                }
            }
            is(Fsm.EVICT) {
                ucWrite := True
                when(ucReady && (process === Request.READ || process === Request.WRITE)) {
                    // Data saved successfully, switch straight to refill.
                    fsm := Fsm.FILL
                } elsewhen (ucReady) {
                    // TODO: Must be flush/sync request, go to next index or complete.
                } otherwise {
                    data.read := True
                    tag.read  := True
                }
            }
        }

        // Request cycle 0.
        when(ackRequest) {
            nextProcess  := request
            nextProcAddr := reqAddr
        }

        when(fsm === Fsm.BUSY || ackRequest) {
            switch(nextProcess) {
                default {
                    fsm := Fsm.IDLE
                }
                is(Request.READ) {
                    fsm       := Fsm.ACCESS
                    tag.read  := True
                    data.read := True
                }
                is(Request.WRITE, Request.FLUSH, Request.SYNC) {
                    fsm      := Fsm.ACCESS
                    tag.read := True
                }
                is(Request.CLEAR) {
                    fsm                := Fsm.IDLE
                    tag.write          := True
                    tag.writeData.next := 0
                    for (i <- 0 until cacheCfg.ways) {
                        tag.writeData(i).valid := False
                        tag.writeData(i).dirty := False
                        tag.writeData(i).upperAddr.assignDontCare()
                    }
                }
            }
        }
    }
}
