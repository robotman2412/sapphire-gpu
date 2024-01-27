
// Copyright © 2024, Julian Scheffers, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Color channel multiplier.
module saph_ch_mul(
    input  logic[7:0] a,
    input  logic[7:0] b,
    output logic[7:0] q
);
    wire[15:0] tmp = a * (b + b[7]);
    assign q = tmp[15:8];
endmodule

// Color channel interpolator.
module saph_ch_interp(
    input  logic[7:0] from,
    input  logic[7:0] to,
    input  logic[7:0] coeff,
    output logic[7:0] q
);
    wire[15:0] tmp = from * 256 + (to - from) * (coeff + coeff[7]);
    assign q = tmp[15:8];
endmodule

// Color math unit.
module saph_colmath(
    // Color A.
    input  color        a,
    // Color B.
    input  color        b,
    // Interpolation factor.
    input  logic[7:0]   c,
    // Color math mode.
    input  logic[1:0]   mode,
    // Resulting color.
    output color        q
);
    // Decode mode.
    wire        tint    = mode == `SAPH_COLMATH_TINT;
    wire        overlay = mode == `SAPH_COLMATH_OVERLAY;
    color       from, to, coeff;
    always @(*) begin
        if (mode == `SAPH_COLMATH_INTERP) begin
            from    = a;
            to      = b;
            coeff   = '{c,   c,   c,   c  };
        end else if (mode == `SAPH_COLMATH_OVERLAY) begin
            from    = a;
            to      = '{255, b.r, b.g, b.b};
            coeff   = '{b.a, b.a, b.a, b.a};
        end else if (mode == `SAPH_COLMATH_TINT) begin
            from    = 0;
            to      = a;
            coeff   = '{b.a, b.r, b.g, b.b};
        end else begin
            from    = 32'bx;
            to      = 32'bx;
            coeff   = 32'bx;
        end
    end
    
    // Channel interpolators.
    saph_ch_interp int_a(from.a, to.a, coeff.a, q.a);
    saph_ch_interp int_r(from.r, to.r, coeff.r, q.r);
    saph_ch_interp int_g(from.g, to.g, coeff.g, q.g);
    saph_ch_interp int_b(from.b, to.b, coeff.b, q.b);
endmodule
