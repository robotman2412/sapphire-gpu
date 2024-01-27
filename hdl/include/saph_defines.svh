
// Copyright © 2024, Julian Scheffers, see LICENSE for more information

`timescale 1ns/1ps

/* verilator lint_off IMPORTSTAR */
import saph_types::*;
/* verilator lint_on IMPORTSTAR */

// Pixel type: ARGB.
// All channel formats are used.
`define SAPH_PIXTYPE_ARGB 4'b00
// Pixel type: RGB.
// Channel format A is ignored.
`define SAPH_PIXTYPE_RGB  4'b01
// Pixel type: greyscale.
// Channel format B is re-used for greyscale.
`define SAPH_PIXTYPE_GREY 4'b10
// Pixel type: palette.
// Color is passed through entirely unmodified.
`define SAPH_PIXTYPE_PAL  4'b11

// Color math: Interpolate between A and B by C.
`define SAPH_COLMATH_INTERP  2'b00
// Color math: Channel-wise multiply of A and B.
`define SAPH_COLMATH_TINT    2'b01
// Color math: Overlay color B over A by alpha.
`define SAPH_COLMATH_OVERLAY 2'b10
