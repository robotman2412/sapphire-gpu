
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps



package saph_types;
// Import float types.
`ifdef usef64
typedef svfloat::float64 float;
`else
typedef svfloat::float32 float;
`endif
// Defining a function doesn't work, so I used a wrapper.
`define fconst(val) svfloat::ffunc#(float)::from_real(val)

// FPU mode: Add.
`define SAPH_FPU_ADD 2'b00
// FPU mode: Subtract.
`define SAPH_FPU_SUB 2'b01
// FPU mode: Multiply.
`define SAPH_FPU_MUL 2'b10
// FPU mode: Divide.
`define SAPH_FPU_DIV 2'b11

// Rasterizer shape type: Line.
`define SAPH_SHAPE_LINE 2'b00
// Rasterizer shape type: Triangle.
`define SAPH_SHAPE_TRI  2'b01
// Rasterizer shape type: Rectangle.
`define SAPH_SHAPE_RECT 2'b10

// Pixel position.
typedef bit signed[15:0] pixpos;

// 24-bit ARGB color.
typedef struct packed {
    bit[7:0] a;
    bit[7:0] r;
    bit[7:0] g;
    bit[7:0] b;
} color;

// Pixel channel format.
typedef struct packed {
    // Bit position.
    bit[4:0] pos;
    // Bit width minus 1.
    bit[2:0] width;
} chfmt;

// Pixel format descriptor.
typedef struct packed {
    // Type category, one of SAPH_PIXTYPE_*.
    bit[3:0] cat;
    // Bit size of total type minus 1.
    bit[4:0] size;
    // Format of alpha channel.
    chfmt a;
    // Format of red channel.
    chfmt r;
    // Format of green channel.
    chfmt g;
    // Format of blue channel.
    chfmt b;
} pixfmt;

// Rasterizer vertex information.
typedef struct packed {
    // Spatial position.
    float x, y, z;
    // Texture position.
    float u, v;
    // Vertex color.
    color vcol;
} vertex;

// Rasterized pixel information.
typedef struct packed {
    // On-screen position.
    pixpos x, y;
    // Depth.
    float z;
    // Texture position.
    float u, v;
    // Tint color.
    color col;
} pixel;
endpackage
