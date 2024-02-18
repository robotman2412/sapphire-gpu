
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Floating-point computation unit.
module saph_fpu#(
    // Number of ports.
    parameter   integer ports       = 1,
    
    // Number of adders.
    parameter   integer n_add       = 1,
    // Number of multipliers.
    parameter   integer n_mul       = 1,
    // Number of dividers.
    parameter   integer n_div       = 1,
    
    // Enable pipeline register before add/mul/div.
    parameter   bit     plr_pre     = 1,
    // Enable pipeline register after add/mul/div.
    parameter   bit     plr_post    = 1
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // FPU interfaces.
    saph_fpi.FPU    port[ports]
);
    genvar x, y;
    localparam integer  latency = plr_pre + plr_post;
    localparam integer  n_fpu   = n_add + n_mul + n_div;
    
    // FPU interfaces.
    saph_fpi#(latency) grid[ports*n_fpu]();
    saph_fpi#(latency) out[n_fpu]();
    
    // FPU mux instantiaion.
    generate
        for (x = 0; x < n_fpu; x = x + 1) begin
            saph_fpi#(latency) col[ports]();
            for (y = 0; y < ports; y = y + 1) begin
                saph_fpi_conn conn(grid[x+n_fpu*y], col[y]);
            end
            saph_fpu_mux#(ports) mux(clk, rst, col, out[x]);
        end
        for (y = 0; y < ports; y = y + 1) begin
            saph_fpi#(latency) row[n_fpu]();
            for (x = 0; x < n_fpu; x = x + 1) begin
                saph_fpi_conn conn(row[x], grid[x+n_fpu*y]);
            end
            saph_fpu_demux#(n_fpu) dmx(clk, rst, port[y], row);
        end
    endgenerate
    
    // FPU instantiation.
    generate
        for (x = 0; x < n_add; x = x + 1) begin
            saph_fpu_single#(1, 0, 0, plr_pre, plr_post) add_inst(clk, rst, out[x]);
        end
        for (x = 0; x < n_mul; x = x + 1) begin
            saph_fpu_single#(0, 1, 0, plr_pre, plr_post) mul_inst(clk, rst, out[x+n_add]);
        end
        for (x = 0; x < n_div; x = x + 1) begin
            saph_fpu_single#(0, 0, 1, plr_pre, plr_post) div_inst(clk, rst, out[x+n_add+n_mul]);
        end
    endgenerate
endmodule

