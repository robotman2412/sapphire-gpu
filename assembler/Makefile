CC=gcc
COMMON_CFLAGS=-Wall -Wno-switch -Wno-format -Wno-unused-function
CFLAGS=-g $(COMMON_CFLAGS)
LD=gcc
LFLAGS=-g

OBJ_DIR := build

as_SRC = sapphire-as.c gpu.c
debugger_SRC = sapphire-debugger.c gpu.c

as_OBJ := $(addprefix $(OBJ_DIR)/,$(addsuffix .c.o,$(notdir $(basename $(as_SRC)))))
debugger_OBJ := $(addprefix $(OBJ_DIR)/,$(addsuffix .c.o,$(notdir $(basename $(debugger_SRC)))))

all: sapphire-as sapphire-debugger

$(OBJ_DIR)/%.c.o: %.c
	@mkdir -p $(OBJ_DIR)
	@$(CC) $(CFLAGS) -c -o $@ $<

sapphire-as: $(as_OBJ)
	@$(LD) $(LFLAGS) -o sapphire-as $(as_OBJ)

sapphire-debugger: $(debugger_OBJ)
	@$(LD) $(LFLAGS) -o sapphire-debugger $(debugger_OBJ)

clean:
	@rm -f $(OBJ_DIR)/*.o sapphire-as sapphire-debugger

clean-obj:
	@rm -f $(OBJ_DIR)/*.o
