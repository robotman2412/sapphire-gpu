package sapphire.mem

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

import spinal.core._
import spinal.lib._

case class CacheCfg(
    ways: Int,
    lines: Long,
    lineSize: Int = 64
) {
    assert(ways >= 1, "`ways` must be at least 1")
    assert(lines >= 4, "`lines` must be at least 4")
    assert(lineSize >= 1, "`lineSize` must be at least 1")
    assert((lines & (lines - 1)) == 0, "`lines` must be a power of two")
    assert(
        (lineSize & (lineSize - 1)) == 0,
        "`lineSize` must be a power of two"
    )

    val totalSize = ways * lines * lineSize
}
