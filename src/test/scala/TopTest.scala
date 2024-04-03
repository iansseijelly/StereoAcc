package revelio

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class TopTest extends AnyFlatSpec with ChiselScalatestTester {

    val BLOCKSIZE: Int = 4
    val IMGWIDTH: Int = 32
    val IMGHEIGHT: Int = 32
    val SEARCHRANGE: Int = 8
    
    def gen_write_data(index: BigInt): BigInt = {
        val data : BigInt = (index%256|(((index+1)%256)<<8)|(((index+2)%256)<<16)|(((index+3)%256)<<24))
        data
    }

    behavior of "StereoAcc"
    it should "instantiate StereoAcc" in {
        test(new StereoAcc(RevelioParams())) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }

    it should "do some computation" in {
        test (new StereoAcc(RevelioParams(blockSize = BLOCKSIZE, imgWidth = IMGWIDTH, imgHeight = IMGHEIGHT, searchRange = SEARCHRANGE))).
            withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (i <- 0 until 2*4*IMGWIDTH by 4) {
                c.io.enq.valid.poke(true.B)
                val data = gen_write_data(BigInt(i))
                c.io.enq.bits.poke(data.U)
                while (!c.io.enq.ready.peek().litToBoolean) {
                    c.clock.step()
                }
                c.clock.step()
            }
            c.clock.step()
            while (!c.io.deq.valid.peek().litToBoolean) {
                c.clock.step()
            }
            c.io.deq.ready.poke(true.B)
            c.clock.step()
        }
    }
}