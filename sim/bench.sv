
// Copyright © 2024, Julian Scheffers and Jesús Sanz del Rey, see LICENSE for more information

`timescale 1ns/1ps



module bench;
    reg clk = 0;
    top top(clk);
    initial begin
        integer i;
        $dumpfile("xsim.dir/sim.vcd");
        $dumpvars(0, top);
        for (i = 0; i < 1000; i = i + 1) begin
            #50 clk = !clk;
        end
        $finish;
    end
endmodule