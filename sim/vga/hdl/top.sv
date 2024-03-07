
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



module top(
    input logic clk
);
    saph_pixreadport pix_port();
    assign pix_port.d_ready = 1;
    assign pix_port.q_res   = pix_port.d_trig ? {8'hff, pix_port.d_x, pix_port.d_y, 8'h00} : 0;
    
    saph_vidport_vga vga_port();
    saph_vidgen_vga vga_gen(
        clk, 0, 1,
        0,
        39, 799, 87, 127,
        0, 599, 22, 3,
        pix_port, vga_port
    );
endmodule
