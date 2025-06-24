# Sapphire GPU Documentation: Commands
Sapphire is heavily reliant on the concept of commands. All explicit communication with between the host and the GPU works though commands, their parameters and their responses. Sapphire also supports raising interrupts, but this is optional and only serves to notify that something the host is interested in happened.

The commands are divided into categories as follows:
- [Compute Pipeline Commands](./compute.md)
- [Draw Call Commands](./drawing.md)
- [General Management Commands](./management.md)
- [Serial Interface Commands](./phys_serial.md) (only exist with the [serial interface](../interface/serial.md))


## Command Summary
| No. | Name                                                                                | Description
| :-- | :---------------------------------------------------------------------------------- | :----------
| 0   | [NOP](./management.md#nop-no-operation)                                             | Does nothing, successfully
| 1   | [STATUS](./management.md#status-read-status-registers)                              | Get status information about the GPU
| 2   | [DESC](./management.md#desc-get-gpu-description-structure)                          | Get GPU hardware description structure
| 3   | [IRQ CLEAR](./management.md#irq-clear-clear-pending-interrupts)                     | Clear pending interrupts
| 4   | [IRQ ENABLE](./management.md#irq-enable-select-enabled-interrupts)                  | Select enabled interrupts
| 8   | [READ DMA](./serial.md#read-dma-use-dma-to-read-gpu-memory)                         | Set up DMA for reading
| 9   | [READ PAYLOAD](./serial.md#read-payload-get-payload-from-previous-read-dma)         | Receive DMA read data
| 10  | [WRITE DMA](./serial.md#write-dma-use-dma-to-write-gpu-memory)                      | Set up DMA for writing
| 11  | [WRITE PAYLOAD](./serial.md#write-payload-send-payload-for-previous-write-dma)      | Send DMA write data
