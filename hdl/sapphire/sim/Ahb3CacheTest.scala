package sapphire.sim

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import sapphire.mem._
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import sapphire.Config
import spinal.core.sim.SimBoolPimper

case class Ahb3CacheDut(cacheCfg: CacheCfg, ahbCfg: AhbLite3Config) extends Component {
    val io = new Bundle {
        val ahb   = slave port AhbLite3(ahbCfg)
        val sync  = in port Bool()
        val flush = in port Bool()
        val clear = in port Bool()
        val busy  = out port Bool()
    }

    val cache = AhbLite3Cache(cacheCfg, ahbCfg)
    val ram   = AhbLite3OnChipRam(ahbCfg, 0x1000)

    cache.io.slave <> io.ahb
    cache.io.master <> ram.io.ahb
    cache.io.sync  := io.sync
    cache.io.flush := io.flush
    cache.io.clear := io.clear
    io.busy        := cache.io.busy
}

object Ahb3CacheTest extends App {

    Config.sim
        .compile(Ahb3CacheDut(CacheCfg(2, 4, 8), AhbLite3Config(32, 32)))
        .doSim(this.getClass.getSimpleName) { dut =>
            val cacheCfg  = dut.cacheCfg
            val ahbCfg    = dut.ahbCfg
            var hasErrors = false

            dut.io.clear #= false
            dut.io.flush #= false
            dut.io.sync #= false

            dut.io.ahb.HTRANS #= 0 // IDLE
            dut.io.ahb.HBURST #= 0
            dut.io.ahb.HPROT #= 0xc
            dut.io.ahb.HMASTLOCK #= false
            dut.io.ahb.HSEL #= true
            dut.io.ahb.HREADY #= true

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

                sleep(1)
                while (!dut.io.ahb.HREADYOUT.toBoolean) {
                    dut.clockDomain.waitSampling()
                    sleep(1)
                }
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

                sleep(1)
                while (!dut.io.ahb.HREADYOUT.toBoolean) {
                    dut.clockDomain.waitSampling()
                    sleep(1)
                }
                val raw = dut.io.ahb.HRDATA.toBigInt

                (raw >> (8 * index)) & ((BigInt(1) << (8 * size)) - 1)
            }

            def expect(addr: Long, sizeExp: Int, value: BigInt): BigInt = {
                val actual = read(addr, sizeExp)
                print(f"$addr%04x:  ")
                if (actual != value) {
                    hasErrors = true
                    print("\u001b[31m")
                }
                print(f"$actual%x")
                println(f"\u001b[0m  (expected $value%x)  t=${simTime()}")
                actual
            }

            forkSensitive(dut.io.ahb.HREADYOUT) {
                dut.io.ahb.HREADY #= dut.io.ahb.HREADYOUT.toBoolean
            }
            dut.clockDomain.forkStimulus(period = 10)

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
}
