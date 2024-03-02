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
    val (w_col_count, w_col_done) = Counter(io.write.fire, imgWidth/4)
    // enq_ptr is the pointer to the bank that is currently being written to
    val (enq_ptr, _) = Counter(w_col_done, nBanks)
    // counter tracking the number of rows written
    val (w_row_count, w_row_done) = Counter(w_col_done, imgHeight)
    val w_enable = VecInit((0 until nBanks map (i => i.U === enq_ptr && w_serdes.io.narrow.fire)).toSeq)
    // FINE counter tracking the serial width slicer write address
    val (w_bytes_count, w_bytes_done) = Counter(w_enable(enq_ptr), wWidth / rWidth)
    val w_addr = Wire(UInt(log2Ceil(dBanks).W))
    w_addr := w_bytes_count + w_col_count << 2
    val w_serdes = Module(new SerialWidthSlicer(8, rWidth))
    w_serdes.io.wide <> io.write

    // FINE counter tracking the read address
    val (r_col_count, r_col_done) = Counter(io.read.response.valid, imgWidth)
    val (r_row_count, r_row_done) = Counter(r_col_done, imgHeight - nRows)
    val r_addr = r_col_count
    val r_enable = Vec(nBanks, Bool())
    val r_datas = VecInit(Seq.fill(nRows){Wire(UInt(rWidth.W))})
    val (deq_ptr, _) = Counter(r_col_done, nBanks)

    io.read.response.bits := VecInit(0 until nRows map (i => (r_datas(deq_ptr + i.U) % nBanks.U)))
    io.read.request.ready := state === s_stable
    
    // Generate NBanks SRAM Banks
    for (i <- 0 until nBanks) {
        val sram = SyncReadMem(dBanks, UInt(rWidth.W))
        // equivalent to this statement, which is not available in chisel 3.6
        // r_datas(i) := sram.readWrite(idx       = Mux(w_enable(i), w_addr, r_addr), 
        //                              writeData = w_serdes.io.narrow.bits, 
        //                              en        = w_enable(i) || r_enable(i),
        //                              isWrite   = w_enable(i))
        r_datas(i) := DontCare
        when (w_enable(i) || r_enable(i)){
            val rdwrPort = sram(Mux(w_enable(i), w_addr, r_addr))
            when (w_enable(i)) {rdwrPort := w_serdes.io.narrow.bits}
            .otherwise {r_datas(i) := rdwrPort}
        }
    }

    // state machine
    val s_idle :: s_fill :: s_stable :: Nil = Enum(3)
    val state = RegInit(s_idle)
    switch(state){
        // wait for write
        is(s_idle){
            state := Mux(io.write.fire, s_fill, s_idle)
            w_serdes.io.narrow.ready := true.B
            io.read.response.valid := false.B
            r_enable.foreach(_ := false.B)
        }

        // fill the banks until nBanks-1 banks are full
        is(s_fill){
            state := Mux(enq_ptr === nRows.U, s_stable, s_fill)
            w_serdes.io.narrow.ready := w_bytes_done
            io.read.response.valid := false.B
            r_enable.foreach(_ := false.B)
        }

        // write one bank, waiting for one full row read 
        is(s_stable){
            state := Mux(r_row_done, s_idle, s_stable)
            w_serdes.io.narrow.ready := w_bytes_done && !w_col_done && !w_row_done
            for (i <- 0 until nBanks) {
                r_enable(i) := i.U =/= enq_ptr && !r_col_done && io.read.request.valid
            }
            io.read.response.valid := RegNext(io.read.request.valid && !r_col_done)
        }
    }
}