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

    it should "compute SAD" in {
        val test_blocksize = 8
        val test_imgWidth = 64
        test (new SADPipe(RevelioParams(blockSize = test_blocksize))) { c =>
            for (i <- 0 until test_blocksize) {
                for (j <- 0 until test_blocksize) {
                    c.io.w_stationary.data(j).poke(1.U)
                    c.io.w_circular.data(j).poke(0.U)
                }
                c.io.w_circular.valid.poke(true.B)
                c.io.w_stationary.valid.poke(true.B)
                c.clock.step()
            }
            c.io.w_stationary.valid.poke(false.B)
        }
    }
}