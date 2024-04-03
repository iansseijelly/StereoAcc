package revelio

import chisel3._
import chisel3.util._

class pipebundle (val param: RevelioParams) extends Bundle {
    val w_stationary = new Bundle{
        val data = Input(Vec(param.blockSize, UInt(8.W)))
        val valid = Input(Bool())
    }
    val w_circular = new Bundle{
        val data = Input(Vec(param.blockSize, UInt(8.W)))
        val valid = Input(Bool())
    }
    val output = Decoupled(UInt(8.W))
}

abstract class AnyPipeModule (val param: RevelioParams) extends Module {
    val io = IO(new pipebundle(param))

    val stationary_reg = RegInit(VecInit.fill(param.blockSize, param.blockSize)(0.U(8.W)))
    val circular_reg = RegInit(VecInit.fill(param.blockSize, param.blockSize)(0.U(8.W)))

    val (s_w_count, s_w_wrap) = Counter(io.w_stationary.valid, param.blockSize)
    val s_w_done = RegInit(false.B)

    val (c_enq_ptr, c_enq_wrap) = Counter(io.w_circular.valid, param.blockSize)
    val (c_w_count, c_w_wrap) = Counter(io.w_circular.valid, param.searchRange + param.blockSize)
    val c_done = RegInit(false.B)
    val c_full = RegInit(false.B)

    val do_compute = RegNext(io.w_circular.valid && c_full)

    when (io.w_stationary.valid){stationary_reg(s_w_count) := io.w_stationary.data}
    when (s_w_wrap) {s_w_done := true.B}
    
    when (io.w_circular.valid) {circular_reg(c_enq_ptr) := io.w_circular.data}
    when (c_enq_wrap) {c_full := true.B}
    when (c_w_wrap) {c_done := true.B}

    // c must not be full when s is not done
    assert(!(!s_w_done && c_full), "c must not be full when s is not done")
}

class SADPipe(param: RevelioParams) extends AnyPipeModule(param) {

    val SAD = Wire(UInt(32.W))
    SAD := VecInit.tabulate(param.blockSize*param.blockSize){i => 
        val x = i % param.blockSize
        val y = i / param.blockSize
        val adpe = Module(new EU_ADPE)
        adpe.io.A := stationary_reg(y)(x)
        adpe.io.B := circular_reg(y)(x)
        val ad = adpe.io.AD
        ad
    }.reduceTree(_+&_)

    val min_SAD = RegInit(0xFFFFFFFFL.U(32.W))
    val best_offset = RegInit(0.U(8.W))

    when (do_compute && (SAD < min_SAD)) {
        min_SAD := SAD
        best_offset := Mux(c_w_wrap, (param.searchRange-1).U, c_w_count - (param.blockSize+1).U)
    }

    io.output.bits := best_offset
    io.output.valid := c_done

    when (io.output.fire){
        // reset all arch state
        min_SAD := 0xFFFFFFFFL.U
        s_w_done := false.B
        c_done := false.B
        c_full := false.B
        s_w_count := 0.U
        c_enq_ptr := 0.U
        c_w_count := 0.U
    }
}