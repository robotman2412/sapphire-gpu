
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



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
    input  wire [unpack_width-1:0]  in,
    // Packed bit position.
    input  wire [pack_exp-1:0]      out_pos,
    // Packed bit width.
    input  wire [unpack_exp-1:0]    out_width,
    // Unpacked number out.
    output logic[pack_width-1:0]    out
);
    // Bit reduction of value.
    wire [pack_width-1:0] tmp = in >> (unpack_width - out_width);
    // Placement in the packed value.
    assign out = tmp << out_pos;
endmodule
