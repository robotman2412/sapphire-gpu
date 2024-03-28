
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



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
    input  wire [pack_width-1:0]    in,
    // Packed bit position.
    input  wire [pack_exp-1:0]      in_pos,
    // Packed bit width.
    input  wire [unpack_exp-1:0]    in_width,
    // Unpacked number out.
    output logic[unpack_width-1:0]  out
);
    // Bit reduction of value.
    wire [pack_width-1:0] tmp = in >> in_pos;
    // Placement in the unpacked value.
    saph_num_exp#(pack_width, unpack_width) exp(tmp << (pack_width - in_width), in_width, out);
endmodule
