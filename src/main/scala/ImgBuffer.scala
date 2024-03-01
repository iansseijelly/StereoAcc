package revelio

import chisel3._
import chisel3.util._
import testchipip.serdes.SerialWidthSlicer

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
    require(rWidth % 32 == 0, "The read width must be a multiple of 32")
    val nBanks = nRows + 1 // an extra bank for the write
    val dBanks = imgWidth * 8 / rWidth // depth of each bank in terms of rWidth 
    
    // COARSE counter tracking the number of columns writes for this row
    val (w_col_count, w_col_done) = Counter(io.write.fire, 0 until imgWidth by 4)
    // enq_ptr is the pointer to the bank that is currently being written to
    val (enq_ptr, _) = Counter(w_col_done, nBanks)
    // counter tracking the number of rows written
    val (w_row_count, w_row_done) = Counter(w_col_done, imgHeight)
    val w_enable = VecInit((0 until nBanks map (i => i.U === enq_ptr && w_serdes.io.narrow.fire)).toSeq)
    // FINE counter tracking the serial width slicer write address
    val (w_bytes_count, w_bytes_done) = Counter(w_enable(enq_ptr), wWidth / rWidth)
    val w_addr = Wire(UInt(log2Ceil(dBanks).W))
    val w_serdes = Module(new SerialWidthSlicer(8, rWidth))
    w_serdes.io.wide <> io.write

    // FINE counter tracking the read address
    val (r_col_count, r_col_done) = Counter(io.read.response.valid, imgWidth)
    val r_addr = Wire(UInt(log2Ceil(dBanks).W))
    val r_enable = Vec(nBanks, Bool())
    val r_datas = VecInit(Seq.fill(nRows){Wire(UInt(rWidth.W))})
    val (deq_ptr, _) = Counter(r_col_done, nBanks)

    io.read.response.bits:= VecInit(0 until nRows map (i => (r_datas(deq_ptr + i)%nBanks).U))
    io.read.request.ready := state === s_stable || state === s_tail
    // Generate NBanks SRAM Banks
    val sram_banks = VecInit(Seq.fill(nBanks){SyncReadMem(dBanks, UInt(rWidth.W))})
    foreach (sram_banks.zipWithIndex) { case (sram, i) =>
        r_datas(i) := sram.readWrite(idx       = Mux(w_enable(i), w_addr, r_addr), 
                                     writeData = w_serdes.io.narrow.bits, 
                                     en        = w_enable(i) || r_enable(i),
                                     isWrite   = w_enable(i))}

    // state machine
    val s_idle :: s_fill :: s_stable :: s_tail :: Nil = Enum(4)

    val state = RegInit(s_idle)
    switch(state){
        // wait for write
        is(s_idle){
            state := Mux(io.write.fire, s_fill, s_idle)
            w_serdes.io.narrow.ready := true.B
            io.read.response.valid := false.B
        }

        // fill the banks until nBanks-1 banks are full
        is(s_fill){
            state := Mux(enq_ptr === nRows.U, s_stable, s_fill)
            w_serdes.io.narrow.ready := w_bytes_done
            w_addr := w_bytes_count + w_col_count << 2
            io.read.response.valid := false.B
        }

        // write one bank, waiting for one full row read 
        is(s_stable){
            state := Mux(w_row_count === imgHeight.U, s_tail, s_stable)
            w_serdes.io.narrow.ready := w_bytes_done && !w_col_done
            for (i <- 0 until nBanks) {
                r_enable(i) := i.U =/= enq_ptr && !r_col_done && io.read.request.valid
            }
            r_addr := r_col_count
            io.read.response.valid := RegNext(io.read.request.valid && !r_col_done)
        }

        // tail case, reject writes and only perform reads
        is(s_tail){
            w_serdes.io.narrow.ready := false.B
            state := Mux(r_col_done, s_idle, s_tail)
            for (i <- 0 until nBanks) {
                r_enable(i) := i.U =/= enq_ptr && !r_col_done && io.read.request.valid
            }
            r_addr := r_col_count
            io.read.response.valid := RegNext(io.read.request.valid && !r_col_done)
        }
    }
}


