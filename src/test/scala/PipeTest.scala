package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PipeTest extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "Pipe"
    it should "instantiate SADPipe" in {
        test(new SADPipe(StereoAccParams())) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }

    it should "compute SAD" in {
        val test_blocksize = 4
        val test_imgWidth = 64
        val searchRange = 8
        test (new SADPipe(StereoAccParams(blockSize = test_blocksize, searchRange = searchRange))).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (i <- 0 until test_blocksize) {
                for (j <- 0 until test_blocksize) {
                    c.io.w_stationary.data(j).poke(i.U)
                    c.io.w_circular.data(j).poke(0.U)
                }
                c.io.w_circular.valid.poke(true.B)
                c.io.w_stationary.valid.poke(true.B)
                c.clock.step()
            }
            c.io.w_stationary.valid.poke(false.B)
            c.io.w_circular.valid.poke(false.B)
            c.clock.step()
            for (i <- 0 until searchRange){
                for (j <- 0 until test_blocksize){
                    c.io.w_circular.data(j).poke(i.U)
                }
                c.io.w_circular.valid.poke(true.B)
                c.clock.step()
            }
            c.io.w_circular.valid.poke(false.B)
            c.clock.step()
            c.io.output.valid.expect(true.B)
            c.io.output.ready.poke(true.B)
            c.clock.step()
            c.io.output.valid.expect(false.B)
        }
    }
}