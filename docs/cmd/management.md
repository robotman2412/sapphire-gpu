# Sapphire GPU Documentation: Commands (General Management)
[← Back to Sapphire GPU Documentation: Commands](./README.md)


## NOP: No Operation
This command does nothing.
It serves primarily as a dummy command, having number 0 so that reading a command's response over [full-duplex serial](../interface/serial.md#full-duplex-operation) doesn't require another action be performed.

Code: 0.

No parameters.

Returns nothing.


## STATUS: Read Status Registers
Code: 1.

No parameters.

Returns the current value of the [status registers](../structures.md#status-registers).


## DESC: Get GPU Description Structure
Code: 2.

No parameters.

Returns the [GPU hardware description](../structures.md#gpu-hardware-description) structure.


## IRQ CLEAR: Clear Pending Interrupts
Clears pending interrupts as seen in the [status registers](../structures.md#status-registers).

Code: 3.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | irq_clear  | Bitmask of [interrupts](../interrupts.md) to be cleared

Returns value layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | irq_clear  | Bitmask of [interrupts](../interrupts.md) that were pending


## IRQ ENABLE: Select Enabled Interrupts
Replaces the value of the irq_enable [status register](../structures.md#status-registers).

Code: 4.

Parameter layout:
| Offset | Size | Name       | Description
| :----- | :--- | :--------- | :----------
| 0      | 4    | irq_enable | Bitmask of which [interrupts](../interrupts.md) should be enabled

Returns nothing.
