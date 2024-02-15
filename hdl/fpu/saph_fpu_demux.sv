
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
    
    if (gpu.latency != fpu[0].latency) begin
        $error("FPU latency mismatch: GPU expects %d, FPU expects %d", gpu.latency, fpu[0].latency);
    end
    
    // Mode availability logic.
    always @(*) begin
        integer i;
        gpu.has_modes = 0;
        for (i = 0; i < fpus; i = i + 1) begin
            gpu.has_modes |= fpu[i].has_modes;
        end
    end
    
    // Request logic.
    logic[fpus-1:0] ready;
    logic[fpus-1:0] trig;
    generate
        for (x = 0; x < fpus; x = x + 1) begin
            assign ready[x]     = fpu[x].d_ready && fpu[x].has_modes[gpu.d_mode];
            assign fpu[x].trig  = trig[x];
        end
        assign trig[0] = ready[0] && gpu.d_trig;
        for (x = 1; x < fpus; x = x + 1) begin
            assign trig[x] = ready[x] && ready[x-1:0] == 0 && gpu.d_trig;
        end
    endgenerate
    
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
            wait_reg <= trig;
        end
    end
    
    // Pipeline timing logic.
    logic[fpus-1:0] plr[gpu.latency+2];
    generate
        assign plr[0] = plr[1];
        assign plr[gpu.latency+1] = trig;
        for (x = 0; x < gpu.latency - 1; x = x + 1) begin
            always @(posedge clk) begin
                plr[x+1] <= plr[x+2];
            end
        end
        if (gpu.latency) begin
            always @(posedge clk) begin
                if (!waiting) begin
                    plr[gpu.latency] <= plr[gpu.latency + 1];
                end
            end
        end
    endgenerate
    
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
