#include "gpu.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef enum {
    disabled = 0,
    pc_equal,
    reg_value_equal,
    reg_value_lt,
    reg_value_le,
    reg_value_gt,
    reg_value_ge,
    mem_value_equal,
    mem_value_lt,
    mem_value_le,
    mem_value_gt,
    mem_value_ge
} breakpoint_enum_t;
typedef struct  {
    breakpoint_enum_t breakpoint_type;
    uint32_t value;
    uint32_t address;
} breakpoint_t;

#define NUMBER_OF_BREAKPOINTS   8

static int tokenize(char *cmd, char **tokens, int token_count) {
    int token = 0;
    for(int i = 0; i < token_count; i++)
        tokens[i][0] = '\0';

    int idx = 0;
    while(*cmd) {
        if(*cmd == ' ' || *cmd == ',' || *cmd == '\t' || *cmd == '\n') {
            tokens[token++][idx] = '\0';
            idx = 0;
            while(*cmd && (*cmd == ' ' || *cmd == ',' || *cmd == '\t' || *cmd == '\n'))
                cmd++;

            if(!(*cmd)) break;
            if(token >= token_count) return token;
        }

        tokens[token][idx++] = *cmd++;
    }

    if(idx > 0)
        tokens[token][idx] = '\0';
    return token + 1;
}

void usage_and_exit() {
    printf("usage:\n"
        "\t-b <file>   binary image file\n"
        "\t-h          this message\n");
    exit(-__LINE__);
}

typedef struct {
    gpu_context_t ctx;
    uint32_t *code;
    uint8_t *memory;
    breakpoint_t breakpoints[NUMBER_OF_BREAKPOINTS];
} debugger_context_t;

typedef struct {
    char    *cmd;
    char    *short_cmd;
    void    (*func)(debugger_context_t *, char **tokens);
    char    *help;
} command_t;

extern command_t commands[];

void cmd_print_help(debugger_context_t *ctx, char **tokens) {
    int idx = 0;
    while(commands[idx].cmd) {
        printf("%s(%s)\t%s\n", commands[idx].cmd, commands[idx].short_cmd, commands[idx].help);
        idx++;
    }
}

extern bool running;
void cmd_quit(debugger_context_t *ctx, char **tokens) {
    running = false;
}

void cmd_print_info(debugger_context_t *ctx, char **tokens) {
    printf("Registers:\n");
    for(int i = 0; i < 16; i++) {
        printf("\tr%d%s: %08X\n", i, (i < 10) ? " " : "", ctx->ctx.regs[i]);
    }
    printf("\tPC : %08X\n", ctx->ctx.PC);
}

void cmd_step(debugger_context_t *ctx, char **tokens) {
    char s[MAX_LINE_LENGTH];

    if(!disasm_instruction(ctx->code[ctx->ctx.PC], ctx->ctx.PC, s)) {
        printf("Invalid instruction 0x%08X at PC 0x%08X\n", ctx->code[ctx->ctx.PC], ctx->ctx.PC);
        return;
    }
    printf("0x%08X:    %s\n", ctx->ctx.PC, s);

    uint32_t queue_data[8];
    int queue;

    single_step_result_t result = single_step(&ctx->ctx, ctx->code, ctx->memory, queue_data, &queue);
    switch(result) {
        case at_end:
            printf("END reached\n");
            break;
        case normal_execution:
            break;
        case queue_write:
            printf("Queue write: %d\n", queue);
            for (int i = 0; i < 8; i++)
                printf("\t%d: %08X\n", i, queue_data[i]);
            break;
        case invalid_address:
            printf("ERROR: attempt to read/write invalid memory address\n");
            break;
    }
}

void cmd_disasm(debugger_context_t *ctx, char **tokens) {
    uint32_t addr;
    if(strlen(tokens[1]) >= 1)
        addr = strtoul(tokens[1], NULL, 0);
    else if(ctx->ctx.PC >= 8)
        addr = ctx->ctx.PC - 8;

    char s[MAX_LINE_LENGTH];
    for(int i = 0; i < 16; i++) {
        if(addr >= (0x10000 / 2))
            return;
        if(!disasm_instruction(ctx->code[addr], addr, s))
            return;
        if(addr == ctx->ctx.PC) printf("* ");
        else printf("  ");
        printf("0x%08X:    %s\n", addr++, s);
    }
}

void cmd_setr(debugger_context_t *ctx, char **tokens) {
    uint32_t v = strtoul(tokens[2], NULL, 0);
    if(!strcmp(tokens[1], "pc"))
        ctx->ctx.PC = v;
    else if(tokens[1][0] == 'r' || tokens[1][0] == 'R') {
        uint32_t r = strtoul(&tokens[1][1], NULL, 0);
        if(r >= 16)
            printf("Invalid register to set: %d", r);
        else
            ctx->ctx.regs[r] = v;
    } else {
        printf("Unknown register: %s\n", tokens[1]);
    }
}

void cmd_dump_memory(debugger_context_t *ctx, char **tokens) {
    if(strlen(tokens[1]) < 1) {
        printf("usage: dump address [length]\n");
        return;
    }
    uint32_t addr = strtoul(tokens[1], NULL, 0);
    uint32_t len = 16*4;
    if(strlen(tokens[2]) > 0)
        len = strtoul(tokens[2], NULL, 0);

    for(int i = 0; i < len; i++) {
        if(addr >= 0x10000)
            return;
        if(i % 16 == 15)
            printf("%02X\n", ctx->memory[addr]);
        else if(i % 16 == 0)
            printf("0x%08X: %02X ", addr, ctx->memory[addr]);
        else
            printf("%02X ", ctx->memory[addr]);
        addr++;
    }
}

void cmd_set_memory(debugger_context_t *ctx, char **tokens) {
    if(strlen(tokens[2]) < 1) {
        printf("usage: setm address value\n");
        return;
    }

    uint32_t addr = strtoul(tokens[1], NULL, 0);
    uint32_t v = strtoul(tokens[2], NULL, 0);

    if(addr > 0x10000) {
        printf("address: 0x%x exceeds memory size\n");
        return;
    }
    ctx->memory[addr] = v;
}

static void print_breakpoint(breakpoint_t *bp) {
    switch (bp->breakpoint_type) {
        case disabled:
            break;
        case pc_equal:
            printf("pc = 0x%08x", bp->value);
            break;
        case reg_value_equal:
            printf("r%d = 0x%x", bp->address, bp->value);
            break;
        case reg_value_lt:
            printf("r%d < 0x%x", bp->address, bp->value);
            break;
        case reg_value_le:
            printf("r%d <= 0x%x", bp->address, bp->value);
            break;
        case reg_value_gt:
            printf("r%d > 0x%x", bp->address, bp->value);
            break;
        case reg_value_ge:
            printf("r%d >= 0x%x", bp->address, bp->value);
            break;
        case mem_value_equal:
            printf("mem[0x%x] = 0x%x", bp->address, bp->value);
            break;
        case mem_value_lt:
            printf("mem[0x%x] < 0x%x", bp->address, bp->value);
            break;
        case mem_value_le:
            printf("mem[0x%x] <= 0x%x", bp->address, bp->value);
            break;
        case mem_value_gt:
            printf("mem[0x%x] > 0x%x", bp->address, bp->value);
            break;
        case mem_value_ge:
            printf("mem[0x%x] >= 0x%x", bp->address, bp->value);
            break;
    }
}

void cmd_set_breakpoint(debugger_context_t *ctx, char **tokens) {
    if(strlen(tokens[4]) < 1) {
        printf("usage:\n"
            "\tsetbp n pc = value\n"
            "\tsetbp n r# = value (or <, <=, >, >=) -- for register watch point\n"
            "\tsetbp n # = value (or <, <=, >, >=) -- for memory watch point\n"
            "*Make sure to use space between each symbol in the comparison\n");
        return;
    }

#define SPACE_PC    0
#define SPACE_REG   1
#define SPACE_MEM   2
#define CMP_EQUAL   0
#define CMP_LT      1
#define CMP_LE      2
#define CMP_GT      3
#define CMP_GE      4

    uint32_t address;
    int space = SPACE_PC;
    if(!strcmp(tokens[2], "pc")) {
        space = SPACE_PC;
    } else if(tokens[2][0] == 'r' || tokens[2][0] == 'R') {
        space = SPACE_REG;
        address = strtoul(&tokens[2][1], NULL, 0);
        if(address >= 16) {
            printf("invalid register: r%d\n", address);
            return;
        }
    } else {
        space = SPACE_MEM;
        address = strtoul(tokens[2], NULL, 0);
        if(address >= 0x10000) {
            printf("memory address: 0x%08X > memory size\n", address);
            return;
        }
    }

    int compare = CMP_EQUAL;
    if(!strcmp(tokens[3], "="))
        compare = CMP_EQUAL;
    else if(!strcmp(tokens[3], "<"))
        compare = CMP_LT;
    else if(!strcmp(tokens[3], "<="))
        compare = CMP_LE;
    else if(!strcmp(tokens[3], ">"))
        compare = CMP_GT;
    else if(!strcmp(tokens[3], ">="))
        compare = CMP_GE;
    else if(!strcmp(tokens[3], "=="))
        compare = 0;
    else {
        printf("Unknown comparison: %s\n", tokens[3]);
        return;
    }

    int i = strtoul(tokens[1], NULL, 0);
    if (i < 0 || i > NUMBER_OF_BREAKPOINTS) {
        printf("invalid breakpoint: %d\n", i);
        return;
    }
    uint32_t value = strtoul(tokens[4], NULL, 0);

    switch(space) {
        case SPACE_PC:
            ctx->breakpoints[i].breakpoint_type = pc_equal;
            ctx->breakpoints[i].value = value;
            break;
        case SPACE_REG:
            ctx->breakpoints[i].address = address;
            ctx->breakpoints[i].value = value;
            switch(compare) {
                case CMP_EQUAL: ctx->breakpoints[i].breakpoint_type = reg_value_equal; break;
                case CMP_LT   : ctx->breakpoints[i].breakpoint_type = reg_value_lt; break;
                case CMP_LE   : ctx->breakpoints[i].breakpoint_type = reg_value_le; break;
                case CMP_GT   : ctx->breakpoints[i].breakpoint_type = reg_value_gt; break;
                case CMP_GE   : ctx->breakpoints[i].breakpoint_type = reg_value_ge; break;
            }
            break;
        case SPACE_MEM:
            ctx->breakpoints[i].address = address;
            ctx->breakpoints[i].value = value;
            switch(compare) {
                case CMP_EQUAL: ctx->breakpoints[i].breakpoint_type = mem_value_equal; break;
                case CMP_LT   : ctx->breakpoints[i].breakpoint_type = mem_value_lt; break;
                case CMP_LE   : ctx->breakpoints[i].breakpoint_type = mem_value_le; break;
                case CMP_GT   : ctx->breakpoints[i].breakpoint_type = mem_value_gt; break;
                case CMP_GE   : ctx->breakpoints[i].breakpoint_type = mem_value_ge; break;
            }
            break;
    }
    printf("Breakpoint %d: ", i);
    print_breakpoint(&ctx->breakpoints[i]);
    printf("\n");
}

void cmd_show_breakpoint(debugger_context_t *ctx, char **tokens) {
    if(strlen(tokens[1]) < 1) {
        for(int i = 0; i < NUMBER_OF_BREAKPOINTS; i++) {
            if(ctx->breakpoints[i].breakpoint_type != disabled) {
                printf("Breakpoint %d: ", i);
                print_breakpoint(&ctx->breakpoints[i]);
                printf("\n");
            }
        }
        return;
    }
    int i = strtol(tokens[1], NULL, 0);
    if(i < 0 || i >= NUMBER_OF_BREAKPOINTS) {
        printf("invalid breakpoint %d\n", i);
        return;
    }
    if(ctx->breakpoints[i].breakpoint_type != disabled) {
        printf("Breakpoint %d: ", i);
        print_breakpoint(&ctx->breakpoints[i]);
        printf("\n");
    } else {
        printf("Breakpoint %d: not set\n", i);
    }
}

void cmd_clear_breakpoint(debugger_context_t *ctx, char **tokens) {
    if(strlen(tokens[1]) < 1)
        return;

    int i = strtol(tokens[1], NULL, 0);
    if(i < 0 || i >= NUMBER_OF_BREAKPOINTS) {
        printf("invalid breakpoint %d\n", i);
        return;
    }
    ctx->breakpoints[i].breakpoint_type = disabled;
}

static bool _is_at_specific_breakpoint(debugger_context_t *ctx,
    breakpoint_t *bp) {
    switch (bp->breakpoint_type) {
        case    disabled:   return false;
        case    pc_equal:   return ctx->ctx.PC == bp->value;
        case    reg_value_equal:    return ctx->ctx.regs[bp->address] == bp->value;
        case    reg_value_lt:       return ctx->ctx.regs[bp->address] < bp->value;
        case    reg_value_le:       return ctx->ctx.regs[bp->address] <= bp->value;
        case    reg_value_gt:       return ctx->ctx.regs[bp->address] > bp->value;
        case    reg_value_ge:       return ctx->ctx.regs[bp->address] >= bp->value;
        case    mem_value_equal:    return ctx->memory[bp->address] == bp->value;
        case    mem_value_lt:       return ctx->memory[bp->address] < bp->value;
        case    mem_value_le:       return ctx->memory[bp->address] <= bp->value;
        case    mem_value_gt:       return ctx->memory[bp->address] > bp->value;
        case    mem_value_ge:       return ctx->memory[bp->address] >= bp->value;
        default: return false;
    }
}

static void print_reached_breakpoint(debugger_context_t *ctx) {
    int i;

    printf("The following breakpoint(s) have been reached:\n\n");

    for (i = 0; i < NUMBER_OF_BREAKPOINTS; i++)
        if (_is_at_specific_breakpoint(ctx, &ctx->breakpoints[i])) {
            printf("breakpoint %d: ", i);
            print_breakpoint(&ctx->breakpoints[i]);
            printf("\n");
        }
}

static bool is_at_any_breakpoint(debugger_context_t *ctx) {
    for(int i = 0; i < NUMBER_OF_BREAKPOINTS; i++)
        if(_is_at_specific_breakpoint(ctx, &ctx->breakpoints[i]))
            return true;
    return false;
}

void cmd_run(debugger_context_t *ctx, char **tokens) {
    uint32_t queue_data[8];
    int queue;

    single_step_result_t result;
    while(true) {
        result = single_step(&ctx->ctx, ctx->code, ctx->memory, queue_data, &queue);
        switch(result) {
            case at_end:
                printf("END reached\n");
                return;
            case normal_execution:
                break;
            case queue_write:
                printf("Queue write: %d\n", queue);
                for (int i = 0; i < 8; i++)
                    printf("\t%d: %08X\n", i, queue_data[i]);
                break;
            case invalid_address:
                printf("ERROR: attempt to read/write invalid memory address\n");
                return;
                break;
        }
        if(is_at_any_breakpoint(ctx)) {
            print_reached_breakpoint(ctx);
            break;
        }
    }
}

command_t commands[] = {
    { "help", "h", cmd_print_help, "print command list" },
    { "quit", "q", cmd_quit, "exit debugger"},
    { "info", "i", cmd_print_info, "print gpu context"},
    { "step", "s", cmd_step, "single step a single instruction"},
    { "disasm", "d", cmd_disasm, "disassemble"},
    { "setr", "r", cmd_setr, "set register value"},
    { "dump", "m", cmd_dump_memory, "show memory contents"},
    { "setm", "w", cmd_set_memory, "set memory contents" },
    { "setbp", "b", cmd_set_breakpoint, "set breakpoint" },
    { "showbp", "t", cmd_show_breakpoint, "show breakpoints" },
    { "clearbp", "y", cmd_clear_breakpoint, "clear breakpoint" },
    { "run", "c", cmd_run, "run program to completion" },
    { NULL, NULL, NULL, NULL}
};

bool running = true;
int main(int argc, char *argv[]) {
    FILE *binaryfp = NULL;
    int argv_idx = 1;
    while(argv_idx < argc) {
        if(argv[argv_idx][0] == '-' || argv[argv_idx][0] == '/') {
            switch(argv[argv_idx][1]) {
                case 'b':
                case 'B':
                    binaryfp = fopen(argv[argv_idx + 1], "r");
                    if(!binaryfp) {
                        printf("Failure to open binary image file: %s\n", argv[argv_idx + 1]);
                        exit(-__LINE__);
                    }
                    argv_idx += 2;
                    break;
                default:
                    usage_and_exit();
            }
        } else {
            usage_and_exit();
        }
    }

    if(!binaryfp) {
        usage_and_exit();
    }

    debugger_context_t *ctx = (debugger_context_t *)malloc(sizeof(debugger_context_t));
    memset(ctx, 0, sizeof(debugger_context_t));

    ctx->code = (uint32_t *)malloc(0x10000);
    ctx->memory = (uint8_t *)malloc(0x10000);

    fread(ctx->code, 0x10000 / sizeof(uint32_t), sizeof(uint32_t), binaryfp);

    char cmd[MAX_LINE_LENGTH];
    char last_cmd[MAX_LINE_LENGTH];
    char    *tokens[6];
    for (int i = 0; i < 6; i++)
        tokens[i] = (char *)malloc(MAX_LINE_LENGTH);

    while(running) {
        printf("debugger > ");
        fflush(stdout);

        fgets(cmd, MAX_LINE_LENGTH, stdin);
        if(strlen(cmd) <= 1) strcpy(cmd, last_cmd);
        else                 strcpy(last_cmd, cmd);

        tokenize(cmd, tokens, 6);

        int idx = 0;
        while(commands[idx].cmd) {
            if(!strcmp(tokens[0], commands[idx].cmd) || (strlen(tokens[0]) == 1 && commands[idx].short_cmd[0] == tokens[0][0])) {
                commands[idx].func(ctx, tokens);
                break;
            }
            idx++;
        }
        if(!commands[idx].cmd)
            printf("Unknown command: %s\n", tokens[0]);
    }

    printf("Goodbye\n");

    return 0;
}