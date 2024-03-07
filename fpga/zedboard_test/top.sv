
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



module top(
    input  logic        sysclk,
    
    input  logic        btnc,
    input  logic        sw0,
    
    output logic[3:0]   vga_r,
    output logic[3:0]   vga_g,
    output logic[3:0]   vga_b,
    output logic        vga_hsync,
    output logic        vga_vsync
);
    saph_pixreadport pix_port();
    assign pix_port.d_ready = 1;
    assign pix_port.q_res   = pix_port.d_trig ? {8'hff, pix_port.d_x[7:0], pix_port.d_y[7:0], 8'h00} : 0;
    
    logic clk;
    param_pll#(100000000, 8, 2) pll(sysclk, clk);
    
    saph_vidport_vga#(4, 4, 4) vga_port();
    assign vga_r        = vga_port.r;
    assign vga_g        = vga_port.g;
    assign vga_b        = vga_port.b;
    assign vga_hsync    = vga_port.hsync;
    assign vga_vsync    = vga_port.vsync;
    
    saph_vidgen_vga vga_gen(
        clk, btnc, sw0,
        9,
        39, 799, 87, 127,
        0, 599, 22, 3,
        pix_port, vga_port
    );
endmodule
