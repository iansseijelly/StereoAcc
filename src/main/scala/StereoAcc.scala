package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters, Field}
import rose.Dataflow
import rose.CompleteDataflowConfig

class StereoAcc(params: StereoAccParams) extends Dataflow(CompleteDataflowConfig(params)) {

    // *** image buffers ***//
    val leftImgBuffer = Module(new SRAMImgBufferExcess(params.blockSize, params.imgWidth, params.imgHeight))
    val rightImgBuffer = Module (new SRAMImgBufferExcess(params.blockSize, params.imgWidth, params.imgHeight))

    // *** pipes ***//
    val pipeio = Seq.fill(params.fuWidth) {
        val SADPipe = Module(new SADPipe(params))
        SADPipe.io
    }

    // *** write arbiter ***//
    // true for right, false for left
    val w_right_sel = RegInit(false.B)
    // this counter tracks when to switch between left and right
    val (w_count, w_wrap) = Counter(io.enq.fire, params.imgWidth/32*8)
    when (w_wrap) {w_right_sel := ~w_right_sel}
    leftImgBuffer.io.write.valid := io.enq.valid && !w_right_sel
    leftImgBuffer.io.write.bits := io.enq.bits
    rightImgBuffer.io.write.valid := io.enq.valid && w_right_sel
    rightImgBuffer.io.write.bits := io.enq.bits
    io.enq.ready := Mux(w_right_sel, rightImgBuffer.io.write.ready, leftImgBuffer.io.write.ready)

    // *** output aggregator ***//
    val des = Module(new SerialWidthAggregator(narrowW=8, wideW=32))
    val (deq_ptr, deq_wrap) = Counter(des.io.narrow.fire, params.fuWidth)
    des.io.narrow.valid := VecInit(pipeio.map(_.output.valid))(deq_ptr)
    des.io.narrow.bits := VecInit(pipeio.map(_.output.bits))(deq_ptr)
    io.deq <> des.io.wide
    pipeio.zipWithIndex.foreach{
        case (o, i) => {
            o.output.ready := Mux(deq_ptr === i.U, des.io.narrow.ready, 0.U)
        }
    }

    // *** compute state machine ***//
    val s_idle :: s_stable :: s_tail :: s_reset :: Nil = Enum(4)
    val state = RegInit(s_idle)

    // the left fine counter tracking the offsets of the reads
    val read_req_fire = rightImgBuffer.io.read.request.ready && rightImgBuffer.io.read.request.valid && leftImgBuffer.io.read.request.ready
    val (l_offset_count, l_offset_wrap) = Counter(read_req_fire, params.blockSize+params.fuWidth+params.searchRange)
    val l_offset_response_count = RegNext(l_offset_count)
    // the left counter tracking how many left images have been read
    val (l_col_count, l_col_wrap) = Counter(l_offset_wrap, params.numIterPerRow)
    val column_done = RegInit(false.B)
    when (l_col_wrap) {column_done := true.B}
    Pulsify(column_done)

    // keeping track of the number of rows operated on
    val (row_count, row_wrap) = Counter(column_done, params.imgHeight-params.blockSize+1)
    val row_done = RegInit(false.B)
    when (row_wrap) {row_done := true.B}

    def do_compute = state === s_stable
    val read_index = l_col_count * params.fuWidth.U +& l_offset_count

    // mux the data into the pipeios
    for (i <- 0 until params.fuWidth) {
        pipeio(i).w_stationary.valid := Mux((i.U<=l_offset_response_count) && (l_offset_response_count<(i+params.blockSize).U),
                                            leftImgBuffer.io.read.response.valid, 0.U)
        pipeio(i).w_stationary.data := leftImgBuffer.io.read.response.bits
        pipeio(i).w_circular.valid := Mux((i.U<=l_offset_response_count) && (l_offset_response_count<(i+params.searchRange+params.blockSize).U),
                                            rightImgBuffer.io.read.response.valid, 0.U)
        pipeio(i).w_circular.data := rightImgBuffer.io.read.response.bits
    }

    // control signals
    leftImgBuffer.io.read.request.index := read_index
    rightImgBuffer.io.read.request.index := read_index
    
    leftImgBuffer.io.read.request.col_done := column_done
    rightImgBuffer.io.read.request.col_done := column_done
    
    leftImgBuffer.io.read.request.valid := do_compute && (l_offset_count < (params.blockSize+params.fuWidth).U)
    rightImgBuffer.io.read.request.valid := do_compute

    switch(state) {
        // wait for both buffers to fill
        is(s_idle) {
            column_done := false.B
            when(leftImgBuffer.io.read.request.ready && rightImgBuffer.io.read.request.ready) {state := s_stable}
        }
        // stably compute
        is(s_stable) {
            when (l_offset_wrap) {state := s_tail}
        }
        // tail state, collect the output
        is(s_tail) {
            // hold the state until the output is collected
            when (deq_wrap) {state := Mux(
                row_done, s_reset, Mux(
                column_done, s_idle, s_stable)
                )
            }
        }
        // this assumes 1-cycle reset
        is (s_reset) {
            // reset the imgbuffers
            leftImgBuffer.reset := true.B
            rightImgBuffer.reset := true.B
            // reset internal state
            l_col_count := 0.U
            row_count := 0.U
            state := s_idle
            // just to be safe
            l_offset_count := 0.U
            deq_ptr := 0.U
            row_done := false.B
        }
    }

    // io.finished := row_wrap
}