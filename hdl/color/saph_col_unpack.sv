
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Color unpacker.
module saph_col_unpack(
    // Packed color.
    input  wire [31:0]  in,
    // Color format.
    input  pixfmt       format,
    // Unpacked color.
    output color        out
);
    // Channel unpackers.
    logic[7:0] out_a;
    saph_num_unpack#(32, 8) unpack_a(in, format.a.pos, format.a.width+1, out_a);
    logic[7:0] out_r;
    saph_num_unpack#(32, 8) unpack_r(in, format.r.pos, format.r.width+1, out_r);
    logic[7:0] out_g;
    saph_num_unpack#(32, 8) unpack_g(in, format.g.pos, format.g.width+1, out_g);
    logic[7:0] out_b;
    saph_num_unpack#(32, 8) unpack_b(in, format.b.pos, format.b.width+1, out_b);
    
    // Output mux.
    always @(*) begin
        if (format.cat == `SAPH_PIXTYPE_ARGB) begin
            // RGB with alpha.
            out = '{out_a, out_r, out_g, out_b};
        end else if (format.cat == `SAPH_PIXTYPE_RGB) begin
            // RGB without alpha.
            out = '{255, out_r, out_g, out_b};
        end else if (format.cat == `SAPH_PIXTYPE_GREY) begin
            // Greyscale stored in blue channel.
            out = '{255, 0, 0, out_b};
        end else if (format.cat == `SAPH_PIXTYPE_PAL) begin
            // Unmodified palette.
            out = in;
        end else begin
            // Invalid format.
            out = 0;
        end
    end
endmodule
