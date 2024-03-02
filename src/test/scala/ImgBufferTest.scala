package revelio

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ImgBufferTest extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "ImgBuffer" 
    it should "instantiate SRAMImgBuffer" in {
        test(new SRAMImgBuffer(4, 32, 32)) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }
}
