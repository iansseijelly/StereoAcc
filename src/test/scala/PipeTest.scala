package revelio

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PipeTest extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "Pipe"
    it should "instantiate SADPipe" in {
        test(new SADPipe(RevelioParams())) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }
}