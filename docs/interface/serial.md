# Sapphire GPU Documentation: Serial Command Interface
The serial command interface refers to any configuration wherein Sapphire is connected to a host processor over an SPI-like serial bus.
This interface is characterized by transactions that are either command or data, and comes in two variants: full-duplex and half-duplex.


## Common Operation
A transfer starts with a single command byte, a command-dependent number of parameter bytes, and padding bytes (which are ignored completely by the GPU and must be 0 for forwards compatibility).

Commands that write something to a part of the GPU registers, queues or RAM may either have the write data as part of the command or as sent with a dedicated payload command command.

Commands that have a response will return the response data according to the rules for half- or full-duplex described later. If the transfer in which the GPU sends the response is too small to store the entire response, the GPU will discard the remainder.


## Full-Duplex Operation
In this mode, there are dedicated data lines in either direction and an interrupt request line from the GPU to the host.

Sapphire will actively send data in response to the command of the previous transaction if there is any, regardless of whether the current transaction also has a command or is a [NOP](../cmd/management.md#nop-no-operation).


## Half-Duplex Operation
In this mode, the data lines are shared for both directions, an interrupt request line from the GPU to the host and a "write" line.
Driving the write line HIGH indicates data transfer from the host to the GPU; LOW indicates from the GPU to the host.

Sapphire will send data in response to the command of the previous transaction if the write line is LOW.


## Memory Access Over Serial
With a serial interfae, the GPU is not directly connected to the host and must therefor expose commands to access memory. These commands are specific to the serial command interface.

There are two main commands for this purpose: [READ DMA](../cmd/phys_serial.md#read-dma-use-dma-to-read-gpu-memory) and [WRITE DMA](../cmd/phys_serial.md#write-dma-use-dma-to-write-gpu-memory).
They are somewhat special in their operation in that they use an interrupt to indicate readiness for DMA data transfer.
The host should use the [STATUS](../cmd/management.md#status-read-status-registers) command to tell whether an interrupt is DMA readiness or another interrupt if it had another interrupt source enabled when issuing a DMA command.
To enable this kind of testing and delayed data transfer, the [READ PAYLOAD](../cmd/phys_serial.md#read-payload-get-payload-from-previous-read-dma) and [WRITE PAYLOAD](../cmd/phys_serial.md#write-payload-send-payload-for-previous-write-dma) exist to separate the DMA setup and data stages.
