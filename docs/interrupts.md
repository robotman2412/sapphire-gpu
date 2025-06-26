# Sapphire GPU Documentation: Interrupts
[← Back to Sapphire GPU Documentation](./README.md)

Sapphire can use an interrupt signal to notify the host of an asynchronous event it's interested in.
The internal interrupts that trigger the external interrupt line can be configured with the [IRQ ENABLE](./cmd/management.md#irq-enable-select-enabled-interrupts) command.


## Interrupts Overview
| No. | Name        | Description
| :-- | :---------- | :----------
| 0   | dma_ready   | DMA setup completed; data transfer may begin
| 1   | dma_error   | DMA setup or data transfer error
| 2   | cmd_error   | An unknown command was specified
| 3   | param_error | A command was provided unsupported parameters


## dma_ready: DMA Setup Completed
When the [READ DMA](./cmd/serial.md#read-dma-use-dma-to-read-gpu-memory) or [WRITE DMA](./cmd/serial.md#write-dma-use-dma-to-write-gpu-memory) command is issued, the GPU will internally prepare for DMA.
This interrupt is asserted when the preparation is done.
Then, the GPU is immediately ready for data transfer using the [READ PAYLOAD](./cmd/serial.md#read-payload-get-payload-from-previous-read-dma) or [WRITE PAYLOAD](./cmd/serial.md#write-payload-send-payload-for-previous-write-dma) respectively.


## dma_error: DMA Fatal Error
A fatal error occurred during the setup or data stage of a DMA transfer.
The following reasons may cause this interrupt:
- Invalid DMA start address
- DMA transfer data stage reached invalid address
- Internal memory failure in transfer data stage

## cmd_error: Unkown Command Error
An unknown or unsupported command is issued.

## param_error: Bad Parameters Error
The parameters to a command were bad, invalid or otherwise unsupported.
