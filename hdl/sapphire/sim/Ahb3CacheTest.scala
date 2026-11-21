package sapphire.sim

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire._
import sapphire.mem._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import scala.util.Random

case class Ahb3CacheDut(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Component {
    val io = new Bundle {
        val ahb = slave port AhbLite3(ahbCfg)

        val sync  = in port Bool()
        val flush = in port Bool()
        val clear = in port Bool()
        val busy  = out port Bool()

        val stallInject = in port Bool()
    }

    val cache = AhbLite3Cache(cacheCfg, ahbCfg)
    val ram   = AhbLite3OnChipRam(ahbCfg, 0x1000)

    cache.io.slave <> io.ahb

    val master  = cache.io.master
    val target  = ram.io.ahb
    val pending = RegInit(False)
    val capture = io.stallInject && !pending && master.HSEL && master.HTRANS(1)
    when(capture) {
        pending := True
    } elsewhen (!io.stallInject && target.HREADYOUT) {
        pending := False
    }

    def buffered[T <: Data](signal: T): T = {
        val saved = RegNextWhen(signal, capture)
        pending.mux(saved, signal)
    }

    target.HADDR     := buffered(master.HADDR)
    target.HWRITE    := buffered(master.HWRITE)
    target.HSIZE     := buffered(master.HSIZE)
    target.HBURST    := buffered(master.HBURST)
    target.HPROT     := buffered(master.HPROT)
    target.HTRANS    := io.stallInject.mux(AhbLite3.IDLE, buffered(master.HTRANS))
    target.HMASTLOCK := buffered(master.HMASTLOCK)
    target.HSEL      := buffered(master.HSEL)

    // The previous address phase can complete at the RAM as the stall begins.
    // Write data still belongs to that phase, and the RAM holds read data while idle.
    target.HWDATA    := master.HWDATA
    target.HREADY    := target.HREADYOUT
    master.HREADYOUT := target.HREADYOUT && !io.stallInject
    master.HRESP     := target.HRESP
    master.HRDATA    := target.HRDATA

    cache.io.sync  := io.sync
    cache.io.flush := io.flush
    cache.io.clear := io.clear
    io.busy        := cache.io.busy
}

class Ahb3CacheTestCase(dut: Ahb3CacheDut, slaveStallRandom: Option[Random] = None) {
    val cacheCfg  = dut.cacheCfg
    val ahbCfg    = dut.ahbCfg
    var hasErrors = false
    private var slaveStalled = false

    dut.io.clear #= false
    dut.io.flush #= false
    dut.io.sync #= false
    dut.io.stallInject #= false

    dut.io.ahb.HTRANS #= 0 // IDLE
    dut.io.ahb.HBURST #= 0
    dut.io.ahb.HPROT #= 0xc
    dut.io.ahb.HMASTLOCK #= false
    dut.io.ahb.HSEL #= true
    dut.io.ahb.HREADY #= true

    forkSensitive(dut.io.ahb.HREADYOUT) {
        driveSlaveReady()
    }
    dut.clockDomain.forkStimulus(period = 10)

    private def driveSlaveReady(): Unit = {
        dut.io.ahb.HREADY #= (dut.io.ahb.HREADYOUT.toBoolean && !slaveStalled)
    }

    private def waitForResponse(addr: Long, operation: String): Unit = {
        val start = simTime()
        def waitUntilReady(): Unit = {
            while (!dut.io.ahb.HREADYOUT.toBoolean) {
                if (simTime() - start >= 10000) {
                    simFailure(f"$operation at 0x$addr%04x timed out waiting for HREADYOUT")
                }
                dut.clockDomain.waitSampling()
                sleep(1)
            }
        }

        sleep(1)
        waitUntilReady()
        slaveStallRandom.foreach { random =>
            if (random.nextInt(32) == 0) {
                slaveStalled = true
                driveSlaveReady()
                dut.clockDomain.waitSampling(2 + random.nextInt(2))
                sleep(1)
                slaveStalled = false
                driveSlaveReady()
                waitUntilReady()
            }
        }
    }

    def write(addr: Long, sizeExp: Int, data: BigInt): Unit = {
        val size       = 1 << sizeExp
        var splatData  = BigInt(0)
        val maskedData = data & ((BigInt(1) << (size * 8)) - 1)
        for (i <- 0 until ahbCfg.bytePerWord by (1 << sizeExp)) {
            splatData |= maskedData << (i * 8)
        }

        dut.io.ahb.HTRANS #= 2 // NONSEQ
        dut.io.ahb.HSIZE #= sizeExp
        dut.io.ahb.HADDR #= addr & ~(size - 1)
        dut.io.ahb.HWRITE #= true
        dut.clockDomain.waitSampling()
        dut.io.ahb.HTRANS #= 0 // IDLE
        dut.io.ahb.HWDATA #= splatData

        waitForResponse(addr, "Write")
    }

    def read(addr: Long, sizeExp: Int): BigInt = {
        val size  = 1 << sizeExp
        val index = addr.toInt & ~(size - 1) & (ahbCfg.bytePerWord - 1)

        dut.io.ahb.HTRANS #= 2 // NONSEQ
        dut.io.ahb.HSIZE #= sizeExp
        dut.io.ahb.HADDR #= addr & ~(size - 1)
        dut.io.ahb.HWRITE #= false
        dut.clockDomain.waitSampling()
        dut.io.ahb.HTRANS #= 0 // IDLE

        waitForResponse(addr, "Read")
        val raw = dut.io.ahb.HRDATA.toBigInt

        (raw >> (8 * index)) & ((BigInt(1) << (8 * size)) - 1)
    }

    def expect(addr: Long, sizeExp: Int, value: BigInt): BigInt = {
        val actual = read(addr, sizeExp)
        if (actual != value) {
            hasErrors = true
            print(f"$addr%04x:  \u001b[31m$actual%x\u001b[0m  (expected $value%x)  t=${simTime()}")
        }
        actual
    }
}

case class Ahb3CacheTestBasic(dut: Ahb3CacheDut) extends Ahb3CacheTestCase(dut) {
    write(0x0002, 0, 0xfe)
    write(0x0003, 0, 0xca)
    expect(0x0002, 1, 0xcafe)
    write(0x0000, 0, 0xbe)
    write(0x0001, 0, 0xba)
    expect(0x0000, 1, 0xbabe)
    expect(0x0000, 2, 0xcafebabeL)
    write(0x0004, 1, 0x5678)
    write(0x0006, 1, 0x1234)
    expect(0x0004, 2, 0x12345678L)

    // Fill both halves of the other three cache sets.
    write(0x0008, 2, 0x10203040L)
    write(0x000c, 2, 0x50607080L)
    write(0x0010, 2, 0x11223344L)
    write(0x0014, 2, 0x55667788L)
    write(0x0018, 2, 0x90abcdefL)
    write(0x001c, 2, 0xfedcba98L)

    write(0x000f, 0, 0x9a)
    expect(0x000f, 0, 0x9a)
    write(0x0016, 1, 0xa1b2)
    expect(0x0016, 1, 0xa1b2)
    write(0x001c, 1, 0xc3d4)
    expect(0x001c, 1, 0xc3d4)

    write(0x0020, 2, 0xdeadbeefL)
    write(0x0024, 2, 0x0badf00dL)
    expect(0x0020, 2, 0xdeadbeefL)
    expect(0x0024, 2, 0x0badf00dL)
    expect(0x0000, 2, 0xcafebabeL)
    expect(0x0004, 2, 0x12345678L)

    // Three lines mapping to set zero force eviction in the two-way cache.
    write(0x0040, 2, 0xbaadc0deL)
    write(0x0044, 2, 0xfacefeedL)
    expect(0x0020, 2, 0xdeadbeefL)
    expect(0x0024, 2, 0x0badf00dL)
    expect(0x0040, 2, 0xbaadc0deL)
    expect(0x0044, 2, 0xfacefeedL)
    expect(0x0000, 2, 0xcafebabeL)
    expect(0x0004, 2, 0x12345678L)
    expect(0x0020, 2, 0xdeadbeefL)
    expect(0x0024, 2, 0x0badf00dL)

    expect(0x0008, 2, 0x10203040L)
    expect(0x000c, 2, 0x9a607080L)
    expect(0x0010, 2, 0x11223344L)
    expect(0x0014, 2, 0xa1b27788L)
    expect(0x0018, 2, 0x90abcdefL)
    expect(0x001c, 2, 0xfedcc3d4L)

    dut.clockDomain.waitSampling(10)

    assert(!hasErrors)
}

case class Ahb3CacheTestFuzz(dut: Ahb3CacheDut) extends Ahb3CacheTestCase(dut, Some(new Random(43L))) {
    val memory = new Array[Byte](256)
    val seed   = 42L
    val random = new Random(seed)

    fork {
        val stalls = new Random(seed)
        while (true) {
            dut.clockDomain.waitSampling(1 + stalls.nextInt(24))
            dut.io.stallInject #= true
            dut.clockDomain.waitSampling(1 + stalls.nextInt(5))
            dut.io.stallInject #= false
        }
    }

    def writeExpected(addr: Int, sizeExp: Int, value: BigInt): Unit = {
        for (offset <- 0 until (1 << sizeExp)) {
            memory(addr + offset) = (value >> (8 * offset)).toByte
        }
    }

    def readExpected(addr: Int, sizeExp: Int): BigInt = {
        var value = BigInt(0)
        for (offset <- 0 until (1 << sizeExp)) {
            value |= BigInt(memory(addr + offset) & 0xff) << (8 * offset)
        }
        value
    }

    // Initialize the DUT to match the zero-filled byte array.
    for (addr <- memory.indices by ahbCfg.bytePerWord) {
        write(addr, log2Up(ahbCfg.bytePerWord), 0)
    }

    for (operation <- 0 until 10000) {
        val sizeExp = random.nextInt(log2Up(ahbCfg.bytePerWord) + 1)
        val addr    = random.nextInt(memory.length >> sizeExp) << sizeExp
        if (random.nextFloat() < 0.03) {
            if (random.nextBoolean()) {
                dut.io.flush #= true
            } else {
                dut.io.sync #= true
            }
            dut.clockDomain.waitSampling()
            dut.io.flush #= false
            dut.io.sync #= false
        } else {
            if (random.nextBoolean()) {
                val value = BigInt(8 << sizeExp, random)
                write(addr, sizeExp, value)
                writeExpected(addr, sizeExp, value)
            } else {
                expect(addr, sizeExp, readExpected(addr, sizeExp))
            }
        }
    }

    // Check writes that the random reads may have missed.
    for (addr <- memory.indices by ahbCfg.bytePerWord) {
        val sizeExp = log2Up(ahbCfg.bytePerWord)
        expect(addr, sizeExp, readExpected(addr, sizeExp))
    }

    dut.clockDomain.waitSampling(10)

    assert(!hasErrors)
}

object Ahb3CacheTest extends App {
    val compiled = Config.sim
        .compile(Ahb3CacheDut(CacheCfg(2, 4, 8), AhbLite3Config(32, 32)))

    compiled.doSim(this.getClass.getSimpleName + "_basic")(Ahb3CacheTestBasic.apply)
    compiled.doSim(this.getClass.getSimpleName + "_fuzz")(Ahb3CacheTestFuzz.apply)
}
