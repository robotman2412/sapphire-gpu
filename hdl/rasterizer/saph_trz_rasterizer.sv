
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Trapezoid and line rasterizer.
module saph_trz_rasterizer#(
    // Enable 3D rendering.
    parameter enable_3d     = 1,
    // Enable 4-channel vertex coloring.
    parameter enable_vcol   = 1,
    // Use 8.8-bit fixed-points instead of floats for vertex colors.
    // Ignored if enable_vcol is 0.
    parameter vcol_fixpt    = 1
)(
    // Core clock.
    input  logic        clk,
    // Synchronous reset.
    input  logic        rst,
    // FPU interface.
    saph_fpi.GPU        fpi,
    
    // Trigger rendering of the shape.
    input  logic        in_trig,
    // Render a line instead of a trapezoid.
    input  logic        in_is_line,
    // Shape vertices.
    input  vertex       in_shape[4],
    // Ready to accept a new shape next posedge clk.
    output logic        in_ready,
    
    // Trigger plotting / shading of the pixel.
    output logic        out_trig,
    // Calculated pixel to render.
    output pixel        out_pixel,
    // Ready to accept a new pixel next posedge clk.
    input  logic        out_ready
);
endmodule
