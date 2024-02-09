#ifndef SAPHHIRE_TEST_H
#define SAPHHIRE_TEST_H

#define TESTNUMREG r15

#define TEST_CASE(testnum, testreg, correctval_u, correctval_l, code...) \
    test_ ## testnum: \
        xor TESTNUMREG, TESTNUMREG, TESTNUMREG; \
        ori TESTNUMREG, TESTNUMREG, testnum; \
        code; \
        lui r13, correctval_u; \
        ori r13, r13, correctval_l; \
        cmp r13, r13, testreg; \
        brs r13, test_good ## testnum; \
        jmp fail; \
    test_good ## testnum: \
        ;

#define TEST_IMM_OP(testnum, inst, result_u, result_l, val1_u, val1_l, imm) \
    TEST_CASE(testnum, r13, result_u, result_l, \
        lui r1, val1_u; \
        ori r1, r1, val1_l; \
        inst r14, r1, imm; \
    )

#endif