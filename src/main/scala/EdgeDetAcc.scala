package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters, Field}
import rose.{Dataflow, CompleteDataflowConfig}

class EdgeDetAcc(params: EdgeDetAccParams) extends Dataflow(CompleteDataflowConfig(params)) {
    // *** image buffer ***//
    val imgBuffer = Module(new SRAMImgBufferExcess(params.blockSize, params.imgWidth, params.imgHeight))
    imgBuffer.io.write <> io.enq

    val pipe = Module(new SobelConvPipe(params))
    val des = Module(new SerialWidthAggregator(narrowW=8, wideW=32))
    des.io.wide <> io.deq

    val s_idle :: s_stable :: s_tail :: s_reset :: Nil = Enum(4)
    val state = RegInit(s_idle)

    val (r_col_count, r_col_wrap) = Counter(io.enq.fire, params.imgWidth/32*8)
    val column_done = RegInit(false.B)
    when (r_col_wrap) {column_done := true.B}
    Pulsify(column_done)
    val (r_row_count, r_row_wrap) = Counter(column_done, params.imgHeight-params.blockSize+1)

    def do_compute = state === s_stable

    imgBuffer.io.read.request.valid := do_compute
    imgBuffer.io.read.request.col_done := column_done
    imgBuffer.io.read.request.index := r_col_count

    pipe.io.w_circular.data := imgBuffer.io.read.response.bits
    pipe.io.w_circular.valid := imgBuffer.io.read.response.valid
    pipe.io.done := column_done
    pipe.io.output <> des.io.narrow

    switch (state) {
        is(s_idle) {
            column_done := false.B
            when(imgBuffer.io.read.request.ready) {state := s_stable}
        }
        is(s_stable) {
            when(r_row_wrap) {state := s_reset}
        }
        is(s_reset) {
            imgBuffer.reset := true.B
            state := s_idle
        }
    }
}