package revelio

import chisel3._
import chisel3.util._
import freechips.rocketchip.util.RotateVector

class StereoAcc(params: RevelioParams) extends Module {
    val io = IO(new Bundle {
        val enq = Flipped(Decoupled(UInt(32.W)))
        val deq = Decoupled(UInt(32.W))
    })

    // *** image buffers ***//
    val leftImgBuffer = Module(new SRAMImgBuffer(params.blockSize, params.imgWidth, params.imgHeight))
    val rightImgBuffer = Module (new SRAMImgBuffer(params.blockSize, params.imgWidth, params.imgHeight))

    // *** pipes ***//
    val pipebundle = new Bundle{
        val w_stationary = new Bundle{
            val data = Input(Vec(params.blockSize, UInt(8.W)))
            val valid = Input(Bool())
        }
        val w_circular = new Bundle{
            val data = Input(Vec(params.blockSize, UInt(8.W)))
            val valid = Input(Bool())
        }
        val output = Decoupled(UInt(8.W))
    }
    val pipeio = VecInit(Seq.fill(params.fuWidth)(Wire(pipebundle)))

    for (i <- 0 until params.fuWidth) {
        val SADPipe = Module(new SADPipe(params))
        SADPipe.io.w_stationary <> pipeio(i).w_stationary
        SADPipe.io.w_circular <> pipeio(i).w_circular
        pipeio(i).output <> SADPipe.io.output
    }

    // *** write arbiter ***//
    // true for right, false for left
    val w_right_sel = RegInit(false.B)
    // this counter tracks when to switch between left and right
    val (w_count, w_wrap) = Counter(io.enq.fire, params.imgWidth*32/8)
    when (w_wrap) {w_right_sel := ~w_right_sel}
    leftImgBuffer.io.write.valid := io.enq.valid && !w_right_sel
    leftImgBuffer.io.write.bits := io.enq.bits
    rightImgBuffer.io.write.valid := io.enq.valid && w_right_sel
    rightImgBuffer.io.write.bits := io.enq.bits
    io.enq.ready := Mux(w_right_sel, rightImgBuffer.io.write.ready, leftImgBuffer.io.write.ready)

    // *** output aggregator ***//
    val des = Module(new SerialWidthAggregator(narrowW=8, wideW=32))
    val (deq_ptr, deq_wrap) = Counter(des.io.narrow.fire, params.fuWidth)
    des.io.narrow.valid := pipeio(deq_ptr).output.valid
    des.io.narrow.bits := pipeio(deq_ptr).output.bits
    io.deq <> des.io.wide
    pipeio.zipWithIndex.foreach{
        case (o, i) => {
            o.output.ready := Mux(deq_ptr === i.U, des.io.narrow.ready, 0.U)
        }
    }

    // *** compute state machine ***//
    val s_idle :: s_fill :: s_stable :: s_tail :: Nil = Enum(4)
    val state = RegInit(s_idle)

    // the left fine counter tracking the offsets of the reads
    val (l_offset_count, l_offset_wrap) = Counter(do_read, params.blockSize)
    // the left counter tracking how many left images have been read
    val (l_col_count, l_col_wrap) = Counter(l_offset_wrap, params.numIterPerRow)
    // the right counter tracking the offsets of the right images
    val (r_offset_count, r_offset_wrap) = Counter(do_compute, params.searchRange)

    def do_read = state === s_fill
    def do_compute = state === s_stable
    val read_index = Mux(do_compute, l_col_count + params.blockSize.U + r_offset_count, 
                        Mux(do_read, l_col_count + l_offset_count, 0.U))

    // mux the data into the pipeios
    for (i <- 0 until params.fuWidth) {
        pipeio(i).w_stationary.valid := Mux(l_offset_count >= i.U, leftImgBuffer.io.read.response.valid && do_read, 0.U)
        pipeio(i).w_stationary.data := leftImgBuffer.io.read.response.bits
        pipeio(i).w_circular.valid := Mux((do_read && l_offset_count >= i.U) || do_compute, rightImgBuffer.io.read.response.valid, 0.U)
        pipeio(i).w_circular.data := rightImgBuffer.io.read.response.bits
    }

    // control signals
    leftImgBuffer.io.read.request.index := read_index
    rightImgBuffer.io.read.request.index := read_index
    
    leftImgBuffer.io.read.request.col_done := state === s_tail
    rightImgBuffer.io.read.request.col_done := state === s_tail
    
    leftImgBuffer.io.read.request.valid := do_read
    rightImgBuffer.io.read.request.valid := do_read || do_compute

    switch(state) {
        // wait for both buffers to fill
        is(s_idle) {
            when(leftImgBuffer.io.read.request.ready && rightImgBuffer.io.read.request.ready) {state := s_fill}
        }
        // fill in the pipes
        is(s_fill) {
            when (l_offset_wrap) {state := s_stable}
        }
        // stably compute
        is(s_stable) {
            when (r_offset_wrap) {state := Mux(l_col_wrap, s_tail, s_fill)}
        }
        // tail state, collect the output
        is(s_tail) {
            // hold the state until the output is collected
            when (deq_wrap) {state := s_idle}
        }
    }
}