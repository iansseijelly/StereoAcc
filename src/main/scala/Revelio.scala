package revelio

import chisel3._
import chisel3.util._

class Revelio(params: RevelioParams) extends Module {
    val io = IO(new Bundle {
        val enq = Flipped(Decoupled(UInt(32.W)))
        val deq = Decoupled(UInt(32.W))
    })

    // *** image buffers ***//
    val leftImgBuffer = new SRAMImgBuffer(params.blockSize, params.imgWidth, params.imgHeight)
    val rightImgBuffer = new SRAMImgBuffer(params.blockSize, params.imgWidth, params.imgHeight)

    // *** pipes ***//
    val pipe_w_stationary_data = Vec(params.fuWidth, Vec(params.blockSize, UInt(8.W)))
    val pipe_w_stationary_valid = Vec(params.fuWidth, Bool())
    val pipe_w_circular_data = Vec(params.fuWidth, Vec(params.blockSize, UInt(8.W)))
    val pipe_w_circular_valid = Vec(params.fuWidth, Bool())
    val pipe_output = Vec(params.fuWidth, Decoupled(UInt(32.W)))   

    for (i <- 0 until params.fuDepth) {
        val SADPipe = Module(new SADPipe(params))
        SADPipe.io.w_stationary.data := pipe_w_stationary_data(i)
        SADPipe.io.w_stationary.valid := pipe_w_stationary_valid(i)
        SADPipe.io.w_circular.data := pipe_w_circular_data(i)
        SADPipe.io.w_circular.valid := pipe_w_circular_valid(i)
        pipe_output(i) <> SADPipe.io.output
    }

    // *** arbiter ***//
    // true for right, false for left
    val w_choice = RegInit(false.B)
    val (w_count, w_wrap) = Counter(io.enq.fire, params.imgWidth*32/8)
    when (w_wrap) {w_choice := ~w_choice}
    leftImgBuffer.io.write.valid := io.enq.valid && !w_choice
    leftImgBuffer.io.write.bits := io.enq.bits
    rightImgBuffer.io.write.valid := io.enq.valid && w_choice
    rightImgBuffer.io.write.bits := io.enq.bits
    io.enq.ready := Mux(w_choice, rightImgBuffer.io.write.ready, leftImgBuffer.io.write.ready)

    // *** compute state machine ***//
    val s_idle :: s_fill :: s_stable :: Nil = Enum(3)
    val state = RegInit(s_idle)
    // the outer loop counter tracking the left block
    // FIXME: +1 here?
    val (left_col_count, left_col_wrap) = Counter(pipe_output.valid.reduceTree(_&_), params.imgWidth-params.searchRange)
    // the inner loop counter tracking the right block
    val (right_col_count, right_col_done) = Counter(_, params.blockSize)
}