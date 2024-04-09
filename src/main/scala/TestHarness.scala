package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.util.TestPrefixSums

abstract class InputGen()(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val enq = Decoupled(UInt(32.W))
  })
  def gen_write_data(index: UInt): UInt
  val params = p(StereoAccKey)
  val (test_count, test_wrap) = Counter(io.enq.fire, (2*params.imgWidth*params.imgHeight/4))
  val test_done = RegInit(false.B)
  when (test_wrap) {test_done := true.B}

  io.enq.bits := gen_write_data(test_count<<2)
  io.enq.valid := !test_done

  when (io.enq.fire) {
    test_count := test_count + 1.U
  }
}

class NumericInputGen()(implicit p: Parameters) extends InputGen {
  override def gen_write_data(index: UInt): UInt = {
        val data : UInt = (index&(0xFF.U)|
                          (((index+1.U)&(0xFF.U))<<8)|
                          (((index+2.U)&(0xFF.U))<<16)|
                          (((index+3.U)&(0xFF.U))<<24))
        data
    }
}

class OutputCheck(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val deq = Flipped(Decoupled(UInt(32.W)))
  })

  when (io.deq.valid) {
    printf("Read data: %x %x %x %x\n", io.deq.bits(7,0), io.deq.bits(15,8), io.deq.bits(23,16), io.deq.bits(31,24))
  }
  io.deq.ready := true.B
}

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle { val success = Output(Bool()) })
  val dut = Module(new StereoAcc(p(StereoAccKey)))
  io.success := dut.io.finished
  val inputGen = Module(new NumericInputGen)
  val outputCheck = Module(new OutputCheck)
  inputGen.io.enq <> dut.io.enq
  outputCheck.io.deq <> dut.io.deq
}
