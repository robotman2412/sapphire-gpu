
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



module top(
    input logic clk
);
    logic rst = 1;
    reg[31:0] cycle;
    always @(posedge clk) begin
        rst   <= 0;
        cycle <= cycle + 1;
    end
    
    color q;
    saph_colmath math(
        32'h7fff0000,
        32'hff00ff00,
        08'h7f,
        `SAPH_COLMATH_INTERP,
        q
    );
    initial $display("%08x\n%08x\n%02x\n%b\n%08x", math.a, math.b, math.c, math.mode, q);
endmodule
