
# Copyright © 2024, Julian Scheffers, see LICENSE for info

.PHONY: all build vhdl sim wave clean

MAKEFLAGS += --silent

SIM  = $(shell cd hdl/sapphire; find sim -name '*.scala' | sed 's|\.scala$$||')
WAVE = $(shell cd hdl/sapphire; find sim -name '*.scala' | sed 's|\.scala$$|.wave|')
HDL  = $(shell find hdl -name '*.scala')

.PHONY: $(SIM)

all:
	echo '$(SIM)'
	echo '$(WAVE)'

build:
	sbt compile

$(SIM):
	sbt "runMain sapphire.sim.$(@F)"

$(WAVE):
	sbt "runMain sapphire.sim.$(shell echo '$(@F)' | sed 's|.wave$$||g')"
	gtkwave "simWorkspace/$(shell echo '$(@F)' | sed 's|.wave$$||g')$$/wave.fst"

clean:
	rm -rf tmp target simWorkspace project gen
