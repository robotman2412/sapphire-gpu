
MAKEFLAGS += --silent --no-print-directory

.PHONY: all build clean run wave

HDL   = ../bench.sv \
		../../sv-float/packages/svfloat.sv \
		../../hdl/packages/saph_types.sv \
		$(shell find ../../sv-float/hdl -name '*.sv') \
		$(shell find ../../hdl -name '*.sv' -not -path '../../hdl/packages/*') \
		$(shell find hdl -name '*.sv')

all: wave

build:
	echo "Compiling"
	out=`xvlog -nolog -i ../../hdl/include -sv $(HDL)` || echo "$$out"
	echo "Elaborating"
	out=`xelab -nolog --incr -top bench -snapshot sim` || echo "$$out"
	rm -f *.pb

run: build
	echo "Running"
	out=`xsim -nolog sim -t ../sim.tcl` || echo "$$out"
	rm -f *.jou *.wdb

wave: run
	gtkwave xsim.dir/sim.vcd > /dev/null

clean:
	rm -rf xsim.dir
