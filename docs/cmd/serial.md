# Sapphire GPU Documentation: Commands (Serial Interface)
[← Back to Sapphire GPU Documentation: Commands](./README.md)


## READ DMA: Use DMA To Read GPU Memory
This command sets up DMA for reading GPU memory.

Code: 8.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | alen | addr       | DMA start address

Returns nothing.

*Note: alen = 4 for a 32-bit GPU, and alen = 8 for a 64-bit GPU.*


## READ PAYLOAD: Get Payload From Previous READ DMA
Returns the read data as set up by the previous READ DMA command.
The number of bytes read determines the transfer length.
Use [DMA END](#dma-end-tear-down-dma-transfer) to tear down the DMA after reading the response.

Code: 9.

No parameters.

Returns: the raw stream of bytes read.


## WRITE DMA: Use DMA To Write GPU Memory
This command sets up DMA for writing GPU memory.

Code: 10.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | alen | addr       | DMA start address

Returns nothing.

*Note: alen = 4 for a 32-bit GPU, and alen = 8 for a 64-bit GPU.*


## WRITE PAYLOAD: Send Payload For Previous WRITE DMA
Specifies the write data as set up by the previous WRITE DMA command.
The number of bytes written determines the transfer length.
Use [DMA END](#dma-end-tear-down-dma-transfer) to tear down the DMA after sending the payload.

Code: 11.

Parameters: the raw stream of bytes to write.

Returns nothing.


## DMA END: Tear Down DMA Transfer
Tears down the current DMA transfer. The driver calls this command DMA TEARDOWN.

Code: 14.

No parameters.

Returns nothing.


## IOREAD: Read An I/O Register
Reads a 32-bit GPU I/O register in the 24-bit I/O address space over the internal APB bus.

Code: 15.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 3    | addr       | I/O register byte address

Return value layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | data       | I/O register value


## IOWRITE: Write An I/O Register
Writes a 32-bit GPU I/O register in the 24-bit I/O address space over the internal APB bus.

Code: 16.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 3    | addr       | I/O register byte address
| 3      | 4    | data       | Value to write

Returns nothing.
