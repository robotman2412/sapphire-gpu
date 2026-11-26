package sapphire.sim

// Copyright (c) 2024 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import java.lang.Float.floatToIntBits
import sapphire._
import sapphire.texture._
import sapphire.util._
import scala.collection.mutable
import scala.util.Random
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.experimental.math._

case class TextureReadTestBench(cfg: SapphireCfg) extends Component {
    val io = new Bundle {
        val texture = in port TextureRef(cfg)
        val uv      = slave port Stream(Vec.fill(2)(Bits(32 bits)))
        val data    = master port Stream(Bits(32 bits))
        val busy    = out port Bool()
    }

    val random  = new Random(1337)
    val romData = for (i <- 0 until 65536) yield random.nextInt().toByte

    val rom    = new AhbLite3OnChipRom(
        AhbLite3Config(cfg.vaddrBits, 32),
        RomPacker(romData, 32 bits)
    )
    val reader = TextureReader(cfg)

    reader.io.texture := io.texture

    reader.io.uv.payload(0).sign     := io.uv.payload(0)(31)
    reader.io.uv.payload(0).exponent := io.uv.payload(0)(30 downto 23)
    reader.io.uv.payload(0).mantissa := io.uv.payload(0)(22 downto 0)
    reader.io.uv.payload(1).sign     := io.uv.payload(1)(31)
    reader.io.uv.payload(1).exponent := io.uv.payload(1)(30 downto 23)
    reader.io.uv.payload(1).mantissa := io.uv.payload(1)(22 downto 0)

    reader.io.uv.valid := io.uv.valid
    io.uv.ready        := reader.io.uv.ready
    reader.io.data >> io.data
    reader.io.ahb.toAhbLite3 <> rom.io.ahb

    io.busy := reader.io.busy
}

object TextureReadTest extends App {
    Config.sim
        .compile(TextureReadTestBench(SapphireCfg(0x100000000L)))
        .doSim(this.getClass.getSimpleName) { dut =>
            dut.io.uv.valid #= false
            dut.io.data.ready #= true

            dut.clockDomain.forkStimulus(period = 10)
            dut.clockDomain.waitSampling()

            def getWidth  = dut.io.texture.spec.width.toInt
            def getHeight = dut.io.texture.spec.height.toInt
            def getBpp    = dut.io.texture.spec.pixfmt.bpp.toInt
            def getVaddr  = dut.io.texture.vaddr.toLong.toInt

            def driveUv(x: Int, y: Int): Unit = {
                val xf = (x + 0.5f) / getWidth
                val yf = (y + 0.5f) / getHeight
                dut.io.uv.valid #= true
                dut.io.uv.payload(0) #= floatToIntBits(xf)
                dut.io.uv.payload(1) #= floatToIntBits(yf)
                while (!dut.io.uv.ready.toBoolean) {
                    dut.clockDomain.waitSampling()
                }
                dut.clockDomain.waitSampling()
                dut.io.uv.valid #= false
            }

            def getSample(): Long = {
                dut.io.data.ready #= true
                while (!dut.io.data.valid.toBoolean) {
                    dut.clockDomain.waitSampling()
                }
                val raw = dut.io.data.payload.toLong
                dut.clockDomain.waitSampling()
                dut.io.data.ready #= false
                raw & ((1L << getBpp) - 1)
            }

            def readExpected(x: Int, y: Int): Long = {
                var raw   = 0L
                val index = x + y * getHeight
                val bpp   = getBpp
                val vaddr = getVaddr + index * bpp / 8
                for (i <- 0 until (bpp + 7) / 8) {
                    raw |= dut.romData(vaddr + i).toLong << (8 * i)
                }
                raw >>= index * bpp % 8
                raw & ((1L << bpp) - 1)
            }

            val random   = new Random(0xdeadbeef)
            val legalBpp = Seq(1, 2, 4, 8, 16, 24, 32)
            val dims     = Seq((4, 4), (17, 17), (91, 7))
            var errors   = false

            def test(coords: Seq[(Int, Int, Int)]): Unit = {
                if (dut.io.busy.toBoolean) {
                    println(s"t=${simTime()}  DUT should not be busy!")
                    errors = true
                }
                fork {
                    for ((x, y, delay) <- coords) {
                        driveUv(x, y)
                        if (delay > 0) {
                            dut.clockDomain.waitSampling(delay)
                        }
                    }
                }
                dut.clockDomain.waitSampling()
                if (!dut.io.busy.toBoolean) {
                    println(f"t=${simTime()}  DUT should be busy!")
                    errors = true
                }
                for ((x, y, delay) <- coords) {
                    val expect = readExpected(x, y)
                    val actual = getSample()
                    if (expect != actual) {
                        println(f"t=${simTime()}  Read \u001b[31m$actual%x\u001b[0m, expected $expect%x")
                        errors = true
                    }
                }
                if (dut.io.busy.toBoolean) {
                    println(f"t=${simTime()}  DUT should not be busy!")
                    errors = true
                }
            }

            def testLinear(): Unit = {
                val width  = getWidth
                val height = getHeight
                val size   = width * height
                val seq    = new mutable.ArrayBuffer[(Int, Int, Int)]()

                for (i <- 0 until 20) {
                    val start  = random.nextInt(size)
                    val length = random.nextInt(10) + 1
                    for (j <- start until start + length) {
                        seq += ((j % width, j / width % height, 0))
                    }
                }

                test(seq)
            }

            def testEdges(): Unit = {
                val width  = getWidth
                val height = getHeight
                val size   = width * height
                val seq    = new mutable.ArrayBuffer[(Int, Int, Int)]()

                for (x <- 0 until width) {
                    seq += ((x, 0, 0))
                }
                for (y <- 1 until height - 1) {
                    seq += ((width - 1, y, 0))
                }
                for (x <- 0 until width) {
                    seq += ((width - x - 1, 0, 0))
                }
                for (y <- 1 until height - 1) {
                    seq += ((0, height - y - 1, 0))
                }

                test(seq)
            }

            def testRandom(): Unit = {
                val width  = getWidth
                val height = getHeight
                test(
                    for (i <- 0 until 1000)
                        yield (random.nextInt(width), random.nextInt(height), random.nextInt(50) - 40)
                )
            }

            for (bpp <- legalBpp) {
                for ((width, height) <- dims) {
                    while (dut.io.busy.toBoolean) {
                        dut.clockDomain.waitSampling()
                    }
                    dut.io.texture.vaddr #= random.nextInt() & 0xfff
                    dut.io.texture.spec.pixfmt.bpp #= bpp
                    dut.io.texture.spec.width #= width
                    dut.io.texture.spec.height #= height
                    dut.clockDomain.waitSampling()

                    testLinear()
                    testEdges()
                    testRandom()
                }
            }

            assert(!errors)
        }
}
