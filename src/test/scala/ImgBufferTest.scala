package revelio

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ImgBufferTest extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "ImgBuffer" 
    it should "instantiate SRAMImgBuffer" in {
        test(new SRAMImgBuffer(nRows=4, imgWidth=32, imgHeight=32)) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }
}
