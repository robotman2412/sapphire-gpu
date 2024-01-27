
// Copyright © 2024, Julian Scheffers, see LICENSE for more information

`timescale 1ns/1ps



package saph_types;
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
endpackage
