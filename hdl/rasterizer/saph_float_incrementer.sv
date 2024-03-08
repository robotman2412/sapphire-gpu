
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Multi-float incrementing unit.
module saph_float_incrementer#(
    // Number of floats to compute.
    parameter   integer numbers = 2,
    // Floating-point interface latency.
    parameter   integer latency = 2
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
    
    initial begin
        if (fpi[0].latency != latency) begin
            $error("Latency mismatch: GPU expects %d, FPU expects %d", latency, fpi[0].latency);
        end
    end
    
    // Additions in progress.
    logic[numbers-1:0]  busy, r_busy, we;
    
    // Increment values.
    float               r_inc[numbers];
    always @(posedge clk) begin
        if (rst) begin
            integer i;
            for (i = 0; i < numbers; i = i + 1) begin
                r_inc[i] <= 0;
            end
        end else if (latch) begin
            r_inc <= inc;
        end
    end
    
    logic[numbers-1:0] trig;
    generate
        for (x = 0; x < numbers; x = x + 1) begin
            // FP request logic.
            assign fpi[x].d_trig = count[x] || r_busy[x];
            assign fpi[x].d_lhs  = cur[x];
            assign fpi[x].d_rhs  = r_inc[x];
            assign fpi[x].d_mode = `SAPH_FPU_FADD;
            assign trig[x]       = fpi[x].d_trig;
            
            always @(posedge clk) begin
                r_busy[x] <= fpi[x].d_trig && !fpi[x].d_ready && !rst;
            end
            assign busy[x] = fpi[x].d_trig && !fpi[x].d_ready;
            
            // FP response logic.
            saph_plr#(1, latency) we_comp(clk, rst, fpi[x].d_trig && fpi[x].d_ready, we[x]);
            always @(posedge clk) begin
                if (rst) begin
                    cur[x] <= 0;
                end else if (latch) begin
                    cur[x] <= init[x];
                end else if (we[x]) begin
                    cur[x] <= fpi[x].q_res;
                end
            end
        end
    endgenerate
    
    // Ready logic.
    saph_plr#(1, latency) rdy_comp(clk, rst || busy != 0, trig != 0, ready);
endmodule
