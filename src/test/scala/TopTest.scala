package revelio

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class TopTest extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "StereoAcc"
    it should "instantiate StereoAcc" in {
        test(new StereoAcc(RevelioParams())) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }
}