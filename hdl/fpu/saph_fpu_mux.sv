
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Floating-point interface multiplexer.
module saph_fpu_mux#(
    // Number of GPU ports.
    parameter   integer gpus    = 1
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // GPU ports.
    saph_fpi.FPU    gpu[gpus],
    // FPU port.
    saph_fpi.GPU    fpu
);
    genvar x;
    
    initial begin
        if (gpu[0].latency != fpu.latency) begin
            $error("FPU latency mismatch: GPU expects %d, FPU expects %d", gpu[0].latency, fpu.latency);
        end
    end
    
    // GPU requesting to use FPU.
    logic[gpus-1:0] req;
    // Current GPU's turn.
    logic[gpus-1:0] cur;
    // Next GPU's turn.
    logic[gpus-1:0] next;
    // Request arbitration.
    generate
        for (x = 0; x < gpus; x = x + 1) begin
            assign req[x]           = gpu[x].d_trig;
            assign next[x]          = gpu[x].d_trig && gpu[x].d_ready;
            assign gpu[x].has_modes = fpu.has_modes;
        end
        assign gpu[0].d_ready   = fpu.d_ready;
        assign next[0] = req[0] && fpu.d_ready;
        for (x = 1; x < gpus; x = x + 1) begin
            assign gpu[x].d_ready   = req[x-1:0] == 0 && fpu.d_ready;
        end
    endgenerate
    
    always @(posedge clk) begin
        if (next) begin
            cur <= next;
        end
    end
    
    // Connection request logic.
    float       lhs_mask[gpus], rhs_mask[gpus];
    logic[1:0]  mode_mask[gpus];
    generate
        for (x = 0; x < gpus; x = x + 1) begin
            assign gpu[x].q_trig    = next[x] && fpu.q_trig;
            assign gpu[x].q_res     = fpu.q_res;
            assign lhs_mask[x]      = next[x] * gpu[x].d_lhs;
            assign rhs_mask[x]      = next[x] * gpu[x].d_rhs;
            assign mode_mask[x]     = next[x] * gpu[x].d_mode;
        end
    endgenerate
    assign fpu.d_trig = next != 0;
    always @(*) begin
        integer i;
        fpu.d_lhs   = 0;
        fpu.d_rhs   = 0;
        fpu.d_mode  = 0;
        for (i = 0; i < gpus; i = i + 1) begin
            fpu.d_lhs   |= lhs_mask[i];
            fpu.d_rhs   |= rhs_mask[i];
            fpu.d_mode  |= mode_mask[i];
        end
    end
endmodule
