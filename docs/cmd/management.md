# Sapphire GPU Documentation: Commands (General Management)
*Note: See the [command summary](./README.md#command-summary) for command numbers.*


## NOP: No Operation
This command does nothing.
It serves primarily as a dummy command, having number 0 so that reading a command's response over [full-duplex serial](../interface/serial.md#full-duplex-operation) doesn't require another action be performed.

No parameters.

Returns nothing.


## STATUS: Read Status Registers
Returns the current value of the [status registers](../structures.md#status-registers).

No parameters.


## DESC: Get GPU Description Structure
Returns the [GPU hardware description](../structures.md#gpu-hardware-description) structure.

No parameters.


## IRQ CLEAR: Clear Pending Interrupts
Clears pending interrupts as seen in the [status registers](../structures.md#status-registers).

Returns nothing.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | irq_clear  | Bitmask of [interrupts](../interrupts.md) to be cleared


## IRQ ENABLE: Select Enabled Interrupts
Replaces the value of the irq_enable [status register](../structures.md#status-registers).

Returns nothing.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | irq_enable | Bitmask of which [interrupts](../interrupts.md) should be enabled
