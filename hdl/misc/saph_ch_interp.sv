
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Color channel interpolator.
module saph_ch_interp(
    input  wire [7:0] from,
    input  wire [7:0] to,
    input  wire [7:0] coeff,
    output logic[7:0] q
);
    wire[15:0] tmp = from * 256 + (to - from) * (coeff + coeff[7]);
    assign q = tmp[15:8];
endmodule
