package sapphire

// Copyright (c) 2025 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.core.sim._

object Config {
    def spinal = SpinalConfig(
        targetDirectory = "gen",
        defaultConfigForClockDomains = ClockDomainConfig(
            resetKind = SYNC,
            resetActiveLevel = HIGH
        ),
        onlyStdLogicVectorAtTopLevelIo = true
    )

    def sim =
        SimConfig.withConfig(spinal).withFstWave.setTestPath("$WORKSPACE/$TEST")
}
