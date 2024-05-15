package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters, Field}
import rose.Dataflow
import rose.CompleteDataflowConfig
import freechips.rocketchip.regmapper.RegField.w
import freechips.rocketchip.regmapper.RegField.r

class Pool2D(params: Pool2DParams) extends Dataflow(CompleteDataflowConfig(params)) {

    // *** image buffers ***//
    val imgBuffer0 = Module(new SRAMImgBuffer(params.blockSize, params.imgWidth, params.imgHeight))
    val imgBuffer1 = Module(new SRAMImgBuffer(params.blockSize, params.imgWidth, params.imgHeight))

    // *** write arbiter ***//
    // false for 0, true for 1
    val w_bufferPtr = RegInit(false.B)
    val r_bufferPtr = RegInit(false.B)
    imgBuffer0.io.write.bits := io.enq.bits
    imgBuffer0.io.write.valid := io.enq.valid && !w_bufferPtr
    imgBuffer1.io.write.bits := io.enq.bits
    imgBuffer1.io.write.valid := io.enq.valid && w_bufferPtr
    io.enq.ready := Mux(w_bufferPtr, imgBuffer1.io.write.ready, imgBuffer0.io.write.ready)

    val read_response_bits = Mux(r_bufferPtr, imgBuffer1.io.read.response.bits, imgBuffer0.io.read.response.bits)
    val read_response_valid = Mux(r_bufferPtr, imgBuffer1.io.read.response.valid, imgBuffer0.io.read.response.valid)
    val read_request_ready = Mux(r_bufferPtr, imgBuffer1.io.read.request.ready, imgBuffer0.io.read.request.ready)

    // *** state machine ***//
    val s_idle :: s_stable :: Nil = Enum(2)
    val state = RegInit(s_idle)

    def do_read = state === s_stable
    def do_write = RegNext(state === s_stable, false.B)
    // 1 delay for req->resp, and 1 delay for resp->changing register value
    def do_compute = RegNext(RegNext(state === s_stable, false.B))

    // *** functional unit ***//
    val (w_count, w_wrap) = Counter(io.enq.fire, params.imgWidth*2/4)
    val (r_count, r_wrap) = Counter(do_read, params.imgWidth)
    val (reg_w_count, reg_w_wrap) = Counter(do_write, params.imgWidth/4)
    val (comp_count, comp_wrap) = Counter(do_compute, params.blockSize)
    val (enq_count, enq_wrap) = Counter(io.enq.fire, params.imgWidth/4)

    val stationary_reg = RegInit(VecInit.fill(params.blockSize, params.blockSize)(0.U(8.W)))
    for (i <- 0 until params.blockSize) {
        when (read_response_valid) {
            stationary_reg(i)(reg_w_count) := read_response_bits(i)
        }
    }

    when (w_wrap) {w_bufferPtr := ~w_bufferPtr}

    val result = Wire(UInt(8.W))

    if (params.reduction == "max") {
        val max = VecInit.tabulate(params.blockSize*params.blockSize){ i => 
            val x = i / params.blockSize
            val y = i % params.blockSize
            stationary_reg(y)(x)
        }.reduceTree((a, b) => Mux(a > b, a, b))
        result := max
    } else if (params.reduction == "avg") {
        val sum = VecInit.tabulate(params.blockSize*params.blockSize){ i => 
            val x = i / params.blockSize
            val y = i % params.blockSize
            stationary_reg(y)(x)
        }.reduceTree(_+&_)
        result := sum / (params.blockSize*params.blockSize).U
    }

    // *** output aggregator ***//
    val des = Module(new SerialWidthAggregator(narrowW=8, wideW=32))
    val (deq_count, deq_wrap) = Counter(des.io.narrow.fire, params.imgWidth)
    des.io.narrow.valid := comp_wrap
    des.io.narrow.bits := result
    io.deq <> des.io.wide

    val read_index = r_count

    imgBuffer0.io.read.request.index := read_index
    imgBuffer1.io.read.request.index := read_index

    imgBuffer0.io.read.request.valid := do_read && ~r_bufferPtr
    imgBuffer1.io.read.request.valid := do_read && r_bufferPtr

    val read_done = RegInit(false.B)
    when (state === s_stable && r_wrap) {read_done := true.B}
    Pulsify(read_done)

    when (r_wrap) {r_bufferPtr := ~r_bufferPtr}
    
    imgBuffer0.io.read.done := Mux(r_bufferPtr, read_done, false.B)
    imgBuffer1.io.read.done := Mux(~r_bufferPtr, read_done, false.B)
    
    switch(state) {
        // wait until the buffer is ready
        is(s_idle) {
            when(read_request_ready) {
                state := s_stable
            }
        }
        // stably compute
        is(s_stable) {
            when(r_wrap) {
                state := s_idle
            }
        }
    } 
}
