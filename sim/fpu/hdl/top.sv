
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



module top(
    input logic clk
);
    parameter plr_pre  = 1;
    parameter plr_post = 1;
    
    logic rst = 1;
    reg[31:0] cycle;
    always @(posedge clk) begin
        rst   <= 0;
        cycle <= cycle + 1;
    end
    
    saph_fpi#(plr_pre + plr_post) fpi[2]();
    saph_fpu#(2, 1, 1, 1, plr_pre, plr_post) fpu(clk, rst, fpi);
    
    always @(*) begin
        fpi[0].d_trig = 0;
        fpi[0].d_lhs  = 0;
        fpi[0].d_rhs  = 0;
        fpi[0].d_mode = 0;
        fpi[1].d_trig = 0;
        fpi[1].d_lhs  = 0;
        fpi[1].d_rhs  = 0;
        fpi[1].d_mode = 0;
        if (cycle == 1) begin
            fpi[0].d_trig = 1;
            fpi[0].d_lhs  = `fconst(-1);
            fpi[0].d_rhs  = `fconst(5);
            fpi[0].d_mode = `SAPH_FPU_ADD;
        end else if (cycle == 2) begin
            fpi[0].d_trig = 1;
            fpi[0].d_lhs  = `fconst(-3.5);
            fpi[0].d_rhs  = `fconst(4);
            fpi[0].d_mode = `SAPH_FPU_MUL;
            fpi[1].d_trig = 1;
            fpi[1].d_lhs  = `fconst(9.7);
            fpi[1].d_rhs  = `fconst(0.5);
            fpi[1].d_mode = `SAPH_FPU_MUL;
        end else if (cycle == 3) begin
            fpi[0].d_trig = 1;
            fpi[0].d_lhs  = `fconst(192);
            fpi[0].d_rhs  = `fconst(64);
            fpi[0].d_mode = `SAPH_FPU_DIV;
            fpi[1].d_trig = 1;
            fpi[1].d_lhs  = `fconst(9.7);
            fpi[1].d_rhs  = `fconst(0.5);
            fpi[1].d_mode = `SAPH_FPU_MUL;
        end
    end
endmodule
