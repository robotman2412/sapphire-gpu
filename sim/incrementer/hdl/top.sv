
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



module top(
    // GPU clock.
    input  logic    clk
);
    // Reset generation logic.
    logic       rst   = 1;
    logic[31:0] cycle = 0;
    always @(posedge clk) begin
        rst   <= 0;
        cycle <= cycle + 1;
    end
    
    // Initialization logic.
    wire        latch    = cycle == 1;
    logic[1:0]  count;
    float       init[2];
    assign      init[0]  = `fconst(1.010);
    assign      init[1]  = `fconst(3.141);
    float       inc[2];
    assign      inc[0]   = `fconst(0.125);
    assign      inc[1]   = `fconst(0.001);
    
    always @(*) begin
        count = 0;
        if (cycle == 2) count = 3;
        if (cycle == 6) count = 1;
    end
    
    // The FPU.
    saph_fpi#(2) fpi[2]();
    saph_fpu#(2, 1, 0, 0) fpu(clk, rst, fpi);
    
    // The incrementoronatorinficator.
    float cur[2];
    logic ready;
    saph_float_incrementer#(2) calc(clk, rst, fpi, latch, count, ready, init, inc, cur);
    
endmodule
