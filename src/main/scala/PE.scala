package stereoacc

import chisel3._
import chisel3.util._

class Any_ADPE extends Module{
    val io = IO(new Bundle{
        val A = Input(UInt(8.W))
        val B = Input(UInt(8.W))
        val AD = Output(UInt(8.W))
    }) 
}

// An EU patent average difference
class EU_ADPE extends Any_ADPE {
    val A_s = io.A.asSInt
    val B_s = io.B.asSInt
    val AB = Wire(SInt(8.W))
    AB := A_s -% B_s // without width expansion
    val BA = Wire(SInt(8.W))
    BA := B_s -% A_s
    io.AD := Mux(AB(7), BA, AB).asUInt
    assert(io.AD(7) === 0.U) // must be positive
}

// A Mux version of computing AD
class Mux_ADPE extends Any_ADPE {
    val A_ge_B = io.A >= io.B
    val large = Mux(A_ge_B, io.A, io.B)
    val small = Mux(A_ge_B, io.B, io.A)
    io.AD := large - small
}