
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps
`include "saph_defines.svh"



// Pipeline register generator.
module saph_plr#(
    // Width of signal.
    parameter   width   = 1,
    // Number of pipeline registers.
    parameter   latency = 1
)(
    // Pipeline clock.
    input  wire             clk,
    // Reset value to 0.
    input  wire             rst,
    
    // Input value.
    input  wire [width-1:0] d,
    // Output value.
    output logic[width-1:0] q
);
    genvar x;
    generate
        if (latency) begin: l1
            reg[width-1:0]    plr[latency];
            initial plr[0] = 0;
            always @(posedge clk) begin
                plr[0] <= rst ? 0 : d;
            end
            for (x = 1; x < latency; x = x + 1) begin
                always @(posedge clk) begin
                    plr[x] <= rst ? 0 : plr[x-1];
                end
            end
            assign q = plr[latency-1];
        end else begin: l0
            assign q = d;
        end
    endgenerate
endmodule
