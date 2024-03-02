package revelio

import chisel3._
import chisel3.util._

// from testchipip/Utils.scala
class SerialWidthSlicer(narrowW: Int, wideW: Int) extends Module {
  require(wideW > narrowW)
  require(wideW % narrowW == 0)
  val io = IO(new Bundle {
    val wide   = Flipped(Decoupled(UInt(wideW.W)))
    val narrow = Decoupled(UInt(narrowW.W))
  })

  val beats = wideW / narrowW
  val wide_beats = RegInit(0.U(log2Ceil(beats).W))
  val wide_last_beat = wide_beats === (beats-1).U

  io.narrow.valid := io.wide.valid
  io.narrow.bits := io.wide.bits.asTypeOf(Vec(beats, UInt(narrowW.W)))(wide_beats)
  when (io.narrow.fire) {
    wide_beats := Mux(wide_last_beat, 0.U, wide_beats + 1.U)
  }
  io.wide.ready := wide_last_beat && io.narrow.ready
}

class SRAMImgBuffer(val nRows: Int, val imgWidth: Int, val imgHeight: Int) extends Module {

    val rWidth : Int = 8
    val wWidth : Int = 32

    val io = IO(new Bundle {
        // write by rows
        val write = Flipped(Decoupled(UInt(32.W)))
        // read by columns
        val read = new Bundle {
            val request = new Bundle {
                val valid = Input(Bool())
                val ready = Output(Bool())
            }
            val response = new Bundle {
                val valid = Output(Bool())
                val bits = Vec(nRows, UInt(rWidth.W))
            }
        }
    })
    val nBanks = nRows + 1 // an extra bank for the write
    val dBanks = imgWidth * 8 / rWidth // depth of each bank in terms of rWidth 

    val s_idle :: s_fill :: s_stable :: Nil = Enum(3)
    val state = RegInit(s_idle)
    val w_col_done = RegInit(false.B)
    val w_row_done = RegInit(false.B)
    val r_col_done = RegInit(false.B)
    io.read.request.ready := state === s_stable
    
    val w_des = Module(new SerialWidthSlicer(narrowW=8, wideW=wWidth))
    w_des.io.wide <> io.write
    // FINE counter tracking the serial width slicer write address
    val (w_col_count, w_col_wrap) = Counter(cond=w_des.io.narrow.fire, n=imgWidth)
    val w_addr = w_col_count
    // enq_ptr is the pointer to the bank that is currently being written to
    val (enq_ptr, _) = Counter(cond=w_col_wrap, n=nBanks)
    // counter tracking the number of rows written
    val (w_row_count, w_row_wrap) = Counter(cond=w_col_wrap, n=imgHeight)

    // FINE counter tracking the read address
    val (r_col_count, r_col_wrap) = Counter(cond=io.read.response.valid, n=imgWidth)
    val (r_row_count, r_row_wrap) = Counter(cond=r_col_wrap, n=imgHeight-nRows)
    val r_addr = r_col_count
    val r_datas = Wire(Vec(nBanks, UInt(rWidth.W)))
    val (deq_ptr, _) = Counter(cond=r_col_wrap, n=nBanks)

    val w_enable = Wire(Vec(nBanks, Bool()))
    val r_enable = Wire(Vec(nBanks, Bool()))
    for (i <- 0 until nBanks) {
        w_enable(i) := i.U === enq_ptr && w_des.io.narrow.fire
        r_enable(i) := state === s_stable && i.U =/= enq_ptr && !r_col_done && io.read.request.valid
    }

    io.read.response.bits := VecInit(0 until nRows map (i => (r_datas(deq_ptr + i.U) % nBanks.U)))
    io.read.response.valid := state === s_stable && RegNext(io.read.request.valid && !r_col_wrap)
    w_des.io.narrow.ready := Mux(state === s_idle || state === s_fill, true.B, 
                                    Mux(state === s_stable, w_col_done && !w_row_done, false.B))    

    // Generate NBanks SRAM Banks
    for (i <- 0 until nBanks) {
        val sram = SyncReadMem(dBanks, UInt(rWidth.W))
        // equivalent to this statement, which is not available in chisel 3.6
        // r_datas(i) := sram.readWrite(idx       = Mux(w_enable(i), w_addr, r_addr), 
        //                              writeData = w_des.io.narrow.bits, 
        //                              en        = w_enable(i) || r_enable(i),
        //                              isWrite   = w_enable(i))
        r_datas(i) := DontCare
        when (w_enable(i) || r_enable(i)){
            val rdwrPort = sram(Mux(w_enable(i), w_addr, r_addr))
            when (w_enable(i)) {rdwrPort := w_des.io.narrow.bits}
            .otherwise {r_datas(i) := rdwrPort}
        }
    }

    // state machine
    switch(state){
        // wait for write
        is(s_idle){
            state := Mux(io.write.fire, s_fill, s_idle)
            // arch state initialization
            enq_ptr := 0.U
            deq_ptr := 0.U
            w_col_done := io.write.fire
            w_row_done := false.B
            r_col_done := false.B
        }

        // fill the banks until nBanks-1 banks are full
        is(s_fill){
            state := Mux(enq_ptr === nRows.U, s_stable, s_fill)
        }

        // write one bank, waiting for one full row read 
        is(s_stable){
            state := Mux(r_row_wrap, s_idle, s_stable)
            when(w_col_wrap) {w_col_done := false.B}
            when(r_col_wrap) {w_col_done := true.B}
            when (w_row_wrap) {w_row_done := true.B}
        }
    }
}