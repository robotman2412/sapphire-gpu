# Sapphire GPU Documentation
Sapphire is a project that aims to be a relatively simple 2D and possibly 3D GPU.
It support hardware rasterization, background scanout, many color formats and conversions, float coordinates,
shader cores with architecture TBD and issuing compute work to those same shader cores.

This documentation details the interface that Sapphire exposes to communicate with a host as well ass some technical details and rationale about Sapphire's inner workings.

*Unless otherwise specified, the endianness of data in Sapphire is LITTLE.*

## Documentation Overview
- [Command Descriptions](./cmd/README.md)
    - [Compute Commands](./cmd/compute.md)
    - [Drawing Commands](./cmd/drawing.md)
    - [Management Commands](./cmd/management.md)
    - [Serial-Attached Interface Commands](./cmd/serial.md)
- [Command Interfaces](./interface/README.md)
    - [Memory-Mapped I/O](./interface/mmio.md)
    - [Serial-Attached](./interface/serial.md)
- [Interrupts](./interrupts.md)
- [Data Structures](./structures.md)
