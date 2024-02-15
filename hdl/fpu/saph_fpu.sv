
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Floating-point computation unit.
module saph_fpu#(
    // Number of ports.
    parameter   integer ports       = 1,
    
    // Number of adders.
    parameter   integer n_add       = 1,
    // Number of multipliers.
    parameter   integer n_mul       = 1,
    // Number of dividers.
    parameter   integer n_div       = 1,
    
    // Enable pipeline register before add/mul/div.
    parameter   bit     plr_pre     = 1,
    // Enable pipeline register after add/mul/div.
    parameter   bit     plr_post    = 1
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // FPU interfaces.
    saph_fpi.FPU    port[ports]
);
    genvar x;
    
endmodule

