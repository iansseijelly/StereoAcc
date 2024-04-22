package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters

import java.io.File
import java.io.FileReader
import java.io.FileInputStream

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

// the most simple input generator
// Expect to see all 0x00s
class NumericInputGen()(implicit p: Parameters) extends InputGen {
  override def gen_write_data(index: UInt): UInt = {
        val data : UInt = (index&(0xFF.U)|
                          (((index+1.U)&(0xFF.U))<<8)|
                          (((index+2.U)&(0xFF.U))<<16)|
                          (((index+3.U)&(0xFF.U))<<24))
        data
    }
}

// an input generator that has different data for left and right images
// Expect to see non-zero outputs
class LeftRightInputGen()(implicit p: Parameters) extends InputGen {
  override def gen_write_data(index: UInt): UInt = {
    val l_data = (index&(0xFF.U)|
                     (((index+1.U)&(0xFF.U))<<8)|
                     (((index+2.U)&(0xFF.U))<<16)|
                     (((index+3.U)&(0xFF.U))<<24))
    val r_data = (index&(0xFF.U)<<27|
                      (((index+1.U)&(0xFF.U)<<13))|
                      (((index+2.U)&(0xFF.U)<<4)|
                      (((index+3.U)&(0xFF.U)))))
    Mux(index(2), r_data, l_data)
  }
}

// an input generator that reads in an image
class ImageInputGen(val img_name: String)(implicit p: Parameters) extends InputGen {
  
  def u_convert(x: Byte): Int = {
    if (x < 0) x + 256 else x
  }

  // call python helper script to convert image to binary
  override def gen_write_data(index: UInt): UInt = {
    val imgWidth = params.imgWidth
    val imgHeight = params.imgHeight

    // NOTE: pwd is chipyard top if running chiseltest
    val w_command = s"python3 generators/stereoacc/src/test/utils/util_write.py --left generators/stereoacc/src/test/img/${img_name}_left.png --right generators/stereoacc/src/test/img/${img_name}_right.png --imgWidth ${imgWidth.toString} --imgHeight ${imgHeight.toString} --min_disp 0 --max_disp ${params.searchRange.toString} --block_size ${params.blockSize.toString}"
    println("Executing: " + w_command)
    val write_process = Runtime.getRuntime().exec(w_command)

    // read the generated intermediate representation
    val l_file = new File(s"generators/stereoacc/src/test/utils/intermediate/left_matrix")
    val l_data = new Array[Byte](imgHeight*imgWidth)
    val l_file_reader = new FileInputStream(l_file)
    l_file_reader.read(l_data)
    l_file_reader.close()
    val l_data_reg = (VecInit(l_data.toSeq.map(x => u_convert(x).U)))

    val r_file = new File(s"generators/stereoacc/src/test/utils/intermediate/right_matrix")
    val r_data = new Array[Byte](imgHeight*imgWidth)
    val r_file_reader = new FileInputStream(r_file)
    r_file_reader.read(r_data)
    r_file_reader.close()
    val r_data_reg = (VecInit(r_data.toSeq.map(x => u_convert(x).U)))

    Mux(index < (2*imgWidth*imgHeight).U,
      Mux(index%(2*imgWidth).U < imgWidth.U, // choose left or right
      Cat(l_data_reg(index), l_data_reg(index+1.U), l_data_reg(index+2.U), l_data_reg(index+3.U)),
      Cat(r_data_reg(index-imgWidth.U), r_data_reg(index-(imgWidth+1).U), r_data_reg(index-(imgWidth+2).U), r_data_reg(index-(imgWidth+3).U))),
    0.U)
  }
}

class OutputCheck(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val deq = Flipped(Decoupled(UInt(32.W)))
  })

  when (io.deq.valid) {
    printf("%x %x %x %x\n", io.deq.bits(7,0), io.deq.bits(15,8), io.deq.bits(23,16), io.deq.bits(31,24))
  }

  dontTouch(io.deq)
  io.deq.ready := true.B
}

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle { val success = Output(Bool()) })
  val dut = Module(new StereoAcc(p(StereoAccKey)))
  val inputGen = Module(new NumericInputGen)
  val outputCheck = Module(new OutputCheck)
  inputGen.io.enq <> dut.io.enq
  outputCheck.io.deq <> dut.io.deq
}

class ImgTestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle { val success = Output(Bool()) })
  val dut = Module(new StereoAcc(p(StereoAccKey)))
  val inputGen = Module(new ImageInputGen("cones"))
  val outputCheck = Module(new OutputCheck)
  inputGen.io.enq <> dut.io.enq
  outputCheck.io.deq <> dut.io.deq
}