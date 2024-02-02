#include "gpu.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

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

command_t commands[] = {
    { "help", "h", cmd_print_help, "print command list" },
    { "quit", "q", cmd_quit, "exit debugger"},
    { "info", "i", cmd_print_info, "print gpu context"},
    { "step", "s", cmd_step, "single step a single instruction"},
    { "disasm", "d", cmd_disasm, "disassemble"},
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