
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Sapphire GPU VGA/RGB output module.
module saph_vga_fsm#(
    // Number of bits for the coordinate.
    parameter   integer width     = 9
)(
    // VGA core clock.
    input  logic            clk,
    // Synchronous reset.
    input  logic            rst,
    // Count enable.
    input  logic            inc,
    
    // Front porch width minus one.
    input  logic[width-1:0] fp_width,
    // Video width minus one.
    input  logic[width-1:0] vid_width,
    // Back porch width minus one.
    input  logic[width-1:0] bp_width,
    // Sync width minus one.
    input  logic[width-1:0] sync_width,
    
    // Current counter value.
    output logic[width-1:0] count,
    // Video enable.
    output logic            vid_en,
    // Sync enable.
    output logic            sync_en,
    // FSM will wrap to front porch on next posedge clk.
    output logic            cout
);
    // Current FSM state; front porch, video, back porch, sync respectively.
    logic[1:0]  state;
    
    // Whether to increment state next posedge clk.
    logic       state_inc;
    always @(*) begin
        case (state)
            0: state_inc = count == fp_width;
            1: state_inc = count == vid_width;
            2: state_inc = count == bp_width;
            3: state_inc = count == sync_width;
        endcase
    end
    
    // Output logic.
    assign cout     = state_inc && state == 3;
    assign vid_en   = state == 1;
    assign sync_en  = state == 3;
    
    // Counting logic.
    always @(posedge clk) begin
        if (rst) begin
            // Reset.
            state   <= 0;
            count   <= 0;
            
        end else if (inc && state_inc) begin
            // Increment state machine.
            count   <= 0;
            state   <= state + 1;
            
        end else if (inc) begin
            // Count.
            count   <= count + 1;
        end
    end
endmodule
