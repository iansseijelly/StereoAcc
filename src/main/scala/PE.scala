package stereoacc

import chisel3._
import chisel3.util._

abstract class Any_ADPE extends Module{
    val io = IO(new Bundle{
        val A = Input(UInt(8.W))
        val B = Input(UInt(8.W))
        val AD = Output(UInt(8.W))
    }) 
}

class EU_ADPE extends Any_ADPE {
    val A_s = Cat(0.U(1.W), io.A).asSInt // 0-extension
    val B_s = Cat(0.U(1.W), io.B).asSInt
    val AB = Wire(SInt(9.W))
    AB := A_s -% B_s // minus without width expansion
    val BA = Wire(SInt(9.W))
    BA := B_s -% A_s
    io.AD := Mux(AB(8), BA(7, 0).asUInt, AB(7, 0).asUInt)
    // assert(io.AD(7) === 0.U) // must be positive
    assert(AB(8) ^ BA(8) || io.A === io.B) // sign must be different
}

// A Mux version of computing AD
class Mux_ADPE extends Any_ADPE {
    val A_ge_B = io.A >= io.B
    val large = Mux(A_ge_B, io.A, io.B)
    val small = Mux(A_ge_B, io.B, io.A)
    io.AD := large - small
}