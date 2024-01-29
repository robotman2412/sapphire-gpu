
// Copyright © 2024, Julian Scheffers, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Multi-float incrementing unit.
module saph_float_incrementer#(
    // Number of floats to compute.
    parameter count = 2
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // Latch init values.
    input  logic    latch,
    // Enable increment.
    // Overriden by latch.
    input  logic    count,
    
    // Initial state.
    input  float    init[count],
    // Increment values.
    input  float    inc[count],
    // Current state.
    output float    cur[count]
);
endmodule

// Line rasterizer.
module saph_line_rasterizer#(
    // Enable 3D rendering.
    parameter enable_3d     = 1,
    // Enable 4-channel vertex coloring.
    parameter enable_vcol   = 1,
    // Use short floats for computing vertex colors.
    // Ignored if enable_vcol is 0.
    parameter vcol_short    = 1
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // Latch init values.
    input  logic    latch,
    // Enable increment.
    // Overriden by latch.
    input  logic    count,
    
    // Start point.
    input  vertex   vstart,
    // End point.
    input  vertex   vend,
    // Current point.
    output vertex   vcur
);
endmodule
