
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Sapphire GPU pixel lookup port.
interface saph_pixreadport#(
    // Latency from d_trig to result valid.
    parameter   integer     latency     = 0
);
    // GPU -> MEM: Trigger pixel lookup.
    logic       d_trig;
    // GPU -> MEM: Pixel coordinates.
    logic[13:0] d_x, d_y;
    // MEM -> GPU: Ready to perform pixel lookup.
    logic       d_ready;
    
    // FPU -> MEM: Retrieved pixel data.
    color       q_res;
    
    // Signals from GPU perspective.
    modport GPU (output d_trig, d_x, d_y, input  d_ready, q_res);
    // Signals from MEM perspective.
    modport MEM (input  d_trig, d_x, d_y, output d_ready, q_res);
endinterface
