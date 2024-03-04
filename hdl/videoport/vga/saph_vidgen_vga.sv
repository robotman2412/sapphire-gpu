
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Sapphire GPU VGA/RGB output module.
module saph_vidgen_vga#(
    // Number of bits for the clock divider.
    parameter   integer div_width   = 6,
    // Number of bits for the X coordinate.
    parameter   integer x_width     = 9,
    // Number of bits for the Y coordinate.
    parameter   integer y_width     = 9,
    // Memory latency in VGA core clock cycles.
    parameter   integer mem_latency = 2
)(
    // VGA core clock.
    input  logic                clk,
    // Synchronous reset.
    input  logic                rst,
    
    // VGA clock divider.
    input  logic[div_width-1:0] vga_clk_div,
    
    // Horizontal front porch width.
    input  logic[x_width-1:0]   h_fp_width,
    // Horizontal video width.
    input  logic[x_width-1:0]   h_vid_width,
    // Horizontal back porch width.
    input  logic[x_width-1:0]   h_bp_width,
    // Horizontal sync width.
    input  logic[x_width-1:0]   h_sync_width,
    
    // Vertical front porch width.
    input  logic[y_width-1:0]   v_fp_width,
    // Vertical video width.
    input  logic[y_width-1:0]   v_vid_width,
    // Vertical back porch width.
    input  logic[y_width-1:0]   v_bp_width,
    // Vertical sync width.
    input  logic[y_width-1:0]   v_sync_width,
    
    // VGA output port.
    saph_vidport_vga.GPU        port
);
    // Horizontal state machine.
    logic[1:0]  h_state;
    // Vertical state machine.
    logic[1:0]  v_state;
endmodule
