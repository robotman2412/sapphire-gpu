package sapphire

import sapphire.mem._
import scala.collection.mutable._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.bus.amba3.apb._
import sapphire.mem.Ahb3ToDma
import spinal.lib.bus.misc.SizeMapping
import sapphire.util.Vacuum
import sapphire.interface.cmd.CmdEngine

// Copyright (c) 2026 Julian Scheffers
// SPDX-License-Identifier: CERN-OHL-P-2.0

object SapphireGpu {
    sealed trait MemoryType

    /** External AHB memory bus with 32-bit data. */
    case object Ahb3 extends MemoryType

    /** Memory access through the DMA bus infrastructure. */
    case object Dma extends MemoryType

    sealed trait HostInterface

    /** Serial command interface to host. */
    case object Serial extends HostInterface

    /** Memory-mapped I/O interface to host. */
    case object Mmio extends HostInterface

    /** Connection to hook into the I/O space of the GPU. */
    private case class ApbConnection(apb: Apb3, base: Int, size: Int)

    /** Context for embedding components within Sapphire. Provides helpers needed to connect to Sapphire's internal
      * buses.
      */
    case class ComponentCtx(cfg: SapphireCfg) {
        private[SapphireGpu] var addAhb = MutableList[AhbLite3]()
        private[SapphireGpu] var addApb = MutableList[ApbConnection]()
        private[SapphireGpu] var addDma = MutableList[DmaBus]()
        private[SapphireGpu] var addIo  = MutableList[Bundle with IMasterSlave]()

        /** Connect into the external memory bus as master.
          *
          * The AHB-lite 3 bus must be in the component's `io` bundle and be a `master`.
          */
        def connectAhb(ahb: AhbLite3): Unit = {
            addAhb += ahb
        }

        /** Connect into the I/O bus as slave.
          *
          * The APB bus must be in the component's `io` bundle and be a `slave`.
          */
        def connectApb(apb: Apb3, base: Int, size: Int): Unit = {
            addApb += ApbConnection(apb, base, size)
        }

        /** Connect into the DMA subsystem.
          *
          * The DMA bus must be in the component's `io` bundle and be a `master`.
          */
        def connectDma(dma: DmaBus): Unit = {
            addDma += dma
        }

        /** Connect custom I/O wires to Sapphire's top-level component. Returns the index in [[SapphireGpu.io.custom]]
          * where the bundle is exposed. The provided bundle must implement [[IMasterSlave]] as master and be clonable.
          *
          * If possible, consider using a standard component's external interface facilities instead, for example
          * [[GenericScanout.IO.external]].
          */
        def exposeIo(io: Bundle with IMasterSlave): Int = {
            val len = addIo.length
            addIo += io
            len
        }
    }

    /** Sapphire GPU builder class. */
    case class Builder() {
        private[SapphireGpu] var ramSize: Option[Long]                = None;
        private[SapphireGpu] var memoryType: Option[MemoryType]       = None;
        private[SapphireGpu] var hostInterface: Option[HostInterface] = None;

        private[SapphireGpu] var coordBits    = 12;
        private[SapphireGpu] var textureSlots = 2;
        private[SapphireGpu] var plCfg        = SapphirePlCfg();
        private[SapphireGpu] var has3D        = false;
        private[SapphireGpu] var hasVCol      = false;
        private[SapphireGpu] var hasMmu       = false;
        private[SapphireGpu] var hasBuilt     = false;

        private[SapphireGpu] var scanout = MutableList[ComponentCtx => GenericScanout]()
        private[SapphireGpu] var custom  = MutableList[ComponentCtx => Component]()

        def withRamSize(size: Long) = {
            assert(ramSize.isEmpty, "Cannot specify ramSize multiple times")
            ramSize = Some(size)
            this
        }

        private def withMemoryType(_type: MemoryType) = {
            assert(
                memoryType.isEmpty,
                "Cannot specify memory type multiple times"
            )
            memoryType = Some(_type)
            this
        }

        def withAhb3() = withMemoryType(Ahb3)

        def withDma() = withMemoryType(Dma)

        private def withHostInterface(interface: HostInterface) = {
            assert(
                hostInterface.isEmpty,
                "Cannot specify memory interface multiple times"
            )
            hostInterface = Some(interface)
            this
        }

        def withSerialInterface() = {
            withHostInterface(Serial)
        }

        def withMmioInterface() = {
            withHostInterface(Mmio)
        }

        def withCoordBits(bits: Int) = {
            coordBits = bits
            this
        }

        def withTextureSlots(slots: Int) = {
            textureSlots = slots
            this
        }

        def withPlCfg(cfg: SapphirePlCfg) = {
            plCfg = cfg
            this
        }

        def with3D(enabled: Boolean = true) = {
            has3D = enabled
            this
        }

        def withVCol(enabled: Boolean = true) = {
            hasVCol = enabled
            this
        }

        def withMmu(enabled: Boolean = true) = {
            hasMmu = enabled
            this
        }

        /** Add a scanout engine to the GPU.
          *
          * Its external I/O interfaces are visible at [[SapphireGpu.io.scanout]].
          */
        def addScanout(gen: ComponentCtx => GenericScanout) = {
            scanout += gen
            this
        }

        /** Add a custom internal component to Sapphire.
          *
          * **WARNING: Do not use this for standard components such as scanout engines, as it they will not integrate
          * with the GPU correctly!**
          */
        def addComponent(gen: ComponentCtx => Component) = {
            custom += gen
            this
        }

        def derivedConfig = SapphireCfg(
            ramSize.get,
            scanout.length,
            coordBits,
            textureSlots,
            plCfg,
            has3D,
            hasVCol,
            hasMmu
        )

        def build(): SapphireGpu = {
            assert(!hasBuilt, "SapphireGpu builder cannot be re-used")
            assert(
                memoryType.nonEmpty,
                "Must choose memory type before building"
            )
            assert(
                hostInterface.nonEmpty,
                "Must choose host interface before building"
            )
            hasBuilt = true
            new SapphireGpu(this)
        }
    }

    private def forwardExposedIo[T <: Bundle with IMasterSlave](
        internal: T
    ): T = {
        if (internal == null) return null.asInstanceOf[T]
        val external =
            master(internal.clone().asInstanceOf[internal.type])
        internal <> external
        external
    }

    object reg {

        /** MMIO: scanout engines block. */
        val scanoutEngines: Int = 0x010000

        /** MMIO: Texture slots block. */
        val textures: Int = 0x020000
    }
}

/** Sapphire 2D GPU. Use [[SapphireGpu$.Builder]] to build it. */
case class SapphireGpu private (private val builder: SapphireGpu.Builder) extends Component {
    import SapphireGpu._
    val cfg           = builder.derivedConfig
    val memoryType    = builder.memoryType.get
    val hostInterface = builder.hostInterface.get

    private val componentCtx = ComponentCtx(cfg)

    // Build generated internal components.
    // Needs to happen before I/O construction as custom components can register I/O bundles.
    val scanout = for (gen <- builder.scanout) yield gen(componentCtx)
    val custom  = for (gen <- builder.custom) yield gen(componentCtx)

    val io = new Bundle {

        /** MMIO access to scanout engines and custom components. */
        val apb = (hostInterface == Mmio) generate { slave port Apb3(24, 32) }

        /** AHB-lite 3 external memory bus. See [[SapphireGpu$.Builder.withAhb]].
          */
        val ahb = (memoryType == Ahb3) generate {
            master port AhbLite3(AhbLite3Config(cfg.vaddrBits, 32))
        }

        /** DMA bus connecting to external memory. See [[SapphireGpu$.Builder.withDma]].
          */
        val dma = (memoryType == Dma) generate {
            master port DmaBus(cfg.vaddrBits bits)
        }

        /** Active-high chip select input. */
        val chipSelect = (hostInterface == Serial) generate { in port Bool() }

        /** Serial receive data. */
        val rxd =
            (hostInterface == Serial) generate { slave port Flow(Bits(8 bits)) }

        /** Serial transmit data. */
        val txd =
            (hostInterface == Serial) generate { master port Vacuum(Bits(8 bits)) }

        /** Interrupt output. */
        val irqOut = out port Bool()

        /** Scanout engines' external I/O interfaces. */
        val scanoutIo = scanout.map(x => forwardExposedIo(x.io.external))

        /** Custom components' custom I/O interfaces. */
        val customIo = componentCtx.addIo.map(forwardExposedIo)
    }

    /* ======== Internal bus definitions ======== */
    private var scanoutApbSlaves = scanout.zipWithIndex.map(x =>
        ApbConnection(
            x._1.io.apb,
            reg.scanoutEngines + x._2 * sapphire.scanout.scanoutEngineStride,
            sapphire.scanout.scanoutEngineStride
        )
    )

    private var ahbMasters = componentCtx.addAhb.clone()
    private var dmaMasters = componentCtx.addDma.clone()
    private var apbSlaves  = scanoutApbSlaves ++ componentCtx.addApb

    // Only AHB clients need translation when main memory uses DMA.
    val ahbToDma = (memoryType == Dma && ahbMasters.nonEmpty) generate {
        Ahb3ToDma(AhbLite3Config(cfg.vaddrBits, 32))
    }
    if (ahbToDma != null) dmaMasters += ahbToDma.io.dma

    // All native DMA clients share this bus directly. With AHB main memory,
    // one converter after the mux will serve all of them.
    val dma = DmaBus(cfg.vaddrBits bits)
    if (memoryType == Dma) {
        io.dma <> dma
    } else if (dmaMasters.nonEmpty) {
        // TODO: Connect DMA masters to the AHB master.
    }

    val ahb = AhbLite3(AhbLite3Config(cfg.vaddrBits, 32))
    if (memoryType == Ahb3) {
        io.ahb <> ahb
    } else if (ahbToDma != null) {
        ahbToDma.io.ahb << ahb
    } else {
        ahb.HRDATA    := 0
        ahb.HRESP     := False
        ahb.HREADYOUT := True
    }

    /* ======== Host interface and interrupts ======== */
    val interrupt = Bits(32 bits)
    interrupt := 0

    val cmdEngine = (hostInterface == Serial) generate CmdEngine(cfg)
    // TODO: MMIO host interface.

    val apb = Apb3(Apb3Config(24, 32))
    if (hostInterface == Serial) {
        apb <> cmdEngine.io.apb
        dmaMasters = cmdEngine.io.dma +: dmaMasters // Command engine gets first dibs on the DMA bus.
        cmdEngine.io.rxd << io.rxd
        cmdEngine.io.txd >> io.txd
        cmdEngine.io.chipSelect := io.chipSelect
        cmdEngine.io.irqIn      := interrupt
        io.irqOut               := cmdEngine.io.irqOut
    } else {
        apb <> io.apb
    }

    /* ======== Internal component bus connections ======== */
    if (ahbMasters.nonEmpty) {
        val arbiter = AhbLite3Arbiter(ahb.config, ahbMasters.length)
        for ((bus, input) <- ahbMasters.zip(arbiter.io.inputs)) {
            input << bus
        }
        ahb << arbiter.io.output
    } else {
        ahb.HADDR     := 0
        ahb.HSEL      := False
        ahb.HREADY    := True
        ahb.HWRITE    := False
        ahb.HSIZE     := 0
        ahb.HBURST    := 0
        ahb.HPROT     := 0
        ahb.HTRANS    := AhbLite3.IDLE
        ahb.HMASTLOCK := False
        ahb.HWDATA    := 0
    }

    if (dmaMasters.nonEmpty) {
        val mux = DmaMux(dmaMasters.length, cfg.vaddrBits)
        for ((bus, controller) <- dmaMasters.zip(mux.io.controllers)) {
            controller <> bus
        }
        dma <> mux.io.target
    } else {
        dma.setup.setup    := False
        dma.setup.teardown := False
        dma.setup.write    := False
        dma.setup.addr     := 0
        dma.rdata.ready    := False
        dma.wdata.valid    := False
        dma.wdata.payload  := 0
    }

    if (apbSlaves.nonEmpty) {
        Apb3Decoder(
            apb,
            apbSlaves.map(x => (x.apb, SizeMapping(x.base, x.size)))
        )
    } else {
        apb.PREADY    := True
        apb.PRDATA    := 0
        apb.PSLVERROR := True
    }
}
