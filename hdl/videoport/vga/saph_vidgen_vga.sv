
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Sapphire GPU VGA/RGB output module.
module saph_vidgen_vga#(
    // Number of bits for the clock divider.
    parameter   integer div_width   = 6,
    // Number of bits for the X coordinate.
    parameter   integer x_width     = 10,
    // Number of bits for the Y coordinate.
    parameter   integer y_width     = 10
)(
    // VGA core clock.
    input  wire                 clk,
    // Synchronous reset.
    input  wire                 rst,
    // VGA output enable.
    input  wire                 en,
    
    // VGA clock divider minus one.
    input  wire [div_width-1:0] vga_clk_div,
    
    // Horizontal front porch width minus one.
    input  wire [x_width-1:0]   h_fp_width,
    // Horizontal video width minus one.
    input  wire [x_width-1:0]   h_vid_width,
    // Horizontal back porch width minus one.
    input  wire [x_width-1:0]   h_bp_width,
    // Horizontal sync width minus one.
    input  wire [x_width-1:0]   h_sync_width,
    
    // Vertical front porch width minus one.
    input  wire [y_width-1:0]   v_fp_width,
    // Vertical video width minus one.
    input  wire [y_width-1:0]   v_vid_width,
    // Vertical back porch width minus one.
    input  wire [y_width-1:0]   v_bp_width,
    // Vertical sync width minus one.
    input  wire [y_width-1:0]   v_sync_width,
    
    // Pixel lookup port.
    saph_pixreadport.GPU        pix,
    // VGA output port.
    saph_vidport_vga.GPU        vga
);
    /* ==== Enable logic ==== */
    // Pixel to sync enable delay.
    logic pix_fsm_en, sync_fsm_en;
    logic[pix.latency:0] fsm_delay;
    assign pix_fsm_en   = en;
    assign fsm_delay[0] = pix_fsm_en;
    assign sync_fsm_en  = fsm_delay[pix.latency];
    generate
        if (pix.latency) begin: pix_l1
            always @(posedge clk) begin
                if (rst) begin
                    fsm_delay[pix.latency:1] <= 0;
                end else begin
                    fsm_delay[pix.latency:1] <= fsm_delay[pix.latency-1:0];
                end
            end
        end
    endgenerate
    
    // Increment signal generation.
    logic[div_width-1:0] pix_fsm_div, sync_fsm_div;
    wire    pix_fsm_inc     = pix_fsm_en && pix_fsm_div == 0;
    wire    sync_fsm_inc    = sync_fsm_en && sync_fsm_div == 0;
    always @(posedge clk) begin
        // Pixel clock divider.
        if (pix_fsm_en && pix_fsm_div == 0) begin
            pix_fsm_div <= vga_clk_div;
        end else if (pix_fsm_en) begin
            pix_fsm_div <= pix_fsm_div - 1;
        end
        // Sync clock divider.
        if (sync_fsm_en && sync_fsm_div == 0) begin
            sync_fsm_div <= vga_clk_div;
        end else if (sync_fsm_en) begin
            sync_fsm_div <= sync_fsm_div - 1;
        end
    end
    
    
    /* ==== VGA finite state machines ==== */
    // Pixel lookup FSM.
    logic[x_width-1:0] pix_x;
    logic pix_h_vid_en, pix_h_sync_en, pix_h_cout;
    saph_vga_fsm#(x_width) pix_fsm_x(
        clk, rst, pix_fsm_inc,
        h_fp_width, h_vid_width, h_bp_width, h_sync_width,
        pix_x, pix_h_vid_en, pix_h_sync_en, pix_h_cout
    );
    logic[y_width-1:0] pix_y;
    logic pix_v_vid_en, pix_v_sync_en, pix_v_cout;
    saph_vga_fsm#(x_width) pix_fsm_y(
        clk, rst, pix_fsm_inc && pix_h_cout,
        v_fp_width, v_vid_width, v_bp_width, v_sync_width,
        pix_y, pix_v_vid_en, pix_v_sync_en, pix_v_cout
    );
    
    // Sync generation FSM.
    logic[x_width-1:0] sync_x;
    logic sync_h_vid_en, sync_h_sync_en, sync_h_cout;
    saph_vga_fsm#(x_width) sync_fsm_x(
        clk, rst, sync_fsm_inc,
        h_fp_width, h_vid_width, h_bp_width, h_sync_width,
        sync_x, sync_h_vid_en, sync_h_sync_en, sync_h_cout
    );
    logic[y_width-1:0] sync_y;
    logic sync_v_vid_en, sync_v_sync_en, sync_v_cout;
    saph_vga_fsm#(x_width) sync_fsm_y(
        clk, rst, sync_fsm_inc && sync_h_cout,
        v_fp_width, v_vid_width, v_bp_width, v_sync_width,
        sync_y, sync_v_vid_en, sync_v_sync_en, sync_v_cout
    );
    
    
    /* ==== Pixel lookup logic ==== */
    assign pix.d_trig = pix_h_vid_en && pix_v_vid_en;
    assign pix.d_x    = pix_x;
    assign pix.d_y    = pix_y;
    
    
    /* ==== VGA signal recombination ==== */
    assign vga.hsync = sync_h_sync_en;
    assign vga.vsync = sync_v_sync_en;
    assign vga.r     = pix.q_res.r >> (8 - vga.r_width);
    assign vga.g     = pix.q_res.g >> (8 - vga.g_width);
    assign vga.b     = pix.q_res.b >> (8 - vga.b_width);
endmodule
