package sapphire.util

// SPDX-License-Identifier: CERN-OHL-P-2.0
// SPDX-CopyRightText: 2025 Julian Scheffers <julian@scheffers.net>

import spinal.core._
import spinal.lib._

/** A handshake where the receiver determines data flow; the conceptual opposite
  * of a `Flow`. The master provides the data while the slave controls the flow.
  */
case class Vacuum[T <: Data](DType: HardType[T])
    extends Bundle
    with IMasterSlave {

    /** Data to be received when ready. */
    val payload = DType()

    /** Will now receive data. */
    val ready = Bool()

    override def asMaster() = {
        out(payload); in(ready)
    }

    def toStream(underflow: Bool): Stream[T] = {
        val stream = Stream(DType()).setCompositeName(this, "toStream", true)
        if (underflow != null) {
            underflow := ready && !stream.valid
        }
        stream.payload := payload
        ready := stream.ready
        stream
    }

    def connectFrom(that: Vacuum[T]): Vacuum[T] = {
        that.ready   := this.ready
        this.payload := that.payload
        that
    }

    def <<(that: Vacuum[T]): Vacuum[T] = connectFrom(that)
    def >>(that: Vacuum[T]): Vacuum[T] = that.connectFrom(this)
}
