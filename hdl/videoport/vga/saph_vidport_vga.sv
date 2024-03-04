
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Sapphire GPU VGA/RGB port.
interface saph_vidport_vga#(
    // Number of red bits.
    parameter   integer r_width = 8,
    // Number of green bits.
    parameter   integer g_width = 8,
    // Number of blue bits.
    parameter   integer b_width = 8
);
    // Synchronization signals.
    logic   hsync, vsync;
    // Color signals.
    logic[r_width-1:0]  r;
    logic[g_width-1:0]  g;
    logic[b_width-1:0]  b;
    
    // Port from GPU perspective.
    modport GPU (output hsync, vsync, r, g, b);
    // Port from monitor perspective.
    modport MON (input  hsync, vsync, r, g, b);
endinterface
