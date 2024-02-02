#include "gpu.h"
#include <stdlib.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

instruction_t instructions[] = {
    {   "and",   OP_ARITHMETIC  , SIMD1 | AND  ,    DEST, REG1,      REG2 },
    {   "or",    OP_ARITHMETIC  , SIMD1 | OR   ,    DEST, REG1,      REG2 },
    {   "xor",   OP_ARITHMETIC  , SIMD1 | XOR  ,    DEST, REG1,      REG2 },
    {   "sll",   OP_ARITHMETIC  , SIMD1 | SLL  ,    DEST, REG1,      REG2 },
    {   "srl",   OP_ARITHMETIC  , SIMD1 | SRL  ,    DEST, REG1,      REG2 },
    {   "sra",   OP_ARITHMETIC  , SIMD1 | SRA  ,    DEST, REG1,      REG2 },
    {   "add",   OP_ARITHMETIC  , SIMD1 | ADD  ,    DEST, REG1,      REG2 },
    {   "sub",   OP_ARITHMETIC  , SIMD1 | SUB  ,    DEST, REG1,      REG2 },
    {   "mul",   OP_ARITHMETIC  , SIMD1 | MUL  ,    DEST, REG1,      REG2 },
    {   "div",   OP_ARITHMETIC  , SIMD1 | DIV  ,    DEST, REG1,      REG2 },
    {   "cmp",   OP_ARITHMETIC  , SIMD1 | CMP  ,    DEST, REG1,      REG2 },
    {   "itof",  OP_ARITHMETIC  , SIMD1 | ITOF ,    DEST, REG1,      REG2 },
    {   "mulu",  OP_ARITHMETIC  , SIMD1 | MULU ,    DEST, REG1,      REG2 },
    {   "divu",  OP_ARITHMETIC  , SIMD1 | DIVU ,    DEST, REG1,      REG2 },
    {   "mulh",  OP_ARITHMETIC  , SIMD1 | MULH ,    DEST, REG1,      REG2 },
    {   "rem",   OP_ARITHMETIC  , SIMD1 | REM  ,    DEST, REG1,      REG2 },
    {   "mulhu", OP_ARITHMETIC  , SIMD1 | MULHU,    DEST, REG1,      REG2 },
    {   "remu",  OP_ARITHMETIC  , SIMD1 | REMU ,    DEST, REG1,      REG2 },
    {   "fadd",  OP_ARITHMETIC  , SIMD1 | FADD ,    DEST, REG1,      REG2 },
    {   "fsub",  OP_ARITHMETIC  , SIMD1 | FSUB ,    DEST, REG1,      REG2 },
    {   "fmul",  OP_ARITHMETIC  , SIMD1 | FMUL ,    DEST, REG1,      REG2 },
    {   "fdiv",  OP_ARITHMETIC  , SIMD1 | FDIV ,    DEST, REG1,      REG2 },
    {   "fcmp",  OP_ARITHMETIC  , SIMD1 | FCMP ,    DEST, REG1,      REG2 },
    {   "ftoi",  OP_ARITHMETIC  , SIMD1 | FTOI ,    DEST, REG1,      REG2 },
    {   "addi",  OP_ARITHMETIC  , SIMD1 | ADD  ,    DEST, REG1, LARGE_IMM },

    {   "and2",   OP_ARITHMETIC , SIMD2 | AND  ,    DEST, REG1,      REG2 },
    {   "or2",    OP_ARITHMETIC , SIMD2 | OR   ,    DEST, REG1,      REG2 },
    {   "xor2",   OP_ARITHMETIC , SIMD2 | XOR  ,    DEST, REG1,      REG2 },
    {   "sll2",   OP_ARITHMETIC , SIMD2 | SLL  ,    DEST, REG1,      REG2 },
    {   "srl2",   OP_ARITHMETIC , SIMD2 | SRL  ,    DEST, REG1,      REG2 },
    {   "sra2",   OP_ARITHMETIC , SIMD2 | SRA  ,    DEST, REG1,      REG2 },
    {   "add2",   OP_ARITHMETIC , SIMD2 | ADD  ,    DEST, REG1,      REG2 },
    {   "sub2",   OP_ARITHMETIC , SIMD2 | SUB  ,    DEST, REG1,      REG2 },
    {   "mul2",   OP_ARITHMETIC , SIMD2 | MUL  ,    DEST, REG1,      REG2 },
    {   "div2",   OP_ARITHMETIC , SIMD2 | DIV  ,    DEST, REG1,      REG2 },
    {   "cmp2",   OP_ARITHMETIC , SIMD2 | CMP  ,    DEST, REG1,      REG2 },
    {   "itof2",  OP_ARITHMETIC , SIMD2 | ITOF ,    DEST, REG1,      REG2 },
    {   "mulu2",  OP_ARITHMETIC , SIMD2 | MULU ,    DEST, REG1,      REG2 },
    {   "divu2",  OP_ARITHMETIC , SIMD2 | DIVU ,    DEST, REG1,      REG2 },
    {   "mulh2",  OP_ARITHMETIC , SIMD2 | MULH ,    DEST, REG1,      REG2 },
    {   "rem2",   OP_ARITHMETIC , SIMD2 | REM  ,    DEST, REG1,      REG2 },
    {   "mulhu2", OP_ARITHMETIC , SIMD2 | MULHU,    DEST, REG1,      REG2 },
    {   "remu2",  OP_ARITHMETIC , SIMD2 | REMU ,    DEST, REG1,      REG2 },
    {   "fadd2",  OP_ARITHMETIC , SIMD2 | FADD ,    DEST, REG1,      REG2 },
    {   "fsub2",  OP_ARITHMETIC , SIMD2 | FSUB ,    DEST, REG1,      REG2 },
    {   "fmul2",  OP_ARITHMETIC , SIMD2 | FMUL ,    DEST, REG1,      REG2 },
    {   "fdiv2",  OP_ARITHMETIC , SIMD2 | FDIV ,    DEST, REG1,      REG2 },
    {   "fcmp2",  OP_ARITHMETIC , SIMD2 | FCMP ,    DEST, REG1,      REG2 },
    {   "ftoi2",  OP_ARITHMETIC , SIMD2 | FTOI ,    DEST, REG1,      REG2 },
    {   "addi2",  OP_ARITHMETIC , SIMD2 | ADD  ,    DEST, REG1, LARGE_IMM },

    {   "and4",   OP_ARITHMETIC , SIMD4 | AND  ,    DEST, REG1,      REG2 },
    {   "or4",    OP_ARITHMETIC , SIMD4 | OR   ,    DEST, REG1,      REG2 },
    {   "xor4",   OP_ARITHMETIC , SIMD4 | XOR  ,    DEST, REG1,      REG2 },
    {   "sll4",   OP_ARITHMETIC , SIMD4 | SLL  ,    DEST, REG1,      REG2 },
    {   "srl4",   OP_ARITHMETIC , SIMD4 | SRL  ,    DEST, REG1,      REG2 },
    {   "sra4",   OP_ARITHMETIC , SIMD4 | SRA  ,    DEST, REG1,      REG2 },
    {   "add4",   OP_ARITHMETIC , SIMD4 | ADD  ,    DEST, REG1,      REG2 },
    {   "sub4",   OP_ARITHMETIC , SIMD4 | SUB  ,    DEST, REG1,      REG2 },
    {   "mul4",   OP_ARITHMETIC , SIMD4 | MUL  ,    DEST, REG1,      REG2 },
    {   "div4",   OP_ARITHMETIC , SIMD4 | DIV  ,    DEST, REG1,      REG2 },
    {   "cmp4",   OP_ARITHMETIC , SIMD4 | CMP  ,    DEST, REG1,      REG2 },
    {   "itof4",  OP_ARITHMETIC , SIMD4 | ITOF ,    DEST, REG1,      REG2 },
    {   "mulu4",  OP_ARITHMETIC , SIMD4 | MULU ,    DEST, REG1,      REG2 },
    {   "divu4",  OP_ARITHMETIC , SIMD4 | DIVU ,    DEST, REG1,      REG2 },
    {   "mulh4",  OP_ARITHMETIC , SIMD4 | MULH ,    DEST, REG1,      REG2 },
    {   "rem4",   OP_ARITHMETIC , SIMD4 | REM  ,    DEST, REG1,      REG2 },
    {   "mulhu4", OP_ARITHMETIC , SIMD4 | MULHU,    DEST, REG1,      REG2 },
    {   "remu4",  OP_ARITHMETIC , SIMD4 | REMU ,    DEST, REG1,      REG2 },
    {   "fadd4",  OP_ARITHMETIC , SIMD4 | FADD ,    DEST, REG1,      REG2 },
    {   "fsub4",  OP_ARITHMETIC , SIMD4 | FSUB ,    DEST, REG1,      REG2 },
    {   "fmul4",  OP_ARITHMETIC , SIMD4 | FMUL ,    DEST, REG1,      REG2 },
    {   "fdiv4",  OP_ARITHMETIC , SIMD4 | FDIV ,    DEST, REG1,      REG2 },
    {   "fcmp4",  OP_ARITHMETIC , SIMD4 | FCMP ,    DEST, REG1,      REG2 },
    {   "ftoi4",  OP_ARITHMETIC , SIMD4 | FTOI ,    DEST, REG1,      REG2 },
    {   "addi4",  OP_ARITHMETIC , SIMD4 | ADD  ,    DEST, REG1, LARGE_IMM },

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

static bool use_large_imm(instruction_t *ins) {
    return ((ins->operand0 & LARGE_IMM) != 0 ||
           (ins->operand1 & LARGE_IMM) != 0 ||
           (ins->operand2 & LARGE_IMM) != 0) ? true : false;
}

static void format_operand(char *s, uint32_t operand_format, uint32_t pc, uint32_t instruction) {
    uint8_t opgroup = instruction >> 30;
    uint8_t rd = (instruction >> 26) & 0xF;
    uint8_t rs1 = (instruction >> 22) & 0xF;
    uint8_t rs2 = (instruction >> 8) & 0xF;
    uint8_t simd = (instruction >> 20) & 0x3;
    if(opgroup != 0x0) simd = 0;
    bool large_imm = (instruction & 0x1000);

    switch(simd) {
        case 1: rd >>= 1; rs1 >>= 1; rs2 >>= 1; break;
        case 2: rd >>= 2; rs1 >>= 2; rs2 >>= 2; break;
    }

    uint32_t arithImm = (large_imm) ? (instruction & 0xFFF) : (instruction & 0xFF);
    uint32_t arithImmS = arithImm;
    if((instruction & 0xF00) == 0 && (instruction & 0x80) != 0) {
        arithImmS |= 0xFFFFFF00;
    } else if((instruction & 0xF00) != 0 && (instruction & 0x800) != 0) {
        arithImmS |= 0xFFFFF000;
    }

    uint32_t memImm;
    if(instruction & 0x80000) {
        memImm = ((instruction >> 6) & 0xF0000) | ((instruction >> 1) & 0xF000) | (instruction & 0xFFF);
        if(memImm & 0x80000) memImm |= 0xFFF00000;
    } else {
        memImm = ((instruction >> 1) & 0xF000) | (instruction & 0xFFF);
        if(memImm & 0x8000) memImm |= 0xFFFF0000;
    }

    uint32_t pcImm = ((instruction >> 4) & 0xFF00) | (instruction & 0xFF);
    uint32_t imm;
    switch (opgroup)
    {
        case 0: imm = arithImm; break;
        case 1: imm = memImm; break;
        case 2: imm = pcImm; break;
        case 3: imm = arithImm; break;
    }

    char t[128];
    switch(operand_format) {
        case DEST: sprintf(t, "r%d", rd); break;
        case REG1: sprintf(t, "r%d", rs1); break;
        case REG2: sprintf(t, "r%d", rs2); break;
        case LARGE_IMM:
        case IMM:
            sprintf(t, "%d", (int)(int32_t)imm); break;
        default:
            if(operand_format & LABEL)
                sprintf(t, "0x%08X", (uint32_t)(pc + imm));
            break;
    }
    strcpy(s, t);
}

bool disasm_instruction(uint32_t instruction, uint32_t pc, char *s) {
    uint8_t opgroup = instruction >> 30;
    uint8_t simd = (instruction >> 20) & 0x3;
    bool large_imm = (instruction & 0x1000) ? true : false;

    uint8_t arithOp = (instruction >> 13) & 0x7F;
    uint8_t memOp = (instruction >> 17) & 0x7;
    uint8_t pcOp = (instruction >> 20) & 0x3;
    uint16_t systemOp = (instruction >> 13) & 0x1FF;

    int idx = 0;
    while(instructions[idx].mnemonic) {
        instruction_t *ins = &instructions[idx];
        if(ins->opcode_group == opgroup) {
            bool equals = false;
            switch(opgroup) {
                case 0:
                    if(ins->opcode == ((uint32_t)arithOp | ((uint32_t)simd << 7)) &&
                       (use_large_imm(ins) == large_imm))
                       equals = true;
                    break;
                case 1:
                    if(ins->opcode == memOp)
                       equals = true;
                    break;
                case 2:
                    if(ins->opcode == pcOp)
                       equals = true;
                    break;
                case 3:
                    if(ins->opcode == systemOp &&
                       (use_large_imm(ins) == large_imm))
                       equals = true;
                    break;
            }
            if(equals)
                break;
        }
        idx++;
    }

    if(!instructions[idx].mnemonic)
        return false;

    char t[128];

    sprintf(s, "%-10.10s", instructions[idx].mnemonic);
    bool first_operand = true;
    if(instructions[idx].operand0) {
        format_operand(t, instructions[idx].operand0, pc, instruction);
        if(!first_operand)
            strcat(s, ", ");
        strcat(s, t);
        first_operand = false;
    }
    if(instructions[idx].operand1) {
        format_operand(t, instructions[idx].operand1, pc, instruction);
        if(!first_operand)
            strcat(s, ", ");
        strcat(s, t);
        first_operand = false;
    }
    if(instructions[idx].operand2) {
        format_operand(t, instructions[idx].operand2, pc, instruction);
        if(!first_operand)
            strcat(s, ", ");
        strcat(s, t);
        first_operand = false;
    }

    return true;
}

single_step_result_t single_step(gpu_context_t *ctx, uint32_t *code, uint8_t *mem, uint32_t *queue_data, int *queue) {
    uint32_t instruction = code[ctx->PC];

    uint8_t opgroup = instruction >> 30;
    uint8_t rd = (instruction >> 26) & 0xF;
    uint8_t rs1 = (instruction >> 22) & 0xF;
    uint8_t rs2 = (instruction >> 8) & 0xF;
    uint8_t simd = (instruction >> 20) & 0x3;
    if(opgroup != 0x0) simd = 0;
    bool large_imm = (instruction & 0x1000);

    uint32_t dest_list[4] = {0, 0, 0, 0};
    uint32_t rs1_list[4] = {0, 0, 0, 0};
    uint32_t rs2_list[4] = {0, 0, 0, 0};
    uint32_t reg_count = 1;
    switch(simd) {
        case 1:
            for(int i = 0; i < 2; i++) {
                dest_list[i] = ((rd & 0x7) << 1) + i;
                rs1_list[i] = ((rs1 & 0x7) << 1) + i;
                rs2_list[i] = ((rs2 & 0x7) << 1) + i;
            }
            reg_count = 2;
            break;
        case 2:
            for(int i = 0; i < 4; i++) {
                dest_list[i] = ((rd & 0x3) << 2) + i;
                rs1_list[i] = ((rs1 & 0x3) << 2) + i;
                rs2_list[i] = ((rs2 & 0x3) << 2) + i;
            }
            reg_count = 4;
            break;
        case 0:
        default:
            dest_list[0] = rd;
            rs1_list[0] = rs1;
            rs2_list[0] = rs2;
            break;
    }

    uint32_t rs1_data[4];
    uint32_t rs2_data[4];
    for(int i = 0; i < 4; i++) {
        rs1_data[i] = ctx->regs[rs1_list[i]];
        rs2_data[i] = ctx->regs[rs2_list[i]];
    }

    uint32_t arithImm = (large_imm) ? (instruction & 0xFFF) : (instruction & 0xFF);
    uint32_t arithImmS = arithImm;
    if((instruction & 0xF00) == 0 && (instruction & 0x80) != 0) {
        arithImmS |= 0xFFFFFF00;
    } else if((instruction & 0xF00) != 0 && (instruction & 0x800) != 0) {
        arithImmS |= 0xFFFFF000;
    }

    uint32_t memImm;
    if(instruction & 0x80000) {
        memImm = ((instruction >> 6) & 0xF0000) | ((instruction >> 1) & 0xF000) | (instruction & 0xFFF);
        if(memImm & 0x80000) memImm |= 0xFFF00000;
    } else {
        memImm = ((instruction >> 1) & 0xF000) | (instruction & 0xFFF);
        if(memImm & 0x8000) memImm |= 0xFFFF0000;
    }

    uint32_t pcImm = ((instruction >> 4) & 0xFF00) | (instruction & 0xFF);

    uint8_t arithOp = (instruction >> 13) & 0x7F;
    uint8_t memOp = (instruction >> 17) & 0x7;
    uint8_t pcOp = (instruction >> 20) & 0x3;
    uint16_t systemOp = (instruction >> 13) & 0x1FF;

    switch(opgroup) {
        case 0x0:
            for(int i = 0; i < reg_count; i++) {
                uint32_t arithOp1 = rs1_data[i];
                uint32_t arithOp2 = (large_imm) ? arithImmS : rs2_data[i];
                float arithfOp1 = *((float *)&arithOp1);
                float arithfOp2 = *((float *)&arithOp2);

                switch(arithOp) {
                    case AND: ctx->regs[dest_list[i]] = arithOp1 & arithOp2; break;
                    case OR : ctx->regs[dest_list[i]] = arithOp1 | arithOp2; break;
                    case XOR: ctx->regs[dest_list[i]] = arithOp1 ^ arithOp2; break;
                    case SLL: ctx->regs[dest_list[i]] = arithOp1 << arithOp2; break;
                    case SRL: ctx->regs[dest_list[i]] = arithOp1 >> arithOp2; break;
                    case SRA: ctx->regs[dest_list[i]] = (uint32_t)((int32_t)arithOp1 >> arithOp2); break;
                    case ADD: ctx->regs[dest_list[i]] = arithOp1 + arithOp2; break;
                    case SUB: ctx->regs[dest_list[i]] = arithOp1 - arithOp2; break;
                    case MUL: ctx->regs[dest_list[i]] = (uint32_t)((int64_t)(int32_t)arithOp1 * (int64_t)(int32_t)arithOp2); break;
                    case DIV: ctx->regs[dest_list[i]] = (uint32_t)((int32_t)arithOp1 / (int32_t)arithOp2); break;
                    case CMP: ctx->regs[dest_list[i]] = (arithOp1 < arithOp2) ? 1 : 0; break; //TODO
                    case ITOF:
                        {
                            float value = (float)arithOp1;
                            uint32_t converted = *((uint32_t *)&value);
                            ctx->regs[dest_list[i]] = converted;
                        }
                        break;
                    case MULU: ctx->regs[dest_list[i]] = (uint32_t)((uint64_t)arithOp1 * (uint64_t)arithOp2); break;
                    case DIVU: ctx->regs[dest_list[i]] = (uint32_t)(arithOp1 / arithOp2); break;
                    case MULH: ctx->regs[dest_list[i]] = (uint32_t)((uint64_t)((int64_t)(int32_t)arithOp1 * (int64_t)(int32_t)arithOp2) >> 32); break;
                    case REM : ctx->regs[dest_list[i]] = (uint32_t)((int32_t)arithOp1 % (int32_t)arithOp2); break;
                    case MULHU: ctx->regs[dest_list[i]] = (uint32_t)(((uint64_t)arithOp1 * (uint64_t)arithOp2) >> 32); break;
                    case REMU: ctx->regs[dest_list[i]] = (uint32_t)((uint32_t)arithOp1 % (uint32_t)arithOp2); break;
                    case FADD:
                        {
                            float result = arithfOp1 + arithfOp2;
                            ctx->regs[dest_list[i]] = *((uint32_t *)&result);
                        }
                        break;
                    case FSUB:
                        {
                            float result = arithfOp1 - arithfOp2;
                            ctx->regs[dest_list[i]] = *((uint32_t *)&result);
                        }
                        break;
                    case FMUL:
                        {
                            float result = arithfOp1 * arithfOp2;
                            ctx->regs[dest_list[i]] = *((uint32_t *)&result);
                        }
                        break;
                    case FDIV:
                        {
                            float result = arithfOp1 / arithfOp2;
                            ctx->regs[dest_list[i]] = *((uint32_t *)&result);
                        }
                        break;
                    case FCMP: ctx->regs[dest_list[i]] = (arithfOp1 < arithfOp2) ? 1 : 0; break;
                    case FTOI:
                        {
                            uint32_t value = arithOp1;
                            float converted = *((float *)&value);
                            ctx->regs[dest_list[i]] = (int32_t)converted;
                        }
                        break;
                    default:
                        return -1;
                }
            }
            ctx->PC++;
            break;
        case 0x1:
            {
                uint32_t address = rs1_data[0] + memImm;
                uint8_t size = (instruction >> 20) & 0x3;
                if(memOp < 0x4) {
                    switch(size) {
                        case 1: if(address & 0x1) return invalid_address; break;
                        case 2: if(address & 0x3) return invalid_address; break;
                    }
                }
                switch(memOp) {
                    case 0x0: //LOAD Signed
                        switch(size) {
                            case 0x0: ctx->regs[rd] = (uint32_t)(int32_t)(int8_t)mem[address];
                            case 0x1: ctx->regs[rd] = (uint32_t)(int32_t)(int16_t)(*((uint16_t *)&mem[address]));
                            case 0x2: ctx->regs[rd] = *((uint32_t *)&mem[address]);
                            default: return -1;
                        }
                        break;
                    case 0x1: //STORE Signed
                        switch(size) {
                            case 0x0: mem[address] = rs2_data[0]; break;
                            case 0x1: *((uint16_t *)&mem[address]) = rs2_data[0]; break;
                            case 0x2: *((uint32_t *)&mem[address]) = rs2_data[0]; break;
                            default: return -1;
                        }
                        break;
                    case 0x3: // LOAD Unsigned
                        switch(size) {
                            case 0x0: ctx->regs[rd] = mem[address];
                            case 0x1: ctx->regs[rd] = *((uint16_t *)&mem[address]);
                            case 0x2: ctx->regs[rd] = *((uint32_t *)&mem[address]);
                            default: return -1;
                        }
                        break;
                    case 0x4: ctx->regs[rd] = ctx->PC + memImm; break;
                    case 0x5: ctx->regs[rd] = memImm << 12; break;
                    default:
                        return -1;
                }
            }
            ctx->PC++;
            break;
        case 0x2:
            {
                switch(pcOp) {
                    case JMP:
                        ctx->PC += pcImm;
                        break;
                    case BRS:
                        if(rs1_data[0] & 0x1)
                            ctx->PC += pcImm;
                        else
                            ctx->PC++;
                        break;
                    default:
                        return -1;
                }
            }
            break;
        case 0x3:
            {
                switch(systemOp) {
                    case END:
                        return at_end;
                        break;
                    case STOREQ:
                        *queue = pcImm;
                        for(int i = 0; i < 8; i++)
                            queue_data[i] = ctx->regs[i];
                        ctx->PC++;
                        return queue_write;
                        break;
                    default:
                        return -1;
                }
            }
            ctx->PC++;
            break;
        default:
            break;
    }

    return normal_execution;
}