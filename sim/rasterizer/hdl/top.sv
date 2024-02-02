
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



module top(
    // GPU clock.
    input  logic    clk,
    // GPU has image.
    output logic    vsync
);
    // Reset generation logic.
    logic rst = 1;
    always @(posedge clk) begin
        rst   <= 0;
    end
    
endmodule
