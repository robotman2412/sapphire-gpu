
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Floating-point unit interface connector.
module saph_fpi_conn(
    saph_fpi.FPU gpu,
    saph_fpi.GPU fpu
);
    if (gpu.latency != fpu.latency) begin
        $error("FPU latency mismatch: GPU expects %d, FPU expects %d", gpu.latency, fpu.latency);
    end
    
    assign fpu.d_trig       = gpu.d_trig;
    assign fpu.d_lhs        = gpu.d_lhs;
    assign fpu.d_rhs        = gpu.d_rhs;
    assign fpu.d_mode       = gpu.d_mode;

    assign gpu.d_ready      = fpu.d_ready;
    assign gpu.q_trig       = fpu.q_trig;
    assign gpu.q_res        = fpu.q_res;
    assign gpu.has_modes    = fpu.has_modes;
endmodule
