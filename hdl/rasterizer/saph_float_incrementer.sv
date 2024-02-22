
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Multi-float incrementing unit.
module saph_float_incrementer#(
    // Number of floats to compute.
    parameter   integer numbers = 2
)(
    // Core clock.
    input  logic                clk,
    // Synchronous reset.
    input  logic                rst,
    // Floating-point unit interfaces.
    saph_fpi.GPU                fpi[numbers],
    
    // Latch init values.
    input  logic                latch,
    // Enable increment.
    // Overriden by latch.
    input  logic[numbers-1:0]   count,
    // Incremented value is ready after posedge clk.
    output logic                ready,
    
    // Initial state.
    input  float                init[numbers],
    // Increment values.
    input  float                inc[numbers],
    // Current state.
    output float                cur[numbers]
);
    genvar x;
    localparam integer latency = fpi.latency;
    
    // Additions in progress.
    logic[numbers-1:0]  busy;
    
    // Floating-point interface logic.
    generate
        for (x = 0; x < numbers; x = x + 1) begin
            logic r_busy;
            assign fpi[x].d_trig = trig || r_busy;
            assign fpi[x].lhs    = cur[x];
        end
    endgenerate
    
endmodule
