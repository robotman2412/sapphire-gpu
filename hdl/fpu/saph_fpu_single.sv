
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Single floating-point computation unit.
module saph_fpu_single#(
    // Enable adder / subtractor.
    parameter   bit     has_add     = 1,
    // Enable multiplier.
    parameter   bit     has_mul     = 1,
    // Enable divider.
    parameter   bit     has_div     = 1,
    
    // Enable pipeline register before add/mul/div.
    parameter   bit     plr_pre     = 1,
    // Enable pipeline register after add/mul/div.
    parameter   bit     plr_post    = 1
)(
    // Core clock.
    input  wire     clk,
    // Synchronous reset.
    input  wire     rst,
    
    // FPU interface.
    saph_fpi.FPU    port
);
    // FPU latency.
    localparam  integer latency = plr_pre + plr_post;
    // Computational unit results.
    float       add_res, mul_res, div_res;
    // Mode select registers.
    logic[1:0]  r_mode_0;
    logic[1:0]  r_mode_1;
    
    assign port.d_ready     = 1;
    assign port.q_trig      = port.d_trig;
    assign port.has_modes   = {has_div, has_mul, has_add, has_add};
    
    initial begin
        if (port.latency != latency) begin
            $error("Invalid latency setting (expected %d; got %d)", latency, port.latency);
        end
    end
    
    generate
        // Pipeline registers.
        if (plr_pre) begin: with_plr_pre
            always @(posedge clk) r_mode_0 <= port.d_mode;
        end else begin: without_plr_pre
            assign r_mode_0 = port.d_mode;
        end
        if (plr_post) begin: with_plr_post
            always @(posedge clk) r_mode_1 <= r_mode_0;
        end else begin: without_plr_post
            assign r_mode_1 = r_mode_0;
        end
        
        // Floating-point adder.
        if (has_add) begin: with_fadd
            float n_rhs;
            svfloat_neg#(float) fneg(port.d_rhs, port.d_mode[0], 1, n_rhs);
            svfloat_add#(float, plr_pre, plr_post) fadd(clk, port.d_lhs, n_rhs, add_res);
        end else begin: without_fadd
            assign add_res = 'bx;
        end
        
        // Floating-point multipler.
        if (has_mul) begin: with_fmul
            svfloat_mul#(float, plr_pre, plr_post) fmul(clk, port.d_lhs, port.d_rhs, mul_res);
        end else begin: without_fmul
            assign mul_res = 'bx;
        end
        
        // Floating-point divider.
        if (has_div) begin: with_fdiv
            svfloat_div#(float, plr_pre, plr_post) fdiv(clk, port.d_lhs, port.d_rhs, div_res);
        end else begin: without_fdiv
            assign div_res = 'bx;
        end
    endgenerate
    
    // Output mux.
    always @(*) begin
        if (has_add && !r_mode_1[1]) begin
            // FP add.
            port.q_res = add_res;
        end else if (has_mul && r_mode_1 == 2'b10) begin
            // FP mul.
            port.q_res = mul_res;
        end else if (has_div && r_mode_1 == 2'b11) begin
            // FP div.
            port.q_res = div_res;
        end else begin
            // Unimplemented.
            port.q_res = 'bx;
        end
    end
endmodule

