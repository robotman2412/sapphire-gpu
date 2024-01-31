#include "gpu.h"
#include <stdlib.h>
#include <stddef.h>

instruction_t instructions[] = {
    {   "and",   OP_ARITHMETIC  , AND  ,    DEST, REG1,      REG2 },
    {   "or",    OP_ARITHMETIC  , OR   ,    DEST, REG1,      REG2 },
    {   "xor",   OP_ARITHMETIC  , XOR  ,    DEST, REG1,      REG2 },
    {   "sll",   OP_ARITHMETIC  , SLL  ,    DEST, REG1,      REG2 },
    {   "srl",   OP_ARITHMETIC  , SRL  ,    DEST, REG1,      REG2 },
    {   "sra",   OP_ARITHMETIC  , SRA  ,    DEST, REG1,      REG2 },
    {   "add",   OP_ARITHMETIC  , ADD  ,    DEST, REG1,      REG2 },
    {   "sub",   OP_ARITHMETIC  , SUB  ,    DEST, REG1,      REG2 },
    {   "mul",   OP_ARITHMETIC  , MUL  ,    DEST, REG1,      REG2 },
    {   "div",   OP_ARITHMETIC  , DIV  ,    DEST, REG1,      REG2 },
    {   "cmp",   OP_ARITHMETIC  , CMP  ,    DEST, REG1,      REG2 },
    {   "itof",  OP_ARITHMETIC  , ITOF ,    DEST, REG1,      REG2 },
    {   "mulu",  OP_ARITHMETIC  , MULU ,    DEST, REG1,      REG2 },
    {   "divu",  OP_ARITHMETIC  , DIVU ,    DEST, REG1,      REG2 },
    {   "mulh",  OP_ARITHMETIC  , MULH ,    DEST, REG1,      REG2 },
    {   "rem",   OP_ARITHMETIC  , REM  ,    DEST, REG1,      REG2 },
    {   "mulhu", OP_ARITHMETIC  , MULHU,    DEST, REG1,      REG2 },
    {   "remu",  OP_ARITHMETIC  , REMU ,    DEST, REG1,      REG2 },
    {   "fadd",  OP_ARITHMETIC  , FADD ,    DEST, REG1,      REG2 },
    {   "fsub",  OP_ARITHMETIC  , FSUB ,    DEST, REG1,      REG2 },
    {   "fmul",  OP_ARITHMETIC  , FMUL ,    DEST, REG1,      REG2 },
    {   "fdiv",  OP_ARITHMETIC  , FDIV ,    DEST, REG1,      REG2 },
    {   "fcmp",  OP_ARITHMETIC  , FCMP ,    DEST, REG1,      REG2 },
    {   "ftoi",  OP_ARITHMETIC  , FTOI ,    DEST, REG1,      REG2 },
    {   "addi",  OP_ARITHMETIC  , ADD  ,    DEST, REG1, LARGE_IMM },

    {     "lb",  OP_GROUP_MEMORY, LB   ,    DEST, REG1,       IMM },
    {     "lh",  OP_GROUP_MEMORY, LH   ,    DEST, REG1,       IMM },
    {     "lw",  OP_GROUP_MEMORY, LW   ,    DEST, REG1,       IMM },
    {    "lbu",  OP_GROUP_MEMORY, LBU  ,    DEST, REG1,       IMM },
    {    "lhu",  OP_GROUP_MEMORY, LHU  ,    DEST, REG1,       IMM },
    {    "lwu",  OP_GROUP_MEMORY, LWU  ,    DEST, REG1,       IMM },
    {     "sb",  OP_GROUP_MEMORY, SB   ,    REG1, REG2,       IMM },
    {     "sh",  OP_GROUP_MEMORY, SH   ,    REG1, REG2,       IMM },
    {     "sw",  OP_GROUP_MEMORY, SW   ,    REG1, REG2,       IMM },
    {  "auipc",  OP_GROUP_MEMORY, AUIPC,    DEST, IMM,          0 },
    {    "lui",  OP_GROUP_MEMORY,   LUI,    DEST, IMM,          0 },

    {    "jmp",  OP_PCCONTROL   ,   JMP, IMM|LABEL, 0,          0 },
    {    "brs",  OP_PCCONTROL   ,   BRS, REG1, IMM|LABEL,       0 },

    {    "end",  OP_SYSTEM      ,   END,       0,   0,          0 },
    { "storeq",  OP_SYSTEM      ,STOREQ,     IMM,   0,          0 },
    {    NULL,                 0,     0,       0,   0,          0 }
};

uint32_t encode_instruction(uint32_t opgroup, uint32_t opcode,
                            bool large_imm,
                            uint32_t dest, uint32_t reg1,
                            uint32_t reg2, uint32_t imm)
{
    uint32_t res = 0;
    res |= (opgroup & OPGROUP_MASK) << OPGROUP_SHIFT;
    res |= (dest & DEST_MASK)    << DEST_SHIFT;
    if(((opgroup & OPGROUP_MASK) != 1) || ((opcode & 0x4) == 0)) {
        res |= (reg1 & REG1_MASK)    << REG1_SHIFT;
        if(!large_imm) res |= (reg2 & REG2_MASK) << REG2_SHIFT;
    }

    switch(opgroup & OPGROUP_MASK) {
        case 0x0:
            res |= (opcode & OPCODE0_MASK) << OPCODE0_SHIFT;
            if(large_imm) res |= (imm & 0xFFF);
            else          res |= (imm & 0xFF);
            res |= (large_imm ? 1 : 0) << LARGE_IMM_SHIFT;
            break;
        case 0x1:
            res |= (opcode & OPCODE1_MASK) << OPCODE1_SHIFT;
            if(opcode & 0x4) {
                res |= (imm & 0xFF);
                res |= ((imm >> 8) & 0xF) << 13;
                res |= ((imm >> 12) & 0xF) << 8;
                res |= ((imm >> 16) & 0xF) << 22;
            } else {
                res |= (imm & 0xFF);
                res |= ((imm >> 8) & 0xF) << 13;
            }
            break;
        case 0x2:
            res |= (opcode & OPCODE2_MASK) << OPCODE2_SHIFT;
            res |= (imm & 0xFF);
            res |= ((imm >> 8) & 0xFF) << 12;
            break;
        case 0x3:
            res |= (opcode & OPCODE3_MASK) << OPCODE3_SHIFT;
            if(large_imm) res |= (imm & 0xFFF);
            else          res |= (imm & 0xFF);
            res |= (large_imm ? 1 : 0) << LARGE_IMM_SHIFT;
            break;
    }

    return res;
}