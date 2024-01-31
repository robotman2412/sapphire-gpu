addi r0, r1, 400
addi r1, r1, 600
add r1, r0, r1
test_label:
    add r1, r0, r2
    jmp r15, test_label
