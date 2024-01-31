#import "template.typ": *
#import "@preview/wavy:0.1.1"

#show raw.where(lang: "wavy"): it => wavy.render(it.text)

// Take a look at the file `template.typ` in the file panel
// to customize this template and discover how it works.
#show: project.with(
  title: "Sapphire Shader Core ISA",
  authors: (
    "Jesús Sanz del Rey", "Robot",
  ),
  date: "January 31, 2024",
)

// We generated the example code below so you can see how
// your document will look. Go ahead and replace it with
// your own content!

= Introduction
#lorem(60)

#pagebreak()



= Instruction Set

=== OPCode Format
```wavy
{reg: [
  {bits: 8,  name: '', attr: ['8']},
  {bits: 4,  name: 'rs2', attr: ['4', 'src2', 'src2', 'src2', 'src2']},
  {bits: 10,  name: '', attr: ['10']},
  {bits: 4,  name: 'rs1', attr: ['4', 'src1', 'src1', 'src1', 'src1']},
  {bits: 4,  name: 'rd', attr: ['4', 'dest', 'dest', 'dest', 'dest']},
  {bits: 2,  name: 'op', attr: ['2', 'ARITH', 'MEM', 'PC', 'SYSTEM']}
]}
```

=== ARITH Type

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2/imm'},
  {bits: 1,  name: 'li'},
  {bits: 7,  name: 'opcode'},
  {bits: 2,  name: 'simd'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '00'}
]}
```

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2'},
  {bits: 1,  name: '0'},
  {bits: 7,  name: 'opcode'},
  {bits: 2,  name: 'simd'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '00'}
]}
```

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'imm[11:8]'},
  {bits: 1,  name: '1'},
  {bits: 7,  name: 'opcode'},
  {bits: 2,  name: 'simd'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '00'}
]}
```

=== MEM type

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2/imm'},
  {bits: 1,  name: '0'},
  {bits: 4,  name: 'imm[11:8]'},
  {bits: 2,  name: 'opcode'},
  {bits: 1,  name: 'mem'},
  {bits: 2,  name: 'size'},
  {bits: 4,  name: 'reg1/imm'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '01'}
]}
```

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2'},
  {bits: 1,  name: '0'},
  {bits: 4,  name: 'imm[11:8]'},
  {bits: 2,  name: 'opcode'},
  {bits: 1,  name: '0'},
  {bits: 2,  name: 'size'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '01'}
]}
```

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'imm[15:12]'},
  {bits: 1,  name: '0'},
  {bits: 4,  name: 'imm[11:8]'},
  {bits: 2,  name: 'opcode'},
  {bits: 1,  name: '1'},
  {bits: 2,  name: 'size'},
  {bits: 4,  name: 'imm[19:16]'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '01'}
]}
```

=== PC Type

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2'},
  {bits: 8,  name: 'imm[15:8]'},
  {bits: 2,  name: 'opcode'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '10'}
]}
```

=== SYSTEM Type

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2/imm'},
  {bits: 1,  name: 'li'},
  {bits: 9,  name: 'opcode'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '11'}
]}
```

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'reg2'},
  {bits: 1,  name: '0'},
  {bits: 9,  name: 'opcode'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '11'}
]}
```

```wavy
{reg: [
  {bits: 8,  name: 'imm[7:0]'},
  {bits: 4,  name: 'imm[11:8]'},
  {bits: 1,  name: '1'},
  {bits: 9,  name: 'opcode'},
  {bits: 4,  name: 'reg1'},
  {bits: 4,  name: 'dest'},
  {bits: 2,  name: '11'}
]}
```
