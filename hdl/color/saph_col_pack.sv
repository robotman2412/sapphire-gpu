
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Color packer.
module saph_col_pack(
    // Unpacked color.
    input  color        in,
    // Color format.
    input  pixfmt       format,
    // Packed color.
    output logic[31:0]  out
);
    // Channel packers.
    logic[31:0] out_a;
    saph_num_pack#(32, 8) pack_a(in.a, format.a.pos, format.a.width+1, out_a);
    logic[31:0] out_r;
    saph_num_pack#(32, 8) pack_r(in.r, format.r.pos, format.r.width+1, out_r);
    logic[31:0] out_g;
    saph_num_pack#(32, 8) pack_g(in.g, format.g.pos, format.g.width+1, out_g);
    logic[31:0] out_b;
    saph_num_pack#(32, 8) pack_b(in.b, format.b.pos, format.b.width+1, out_b);
    
    // Output mux.
    always @(*) begin
        if (format.cat == `SAPH_PIXTYPE_ARGB) begin
            // RGB with alpha.
            out = out_a | out_r | out_g | out_b;
        end else if (format.cat == `SAPH_PIXTYPE_RGB) begin
            // RGB without alpha.
            out = out_r | out_g | out_b;
        end else if (format.cat == `SAPH_PIXTYPE_GREY) begin
            // Greyscale stored in blue channel.
            out = out_b;
        end else if (format.cat == `SAPH_PIXTYPE_PAL) begin
            // Unmodified palette.
            out = in;
        end else begin
            // Invalid format.
            out = 0;
        end
    end
endmodule
