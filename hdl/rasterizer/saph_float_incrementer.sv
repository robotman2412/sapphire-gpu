
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Multi-float incrementing unit.
module saph_float_incrementer#(
    // Number of floats to compute.
    parameter   integer width   = 2,
    // Number of floating-point adders to use.
    parameter   integer adders  = 1,
    // Number of cycles required for addition.
    localparam  integer delay   = width / adders
)(
    // Core clock.
    input  logic    clk,
    // Synchronous reset.
    input  logic    rst,
    
    // Latch init values.
    input  logic    latch,
    // Enable increment.
    // Overriden by latch.
    input  logic    count,
    // Incremented value is ready after posedge clk.
    output logic    ready,
    
    // Initial state.
    input  float    init[width],
    // Increment values.
    input  float    inc[width],
    // Current state.
    output float    cur[width]
);
    genvar x;
    
    // Increment buffer.
    float inc_reg[width];
    // Addition result.
    float res[adders];
    
    generate
        // For multicycle: Currently busy counting.
        logic counting;
        // For multicycle: Current bank being counted on.
        logic[$clog2(delay)-1:0] bank;
        
        // Increment latching logic.
        for (x = 0; x < width; x = x + 1) begin
            always @(posedge clk) begin
                if (latch) begin
                    inc_reg[x] <= inc[x];
                end
            end
        end
        
        if (delay > 1) begin: d1
            // Counting multicycle.
            assign  ready   = counting && bank == delay - 1;
            
            // Counting state machine.
            always @(posedge clk) begin
                if (rst || latch) begin
                    // Reset.
                    counting    <= 0;
                    bank        <= 0;
                end else if (counting && bank == delay - 1) begin
                    // Last cycle of counting.
                    counting    <= 0;
                    bank        <= 0;
                end else if (counting) begin
                    // Busy counting.
                    bank        <= bank + 1;
                end else if (count) begin
                    // Start counting.
                    counting    <= 1;
                    bank        <= bank + 1;
                end
            end
            
            // Latching logic.
            for (x = 0; x < width; x = x + 1) begin
                always @(posedge clk) begin
                    if (rst) begin
                        // Reset.
                        cur[x] <= 'bx;
                    end else if (latch) begin
                        // Latch initial state.
                        cur[x] <= init[x];
                    end else if (count && bank == 0 && x / adders == 0) begin
                        // Count (first bank).
                        cur[x] <= res[x % adders];
                    end else if (counting && bank == x / adders && x / adders >= 1) begin
                        // Count (second and later banks).
                        cur[x] <= res[x % adders];
                    end
                end
            end
            
        end else begin: d2
            // Adding all at once.
            assign  bank        = 0;
            assign  counting    = 0;
            assign  ready       = 1;
            
            // Latching logic.
            for (x = 0; x < adders; x = x + 1) begin
                always @(posedge clk) begin
                    if (rst) begin
                        // Reset.
                        cur[x] <= 'bx;
                    end else if (count) begin
                        // Count.
                        cur[x] <= res[x];
                    end
                end
            end
        end
        
        // Adder array.
        for (x = 0; x < adders; x = x + 1) begin
            svfloat_add#(float) fadd(clk, inc_reg[x+bank*adders], cur[x+bank*adders], res[x]);
        end
    endgenerate
endmodule
