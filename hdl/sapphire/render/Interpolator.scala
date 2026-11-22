package sapphire.render

import sapphire._
import spinal.core._
import spinal.lib._

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

case class InterpolatorConfig(numCounters: Int, numAdders: Int, dataBits: Int, cycleBits: Int)

object IntInterpolator {
    def PacketType(cfg: InterpolatorConfig) = Vec.fill(cfg.numCounters)(UInt(cfg.dataBits bits))

    case class Init(cfg: InterpolatorConfig) extends Bundle {
        val cycles = UInt(cfg.dataBits bits)
        val start  = PacketType(cfg)
        val step   = PacketType(cfg)
    }
}

case class IntInterpolator(cfg: InterpolatorConfig) extends Component {
    import IntInterpolator._
    val cyclePerAdd = (cfg.numCounters + cfg.numAdders - 1) / cfg.numAdders

    val io = new Bundle {
        val cancel = in port Bool()
        val din    = slave port Stream(Init(cfg))
        val dout   = master port Stream(PacketType(cfg))
    }

    val active      = RegInit(False)
    val cycles      = RegInit(U(0, cfg.cycleBits bits))
    val accumulator = Reg(PacketType(cfg))
    val step        = Reg(PacketType(cfg))
    val finish      = cycles === 0 && io.dout.fire
    io.din.ready := !active || finish

    val directAdd = (cyclePerAdd == 1) generate new Area {
        io.dout.valid   := active
        io.dout.payload := accumulator
        when(io.dout.fire) {
            for (i <- 0 until cfg.numCounters) {
                accumulator(i) := accumulator(i) + step(i)
            }
        }
    }
    val sharedAdd = (cyclePerAdd > 1) generate new Area {
        val addCycle = Reg(UInt(log2Up(cyclePerAdd) bits))

        val lhs = Vec.fill(cfg.numAdders)(UInt(cfg.dataBits bits))
        val rhs = Vec.fill(cfg.numAdders)(UInt(cfg.dataBits bits))
        val acc = Vec.fill(cfg.numAdders)(UInt(cfg.dataBits bits))
        lhs.assignDontCare()
        rhs.assignDontCare()
        for (i <- 0 until cfg.numAdders) {
            acc(i) := lhs(i) + rhs(i)
        }

        switch(addCycle) {
            for (i <- 0 until cyclePerAdd) is(i) {
                val start = i * cfg.numAdders
                val end   = ((i + 1) * cfg.numAdders).min(cfg.numCounters)
                for (x <- start until end) {
                    lhs(x - start) := accumulator(x)
                    rhs(x - start) := step(x)
                    when(active && !io.dout.isStall) {
                        accumulator(x) := acc(x - start)
                    }
                }
            }
        }

        io.dout.valid   := active && addCycle === 0
        io.dout.payload := accumulator
        when(finish || io.cancel || io.din.fire) {
            addCycle := 0
        } elsewhen (active && !io.dout.isStall) {
            when(addCycle === (cyclePerAdd - 1)) {
                addCycle := 0
            } otherwise {
                addCycle := addCycle + 1
            }
        }
    }

    when(io.cancel) {
        active := False
        cycles := 0
    } elsewhen (io.din.fire) {
        active      := True
        cycles      := io.din.cycles
        accumulator := io.din.start
        step        := io.din.step
    } elsewhen (finish) {
        active := False
        cycles := 0
    } elsewhen (io.dout.fire) {
        cycles := cycles - 1
    }
}
