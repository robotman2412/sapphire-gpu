
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Floating-point interface demultiplexer.
module saph_fpu_demux#(
    // Number of FPU ports.
    parameter   integer fpus    = 1
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // GPU ports.
    saph_fpi.FPU    gpu,
    // FPU port.
    saph_fpi.GPU    fpu[fpus]
);
    genvar x;
    parameter latency = gpu.latency;
    
    initial begin
        if (gpu.latency != fpu[0].latency) begin
            $error("FPU latency mismatch: GPU expects %d, FPU expects %d", gpu.latency, fpu[0].latency);
        end
    end
    
    // Mode availability logic.
    logic[3:0] has_modes_mask[fpus];
    generate
        for (x = 0; x < fpus; x = x + 1) begin
            assign has_modes_mask[x] = fpu[x].has_modes;
        end
    endgenerate
    always @(*) begin
        integer i;
        gpu.has_modes = 0;
        for (i = 0; i < fpus; i = i + 1) begin
            gpu.has_modes |= has_modes_mask[i];
        end
    end
    
    // Request logic.
    /* verilator lint_off UNOPTFLAT */
    logic[fpus-1:0] ready;
    logic[fpus-1:0] has_mode;
    logic[fpus-1:0] trig;
    assign gpu.d_ready = (ready & has_mode) != 0;
    generate
        for (x = 0; x < fpus; x = x + 1) begin
            assign ready[x]         = fpu[x].d_ready;
            assign has_mode[x]      = fpu[x].has_modes[gpu.d_mode];
            assign fpu[x].d_trig    = trig[x];
            assign fpu[x].d_lhs     = gpu.d_lhs;
            assign fpu[x].d_rhs     = gpu.d_rhs;
            assign fpu[x].d_mode    = gpu.d_mode;
        end
        assign trig[0] = has_mode[0] && gpu.d_trig;
        for (x = 1; x < fpus; x = x + 1) begin
            assign trig[x] = has_mode[x] && (ready[x-1:0] & has_mode[x-1:0]) == 0 && gpu.d_trig;
        end
    endgenerate
    /* verilator lint_on UNOPTFLAT */
    
    // Waiting state logic.
    logic[fpus-1:0] q_trig_mask;
    logic[fpus-1:0] wait_reg;
    logic           waiting = wait_reg != 0 && q_trig_mask == 0;
    generate
        for (x = 0; x < fpus; x = x + 1) begin
            assign q_trig_mask[x] = fpu[x].q_trig;
        end
    endgenerate
    always @(posedge clk) begin
        if (rst) begin
            wait_reg <= 0;
        end else if (!waiting) begin
            wait_reg <= trig & ready;
        end
    end
    
    // Pipeline timing logic.
    /* verilator lint_off UNOPTFLAT */
    logic[fpus-1:0] plr[latency+2];
    generate
        assign plr[0]           = plr[1];
        assign plr[latency+1]   = trig & ready;
        for (x = 1; x < latency; x = x + 1) begin
            always @(posedge clk) begin
                plr[x] <= plr[x+1];
            end
        end
        if (latency) begin
            always @(posedge clk) begin
                if (!waiting) begin
                    plr[latency] <= plr[latency + 1];
                end
            end
        end
    endgenerate
    /* verilator lint_on UNOPTFLAT */
    
    // Response logic.
    float res_mask[fpus];
    generate
        for (x = 0; x < fpus; x = x + 1) begin
            assign res_mask[x] = plr[0][x] ? fpu[x].q_res : 0;
        end
    endgenerate
    always @(*) begin
        integer i;
        gpu.q_res = 0;
        for (i = 0; i < fpus; i = i + 1) begin
            gpu.q_res |= res_mask[i];
        end
    end
endmodule
