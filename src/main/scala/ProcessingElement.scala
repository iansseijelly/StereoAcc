package revelio

import chisel3._
import chisel3.util._

abstract class AnyPEModule (val param: RevelioParams) extends Module {
    val io = IO(new Bundle {
        val w_stationary = new Bundle{
            val data = Input(UInt((param.blockSize*8).W)) //write one column
            val valid = Input(Bool())
        }
        val w_circular = new Bundle{
            val data = Output(Vec(param.blockSize, UInt(8.W)))
            val valid = Input(Bool())
        }
        val output = new Bundle{
            val data = Output(UInt(8.W)) //read one column
            val valid = Output(Bool())
        }
    })

    val stationary_reg = RegInit(VecInit.fill(param.blockSize, param.blockSize)(0.U(8.W)))
    val circular_reg = RegInit(VecInit.fill(param.blockSize, param.blockSize)(0.U(8.W)))

    val (s_w_count, s_w_wrap) = Counter(io.w_stationary.valid, param.blockSize)
    val s_w_done = RegInit(false.B)

    val (c_enq_ptr, _) = Counter(io.w_circular.valid, param.blockSize)

    when (io.w_stationary.valid) {
        for (i <- 0 until param.blockSize) {
            stationary_reg(i)(s_w_count) := io.w_stationary.data((i+1)*8-1, i*8)
        }
    }
    when (s_w_wrap) {s_w_done := true.B}

    when (io.w_circular.valid) {
        circular_reg(c_enq_ptr) := io.w_circular.data
    }
    
}
class SADPE(param: RevelioParams) extends AnyPEModule(param) {

}