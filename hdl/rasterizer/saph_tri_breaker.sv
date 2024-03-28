
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// TODO: Breaks triangles into 0-2 trapezoids.
module saph_tri_breaker#(
    // Enable 3D rendering.
    parameter enable_3d     = 1,
    // Enable 4-channel vertex coloring.
    parameter enable_vcol   = 1,
    // Use 8.8-bit fixed-points instead of floats for vertex colors.
    // Ignored if enable_vcol is 0.
    parameter vcol_fixpt    = 1
)(
    // Core clock.
    input  wire         clk,
    // Synchronous reset.
    input  wire         rst
);
endmodule
