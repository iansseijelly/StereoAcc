package stereoacc

import chisel3._
import chisel3.util._

class Circular_ShiftReg(val param: StereoAccParams) extends Module {
    val io = IO(new Bundle {
        val input_data = Input(Vec(param.blockSize, UInt(8.W)))
        val input_valid = Input(Bool())
        val data = Output(Vec(param.blockSize, Vec(param.blockSize, UInt(8.W))))
    })
    val shift_reg = Seq.fill(param.blockSize) (RegInit(VecInit.fill(param.blockSize)(0.U(8.W))))
    when (io.input_valid) {
        shift_reg(0) := io.input_data
        for (i <- 1 until param.blockSize) {
            shift_reg(i) := shift_reg(i-1)
        }
    }
    io.data := shift_reg
}

abstract class AnyPipeModule (val param: StereoAccParams) extends Module {
        val io = IO(new Bundle {
            val w_stationary = new Bundle{
                val data = Input(Vec(param.blockSize, UInt(8.W)))
                val valid = Input(Bool())
            }
            val w_circular = new Bundle{
                val data = Input(Vec(param.blockSize, UInt(8.W)))
                val valid = Input(Bool())
            }
            val output = Decoupled(UInt(8.W)
        ) 
    })

    val stationary_reg = RegInit(VecInit.fill(param.blockSize, param.blockSize)(0.U(8.W)))
    val circular_reg = Module(new Circular_ShiftReg(param))
    circular_reg.io.input_data := io.w_circular.data
    circular_reg.io.input_valid := io.w_circular.valid

    val (s_w_count, s_w_wrap) = Counter(io.w_stationary.valid, param.blockSize)
    val s_w_done = RegInit(false.B)
    when (s_w_wrap) {s_w_done := true.B}

    val (c_w_count, c_w_wrap) = Counter(io.w_circular.valid, param.searchRange + param.blockSize)
    val c_w_done = RegInit(false.B)
    when (c_w_wrap) {c_w_done := true.B}
    val c_full = RegInit(false.B)

    val do_compute = io.w_circular.valid && c_full
    
    when (io.w_stationary.valid){stationary_reg(s_w_count) := io.w_stationary.data}
    
    val c_c_done = RegNext(c_w_done)

    // c must not be full when s is not done
    assert(!(!s_w_done && c_full), "c must not be full when s is not done")
}

class SADPipe(param: StereoAccParams) extends AnyPipeModule(param) {

    val SAD = Wire(UInt(32.W))
    SAD := VecInit.tabulate(param.blockSize*param.blockSize){i => 
        val x = i % param.blockSize
        val y = i / param.blockSize
        val adpe = Module(new EU_ADPE)
        adpe.io.A := stationary_reg(y)(x)
        adpe.io.B := circular_reg.io.data(y)(x)
        adpe.io.AD
    }.reduceTree(_+&_)

    val min_SAD = RegInit(0xFFFFFFFFL.U(32.W))
    val best_offset = RegInit(0.U(8.W))

    when (do_compute && (SAD < min_SAD)) {
        min_SAD := SAD
        best_offset := Mux(c_w_wrap, (param.searchRange-1).U, c_w_count - param.blockSize.U)
    }

    io.output.bits := best_offset
    io.output.valid := c_c_done

    when (io.output.fire){
        // reset all arch state
        min_SAD := 0xFFFFFFFFL.U
        s_w_done := false.B
        c_w_done := false.B
        c_c_done := false.B
        c_full := false.B
        s_w_count := 0.U
        c_w_count := 0.U
    }
}