#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <ctype.h>
#include "gpu.h"

bool _isdiscardable(char ch) {
    switch(ch) {
        case '/':
        case ',':
        case ' ':
        case '\t':
            return true;
        default:
            return false;
    }
}

static bool _iscommentstart(char ch) {
    switch (ch) {
        case    '#':
            return true;
        default:
            return false;
    }
}

static bool _ishardnewline(char ch) {
    switch (ch) {
        case    '\0':
        case    '\n':
            return true;
        default:
            return false;
    }
}

static bool _isnewline(char ch) {
    switch (ch) {
        case    '\0':
        case    '\n':
        case    ';':
            return true;
        default:
            return false;
    }
}

static char _lowercase(char ch) {
    if (ch >= 'A' && ch <= 'Z')
        return ch - 'A' + 'a';
    return ch;
}

static int line_number = 1;
static int _readline(char *s, int maxlen, FILE *infp) {
    int count = 0;
    char    ch;

    while (count < maxlen) {
        if (feof(infp)) {
            if (count > 0)
                return count;
            else
                return -1;
        }
        ch = fgetc(infp);
        if (feof(infp) && count == 0)
            return -1;

        // Update the line number if needed
        if (_ishardnewline(ch))
            ++line_number;

        // Skip white space at the start of a line
        if ((_isdiscardable(ch) || _isnewline(ch)) && count == 0)
            continue;

        // if this is a comment, skip the input until the of the line
        if (_iscommentstart(ch) && count == 0) {
            fgetc(infp);  // discard character
            fgets(s, maxlen, infp);
            int temp_line_number = atoi(s);
            if (temp_line_number > 0)
                line_number = temp_line_number;
            continue;
        }

        // If we are starting a comment or ending a line and we have
        // characters, then return.
        if ((_isnewline(ch) || _iscommentstart(ch)) && count != 0) {
            *s = '\0';
            return count;
        }

        *s = ch;
        ++s;
        ++count;
    }

    printf("Long line on input\n");
    exit(-__LINE__);
}

enum {
    output_format_ascii_hex = 1,
    output_format_ascii_binary,
    output_format_binary,
};

static void _parse_operand(
    uint32_t opgroup,
    uint32_t *imm,
    uint32_t   *dest,
    uint32_t   *reg1,
    uint32_t   *reg2,
    bool        *large_imm,
    char        *s,
    uint32_t   operand_format,
    char        *input_line,
    bool        *use_label,
    char        *label)
{
    if (operand_format == 0) {
        if (strlen(s) != 0) {
            printf("Malformed operand on line: (%d) %s\n", line_number, input_line);
            exit(-__LINE__);
        }
        return;
    }

    if (operand_format != IMM && operand_format != LARGE_IMM && ((operand_format & LABEL) == 0)) {
        if (!(*s == 'r' || *s == 'R' || *s == '$')) {
            printf("Malformed operand on line: (%d) %s\n", line_number, input_line);
            exit(-__LINE__);
        }
        ++s;
    }

    if((operand_format & LABEL) && isalpha(*s)) {
        *use_label = true;
        memcpy(label, s, strlen(s));
        label[strlen(s)] = '\0';
        return;
    }
    else if (operand_format == IMM || operand_format == LARGE_IMM) {
        uint32_t v = strtoul(s, NULL, 0);
        if(opgroup == 0 || opgroup == 3) {
            v &= (operand_format == LARGE_IMM) ? 0xFFF : 0xFF;
        } else {
            v &= 0xFFFF;
        }
        *imm = v;
        *large_imm = operand_format == LARGE_IMM;
        return;
    }

    if (operand_format == DEST)
        *dest = atoi(s);
    else if (operand_format == REG1)
        *reg1 = atoi(s);
    else if (operand_format == REG2)
        *reg2 = atoi(s);
    else {
        printf("Malformed assembly format table for instruction: %s\n", input_line);
        exit(-__LINE__);
    }
}

int main(int argc, char *argv[]) {
    FILE    *infp = stdin;
    FILE    *outfp = stdout;
    FILE    *debugfp = NULL;
    int     output_format = output_format_ascii_hex;
    int     i;

    i = 1;
    while (i < argc) {
        if (argv[i][0] == '-') {
            switch(argv[i][1]) {
                case    'i':
                case    'I':
                    infp = fopen(argv[i + 1], "r");
                    if (!infp) {
                        printf("Failure to open input file: %s\n", argv[i + 1]);
                        exit(__LINE__);
                    }
                    i += 2;
                    break;
                case    'o':
                case    'O':
                    outfp = fopen(argv[i + 1], "w");
                    if (!outfp) {
                        printf("Failure to create output file: %s\n", argv[i + 1]);
                        exit(__LINE__);
                    }
                    i += 2;
                    break;
                case    'd':
                case    'D':
                    debugfp = fopen(argv[i + 1], "w");
                    if (!debugfp) {
                        printf("Failure to create debug symbols file: %s\n", argv[i + 1]);
                        exit(__LINE__);
                    }
                    i += 2;
                    break;
                case    'a':
                case    'A':
                    output_format = output_format_ascii_hex;
                    ++i;
                    break;
                case    'b':
                case    'B':
                    output_format = output_format_ascii_binary;
                    ++i;
                    break;
                case    'm':
                case    'M':
                    output_format = output_format_binary;
                    ++i;
                    break;
                default:
                    printf("Usage:\n"
                        "\t-i <input>     input file\n"
                        "\t-o <output>    output file\n"
                        "\t-d <file>      debug symbols file\n"
                        "\t-a             output ascii hex\n"
                        "\t-b             output ascii binary\n"
                        "\t-m             output binary\n");
                    exit(__LINE__);
            }
        } else {
            printf("Try -- for help\n");
            exit(-__LINE__);
        }
    }

    char    input_line[MAX_LINE_LENGTH];

    #define MAX_TOKENS  5
    char    tokens[MAX_TOKENS][MAX_LINE_LENGTH];
    int token;
    char    *s;

    uint32_t instructionCount = 0;
    uint32_t instructionSetSize = 0x400;
    uint32_t *rawInstructions = (uint32_t *)malloc(instructionSetSize * sizeof(uint32_t));
    if(rawInstructions == NULL) {
        if (infp != stdin)
            fclose(infp);
        if (outfp != stdout)
            fclose(outfp);

        printf("Failed to allocate\n");
        exit(-__LINE__);
    }

    typedef struct {
        char *name;
        uint32_t address;
    } label_t;
    uint32_t labelCount = 0;
    uint32_t labelSetSize = 0x400;
    label_t *labels = (label_t *)malloc(labelSetSize * sizeof(label_t));
    if(labels == NULL) {
        if (infp != stdin)
            fclose(infp);
        if (outfp != stdout)
            fclose(outfp);

        free(rawInstructions);

        printf("Failed to allocate\n");
        exit(-__LINE__);
    }

    typedef struct {
        char *label;
        uint32_t address;
    } backpatch_t;
    uint32_t backpatchCount = 0;
    uint32_t backpatchSetSize = 0x400;
    backpatch_t *backpatches = (backpatch_t *)malloc(backpatchSetSize * sizeof(backpatch_t));
    if(backpatches == NULL) {
        if (infp != stdin)
            fclose(infp);
        if (outfp != stdout)
            fclose(outfp);

        free(labels);
        free(rawInstructions);

        printf("Failed to allocate\n");
        exit(-__LINE__);
    }

    while (_readline(input_line, MAX_LINE_LENGTH, infp) > 0) {
        // Parse the input line into individual tokens
        i = 0;
        for (token = 0; token < MAX_TOKENS; token++)
            tokens[token][0] = '\0';
        token = 0;
        s = input_line;
        while (*s) {
            if (_isdiscardable(*s)) {
                tokens[token][i] = '\0';
                i = 0;
                ++token;
                while (*s && _isdiscardable(*s))
                    ++s;
                if (!(*s))
                    break;
                if (token >= MAX_TOKENS) {
                    if (!(*s)) {
                        printf("Cannot parse: (%d) %s\n", line_number, input_line);
                        exit(-__LINE__);
                    }
                    break;
                }
            }
            tokens[token][i] = *s;
            ++i;
            ++s;
        }
        if (i > 0)
            tokens[token][i] = '\0';

        if(labelCount >= labelSetSize) {
            labelSetSize += 0x400;
            labels = (label_t *)realloc((void *)labels, labelSetSize * sizeof(label_t));

            if(labels == NULL) {
                if (infp != stdin)
                    fclose(infp);
                if (outfp != stdout)
                    fclose(outfp);

                free(backpatches);
                free(rawInstructions);

                printf("Failed to allocate\n");
                exit(-__LINE__);
            }
        }
        if(tokens[0][strlen(tokens[0])-1] == ':') {
            //This is an address label, store it and skip
            labels[labelCount].address = instructionCount;
            labels[labelCount].name = (char *)malloc(sizeof(uint8_t) * (strlen(tokens[0])-1));
            memcpy(labels[labelCount++].name, tokens[0], strlen(tokens[0])-1);
            if(token == 0) continue;

            for(int i = 0; i < token; i++) {
                memcpy(tokens[i], tokens[i+1], strlen(tokens[i+1]) + 1);
            }
            tokens[token][0] = '\0';
        }

        int ins = 0;
        while (instructions[ins].mnemonic) {
            if (!strcmp(instructions[ins].mnemonic, tokens[0]))
                break;
            ++ins;
        }
        if (!instructions[ins].mnemonic) {
            printf("Cannot parse: (%d) %s\n", line_number, input_line);
            exit(-__LINE__);
        }

        uint32_t opcode = instructions[ins].opcode;
        uint32_t opgroup = instructions[ins].opcode_group;
        uint32_t imm = 0;
        uint32_t dest = 0, reg1 = 0, reg2 = 0;
        bool large_imm = false;
        bool use_label = false;
        char label[MAX_LINE_LENGTH];

        _parse_operand(opgroup, &imm, &dest, &reg1, &reg2, &large_imm,
            tokens[1], instructions[ins].operand0, input_line, &use_label, label);
        _parse_operand(opgroup, &imm, &dest, &reg1, &reg2, &large_imm,
            tokens[2], instructions[ins].operand1, input_line, &use_label, label);
        _parse_operand(opgroup, &imm, &dest, &reg1, &reg2, &large_imm,
            tokens[3], instructions[ins].operand2, input_line, &use_label, label);

        if(use_label) {
            if(backpatchCount >= backpatchSetSize) {
                backpatchSetSize += 0x400;
                backpatches = (backpatch_t *)realloc((void *)backpatches, backpatchSetSize * sizeof(backpatch_t));

                if(backpatches == NULL) {
                    if (infp != stdin)
                        fclose(infp);
                    if (outfp != stdout)
                        fclose(outfp);

                    free(labels);
                    free(rawInstructions);

                    printf("Failed to allocate\n");
                    exit(-__LINE__);
                }
            }

            backpatches[backpatchCount].address = instructionCount;
            backpatches[backpatchCount].label = (char *)malloc(sizeof(uint8_t) * (strlen(label) + 1));
            memcpy(backpatches[backpatchCount++].label, label, strlen(label) + 1);
        }

        uint32_t res = encode_instruction(opgroup, opcode, large_imm, dest, reg1, reg2, imm);
        if(instructionCount >= instructionSetSize) {
            instructionSetSize += 0x400;
            rawInstructions = (uint32_t *)realloc((void *)rawInstructions, instructionSetSize * sizeof(uint32_t));

            if(rawInstructions == NULL) {
                if (infp != stdin)
                    fclose(infp);
                if (outfp != stdout)
                    fclose(outfp);

                free(labels);
                free(backpatches);

                printf("Failed to allocate\n");
                exit(-__LINE__);
            }
        }

        rawInstructions[instructionCount++] = res;

        if (debugfp) {
            struct  debug_symbol   ds;

            memset(&ds, 0, sizeof(ds));
            ds.line_number = line_number;
            ds.line_length = strlen(input_line) + 1;
            strcpy(ds.line, input_line);
            fwrite(&ds, 1, sizeof(int) * 2 + strlen(input_line) + 1, debugfp);
        }
    }

    for(int i = 0; i < backpatchCount; i++) {
        backpatch_t *bp = &backpatches[i];
        int labelIndex = -1;
        for(int j = 0; j < labelCount; j++) {
            if(!strcmp(bp->label, labels[i].name)) {
                labelIndex = j;
                break;
            }
        }
        if(labelIndex == -1) {
            if (infp != stdin)
                fclose(infp);
            if (outfp != stdout)
                fclose(outfp);

            free(rawInstructions);
            free(labels);
            free(backpatches);

            printf("Undefined label %s at address %d\n", bp->label, bp->address);
            exit(-__LINE__);
        }

        uint32_t res = rawInstructions[bp->address];
        uint32_t bpToAddress = labels[labelIndex].address;
        int64_t relativeAddress = (int64_t)(uint64_t)bpToAddress - (int64_t)(uint64_t)bp->address;
        if(relativeAddress < -32768 || relativeAddress > 32767) {
            if (infp != stdin)
                fclose(infp);
            if (outfp != stdout)
                fclose(outfp);

            free(rawInstructions);
            free(labels);
            free(backpatches);

            printf("Relative address out of range(%d): label %s at address %d\n", relativeAddress, bp->label, bp->address);
            exit(-__LINE__);
        }

        //printf("Backpatch %d(%s): %d\n", bp->address, bp->label, relativeAddress);

        res |= ((uint16_t)(relativeAddress) & 0xFF);
        res |= (((uint16_t)(relativeAddress) >> 8) & 0xFF) << 12;
        rawInstructions[bp->address] = res;
    }

    for(uint32_t i = 0; i < instructionCount; i++) {
        uint32_t res = rawInstructions[i];

        uint32_t mask;
        switch (output_format) {
            case    output_format_ascii_hex:
                fprintf(outfp, "0x%8.8x\n", res);
                break;
            case    output_format_ascii_binary:
                i = 0;

                mask = (1 << 31);
                while (i < 32) {
                    if (res & mask)
                        fprintf(outfp, "1");
                    else
                        fprintf(outfp, "0");
                    mask = mask >> 1;
                    ++i;
                }
                fprintf(outfp, "\n");
                break;
            case    output_format_binary:
                fwrite(&res, 1, sizeof(res), outfp);
                break;
        }
    }

    if (infp != stdin)
        fclose(infp);
    if (outfp != stdout)
        fclose(outfp);

    free(rawInstructions);
    free(labels);
    free(backpatches);

    return 0;
}