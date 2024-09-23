package sapphire

// Copyright © 2024, Julian Scheffers, see LICENSE for info

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
    
    def sim = SimConfig.withConfig(spinal).withFstWave.setTestPath("$WORKSPACE/$TEST")
}
