# Sapphire GPU Documentation: Data Structures


## Status Registers
The status registers serve as a quick overview of the general state and healthe of the GPU.
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | irq_state  | Which [interrupts](./interrupts.md) are asserted, regardless of enable
| 4      | 4    | irq_enable | Which [interrupts](./interrupts.md) are currently enabled


## GPU Hardware Description
The GPU hardware description struct conveys static information to the driver, both for things that the driver must support and things that are optional for the driver to support. This struct is constant for a given implementation of Sapphire.
| Offset | Size   | Name              | Description
| :----- | :----- | :---------------- | :----------
| 0      | 1      | gpu_major         | SEMVER major revision of the GPU
| 1      | 1      | gpu_minor         | SEMVER minor revision of the GPU
| 2      | 1      | gpu_minor         | SEMVER patch revision of the GPU
| 3      | 1      | scanout_count     | Number of scanout engines
| 4      | 4      | irq_impl          | Bitset of implemented [interrupts](./interrupts.md)
| 8      | 8      | ram_size          | Byte size of the GPU's RAM
| 16     | 4      | required_features | Bitset of features that the driver is required to support
| 24     | 4      | optional_features | Bitset of features that the driver may optionally support

*Note: The SEMVER revision of the GPU also identifies the readability of this structure to older drivers.*

The bitfield required_features:
| Bit | Name     | Description
| --- | :------- | :----------
| 0   | is_64bit | The GPU uses 64-bit pointers instead of 32-bit ones

*Note: The driver is required to reject the GPU if required_features.is_64bit = 0 and it does not support 32-bit GPUs.*

The bitfield optional_features:
| Bit | Name     | Description
| :-- | :------- | :----------
| 0   | has_3d   | The GPU has support for 3D rendering


