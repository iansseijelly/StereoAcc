package revelio

import chisel3._
import chisel3.util._

class SRAMImgBuffer(val n: Int, val w: Int) extends Module {
    val io = IO(new Bundle {
        // write by rows
        val write = Flipped(Decoupled(UInt(32.W)))
        // read by columns
        val read = Decoupled(UInt(n.W))
    })

    // Generate N SRAM Banks
    val sram_banks = VecInit(Seq.fill(n){SyncReadMem(w, UInt(8.W))})
    val valids = RegInit(VecInit(Seq.fill(n){false.B}))
    val w_idx = RegInit(0.U(log2Ceil(n).W))

    // write state machine
    
}


