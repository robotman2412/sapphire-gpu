#ifndef GPU_H
#define GPU_H

#include <stdint.h>
#include <stdbool.h>

#define OP_ARITHMETIC   0x0
#define OP_GROUP_MEMORY 0x1
#define OP_PCCONTROL    0x2
#define OP_SYSTEM       0x3

#define AND         0x00
#define OR          0x01
#define XOR         0x02
#define SLL         0x04
#define SRL         0x05
#define SRA         0x06
#define ADD         0x10
#define SUB         0x11
#define MUL         0x12
#define DIV         0x13
#define CMP         0x14
#define ITOF        0x16
#define MULU        0x18
#define DIVU        0x19
#define MULH        0x1A
#define REM         0x1B
#define MULHU       0x1C
#define REMU        0x1D
#define FADD        0x20
#define FSUB        0x21
#define FMUL        0x22
#define FDIV        0x23
#define FCMP        0x24
#define FTOI        0x26

#define SIMD1       0x000
#define SIMD2       0x080
#define SIMD4       0x100

#define LB          0x00
#define LH          0x10
#define LW          0x20
#define SB          0x01
#define SH          0x11
#define SW          0x21
#define LBU         0x02
#define LHU         0x12
#define LWU         0x22
#define AUIPC       0x04
#define LUI         0x05

#define JMP         0x0
#define BRS         0x1

#define END         0x0
#define STOREQ      0x1

#define DEST        0x01
#define REG1        0x02
#define REG2        0x04
#define IMM         0x08
#define LARGE_IMM   0x10
#define LABEL       0x20

typedef struct {
    char *mnemonic;
    uint32_t opcode_group;
    uint32_t opcode;
    uint32_t operand0;
    uint32_t operand1;
    uint32_t operand2;
} instruction_t;

extern instruction_t instructions[];

#define OPGROUP_SHIFT 30
#define OPGROUP_MASK 0x3
#define DEST_SHIFT 26
#define DEST_MASK 0xF
#define REG1_SHIFT 22
#define REG1_MASK 0xF
#define REG2_SHIFT 8
#define REG2_MASK 0xF
#define OPCODE0_SHIFT 13
#define OPCODE0_MASK 0x1FF
#define OPCODE1_SHIFT 17
#define OPCODE1_MASK 0x7
#define OPCODE2_SHIFT 20
#define OPCODE2_MASK 0x3
#define OPCODE3_SHIFT 13
#define OPCODE3_MASK 0x1FF
#define LARGE_IMM_SHIFT 12

uint32_t encode_instruction(uint32_t opgroup, uint32_t opcode,
                            bool large_imm,
                            uint32_t dest, uint32_t reg1,
                            uint32_t reg2, uint32_t imm);

bool disasm_instruction(uint32_t instruction, uint32_t pc, char *s);

#define MAX_LINE_LENGTH (16*1024)
struct debug_symbol {
    int     line_number;
    int     line_length;
    char    line[MAX_LINE_LENGTH];
};

typedef struct {
    uint32_t regs[16];
    uint32_t PC;
} gpu_context_t;

typedef enum {
    at_end = 0,
    normal_execution = 1,
    queue_write = 2,
    invalid_address = -1,
} single_step_result_t;

single_step_result_t single_step(gpu_context_t *ctx, uint32_t *code, uint8_t *mem, uint32_t *queue_data, int *queue);

#endif