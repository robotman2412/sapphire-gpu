
MAKEFLAGS += --silent --no-print-directory

.PHONY: all build clean run wave

HDL   = ../../hdl/packages/saph_types.sv \
		$(shell find ../../hdl -name '*.sv' -not -path '../../hdl/packages/*') \
		$(shell find hdl -name '*.sv')

all: wave

build: $(HDL)
	mkdir -p obj_dir
	verilator -Wall -Wno-fatal -Werror-PINNOCONNECT -Werror-IMPLICIT -Wno-DECLFILENAME -Wno-VARHIDDEN -Wno-WIDTH -Wno-UNUSED \
		--trace --trace-fst --trace-depth 20 --trace-max-array 256 --trace-max-width 128 \
		-sv --cc --exe --build \
		-I../../hdl/include \
		--top-module top \
		-j $(shell nproc) bench.cpp $(HDL) -o sim

clean:
	rm -rf obj_dir

run: build
	./obj_dir/sim

wave: run
	gtkwave obj_dir/sim.fst
