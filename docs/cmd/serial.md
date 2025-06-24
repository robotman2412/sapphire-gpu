# Sapphire GPU Documentation: Commands (Serial Interface)
*Note: See the [command summary](./README.md#command-summary) for command numbers.*


## READ DMA: Use DMA To Read GPU Memory
This command sets up DMA for reading GPU memory.

Returns nothing.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | alen | addr       | DMA start address
| alen   | alen | size       | DMA transfer size hint (optional)

*Note: alen = 4 for a 32-bit GPU, and alen = 8 for a 64-bit GPU.*


## READ PAYLOAD: Get Payload From Previous READ DMA
Returns the read data as set up by the previous READ DMA command.
More or less bytes may be read than the size hint.
After the transfer for this command ends, the DMA is torn down.

No parameters.


## WRITE DMA: Use DMA To Write GPU Memory
This command sets up DMA for writing GPU memory.

Returns nothing.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | alen | addr       | DMA start address
| alen   | alen | size       | DMA transfer size hint (optional)

*Note: alen = 4 for a 32-bit GPU, and alen = 8 for a 64-bit GPU.*


## WRITE PAYLOAD: Send Payload For Previous WRITE DMA
Specifies the write data as set up by the previous WRITE DMA command.
More or less bytes may be written than the size hint.
After the transfer for this command ends, the DMA is torn down.

Returns nothing.

Parameters: the raw stream of bytes to write.
