CC=gcc
COMMON_CFLAGS=-Wall -Wno-switch -Wno-format -Wno-unused-function
CFLAGS=-g $(COMMON_CFLAGS)
LD=gcc
LFLAGS=-g

OBJ_DIR := build

as_SRC=sapphire-as.c gpu.c
as_OBJ := $(addprefix $(OBJ_DIR)/,$(addsuffix .c.o,$(notdir $(basename $(as_SRC)))))

all: sapphire-as

$(OBJ_DIR)/%.c.o: %.c
	@mkdir -p $(OBJ_DIR)
	@$(CC) $(CFLAGS) -c -o $@ $<

sapphire-as: $(as_OBJ)
	@$(LD) $(LFLAGS) -o sapphire-as $(as_OBJ)

clean:
	@rm -f *.o as

clean-obj:
	@rm -f *.o
