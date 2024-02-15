
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Floating-point unit interface.
interface saph_fpi#(
    // Latency from d_trig to result valid.
    parameter   integer latency = 0
);
    // GPU -> FPU: Trigger FPU computation.
    logic       d_trig;
    // GPU -> FPU: FPU operands.
    float       d_lhs, d_rhs;
    // GPU -> FPU: FPU mode.
    logic[1:0]  d_mode;
    // FPU -> GPU: Ready to perform computation.
    // Must always be 0 if a computation has been triggered and can't be released yet.
    logic       d_ready;
    
    // FPU -> GPU: Computation result ready.
    logic       q_trig;
    // FPU -> GPU: Computation result.
    float       q_res;
    
    // Signals from GPU perspective.
    modport GPU (output d_trig, d_lhs, d_rhs, d_mode, input  d_ready, q_res);
    // Signals from FPU perspective.
    modport FPU (input  d_trig, d_lhs, d_rhs, d_mode, output d_ready, q_res);
endinterface

