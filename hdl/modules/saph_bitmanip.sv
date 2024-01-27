
// Copyright © 2024, Julian Scheffers, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Variable number bit expander.
module saph_num_exp#(
    // Maximum width of the packed number, 2+.
    parameter   pack_width      = 2,
    // Width of the unpacked number, 2+.
    parameter   unpack_width    = 8,
    // Number of bits required to represent a range in the packed data.
    localparam  pack_exp        = $clog2(pack_width+1),
    // Number of bits required to represent a range in the unpacked data.
    localparam  unpack_exp      = $clog2(unpack_width)
)(
    // Packed data.
    input  logic[pack_width-1:0]    in,
    // Packed width.
    input  logic[pack_exp-1:0]      in_width,
    // Unpacked number out.
    output logic[unpack_width-1:0]  out
);
    genvar x;
    integer idx[unpack_width];
    generate
        for (x = 0; x < unpack_width; x = x + 1) begin
            assign idx[unpack_width-1-x] = pack_width-1-x%in_width;
            assign out[unpack_width-1-x] = in[pack_width-1-x%in_width];
        end
    endgenerate
endmodule

// Unpack a fixed-size number from a variable bit range.
module saph_num_unpack#(
    // Width of the packed bit field, 2+.
    parameter   pack_width      = 8,
    // Width of the number, 2+.
    parameter   unpack_width    = 8,
    // Number of bits required to represent a range in the packed data.
    localparam  pack_exp        = $clog2(pack_width),
    // Number of bits required to represent a range in the unpacked data.
    localparam  unpack_exp      = $clog2(unpack_width+1)
)(
    // Packed data.
    input  logic[pack_width-1:0]    in,
    // Packed bit position.
    input  logic[pack_exp-1:0]      in_pos,
    // Packed bit width.
    input  logic[unpack_exp-1:0]    in_width,
    // Unpacked number out.
    output logic[unpack_width-1:0]  out
);
    // Bit reduction of value.
    wire [pack_width-1:0] tmp = in >> in_pos;
    // Placement in the unpacked value.
    saph_num_exp#(pack_width, unpack_width) exp(tmp << (pack_width - in_width), in_width, out);
endmodule

// Pack a fixed-size number into a variable bit range.
module saph_num_pack#(
    // Width of the packed bit field, 2+.
    parameter   pack_width      = 8,
    // Width of the number, 2+.
    parameter   unpack_width    = 8,
    // Number of bits required to represent a range in the packed data.
    localparam  pack_exp        = $clog2(pack_width),
    // Number of bits required to represent a range in the unpacked data.
    localparam  unpack_exp      = $clog2(unpack_width+1)
)(
    // Unpacked data.
    input  logic[unpack_width-1:0]  in,
    // Packed bit position.
    input  logic[pack_exp-1:0]      out_pos,
    // Packed bit width.
    input  logic[unpack_exp-1:0]    out_width,
    // Unpacked number out.
    output logic[pack_width-1:0]    out
);
    // Bit reduction of value.
    wire [pack_width-1:0] tmp = in >> (unpack_width - out_width);
    // Placement in the packed value.
    assign out = tmp << out_pos;
endmodule

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

// Color unpacker.
module saph_col_unpack(
    // Packed color.
    input  logic[31:0]  in,
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
