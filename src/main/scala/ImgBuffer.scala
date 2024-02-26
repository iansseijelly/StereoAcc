package revelio

import chisel3._
import chisel3.util._

class SRAMImgBuffer(val nRows: Int, val rWidth: Int, val imgWidth: Int, val imgHeight: Int) extends Module {
    val io = IO(new Bundle {
        // write by rows
        val write = Flipped(Decoupled(UInt(32.W)))
        // read by columns
        val read = Decoupled(UInt(rWidth.W))
    })
    require(rWidth % 32 == 0, "The read width must be a multiple of 32")
    val nBanks = nRows + 1 // an extra bank for the write
    val dBanks = imgWidth * 8 / rWidth // depth of each bank in terms of rWidth 
    val b_valids = RegInit(VecInit(Seq.fill(nBanks){false.B}))
    val w_enable = RegInit(VecInit(Seq.fill(nBanks){false.B}))
    val r_enable = RegInit(VecInit(Seq.fill(nBanks){false.B}))

    val enq_ptr = RegInit(0.U(log2Ceil(nBanks).W))
    val deq_ptr = RegInit(0.U(log2Ceil(nBanks).W))

    val w_idx = RegInit(0.U(log2Ceil(nBanks).W))
    val w_addr = RegInit(0.U(log2Ceil(w).W))
    val r_addr = RegInit(0.U(log2Ceil(w).W)) 

    // Generate NBanks SRAM Banks
    val sram_banks = VecInit(Seq.fill(nBanks){SyncReadMem(dBanks, UInt(rWidth.W))})
    foreach (sram_banks.zipWithIndex) { case (sram, i) =>
        sram.readWrite()
    }
    io.write.ready := valids.reduce(_&&_)

    // state machine
    val s_idle :: s_fill :: s_stable :: s_tail :: Nil = Enum(4)
}


