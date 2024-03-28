
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

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
    input  wire [pack_width-1:0]    in,
    // Packed width.
    input  wire [pack_exp-1:0]      in_width,
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
