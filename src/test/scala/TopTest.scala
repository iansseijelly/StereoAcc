package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class TopTest extends AnyFlatSpec with ChiselScalatestTester {

    val BLOCKSIZE: Int = 4
    val IMGWIDTH: Int = 32
    val IMGHEIGHT: Int = 32
    val SEARCHRANGE: Int = 8
    val ITER_COUNT: Int = 1
    
    def gen_write_data(index: BigInt): BigInt = {
        val data : BigInt = (index%256|(((index+1)%256)<<8)|(((index+2)%256)<<16)|(((index+3)%256)<<24))
        data
    }

    def print_read_data(data: BigInt): Unit = {
        // get the last two bytes
        val byte1 = (data & 0xFF).toByte
        val byte2 = ((data >> 8) & 0xFF).toByte
        val byte3 = ((data >> 16) & 0xFF).toByte
        val byte4 = ((data >> 24) & 0xFF).toByte
        println("Read data: " + byte1 + " " + byte2 + " " + byte3 + " " + byte4)
    }

    behavior of "StereoAcc"
    it should "instantiate StereoAcc" in {
        test(new StereoAcc(StereoAccParams())) { c =>
            c.clock.step()
            println("Instantiation successful!")
        }
    }

    it should "do some computation" in {
        test (new StereoAcc(StereoAccParams(blockSize = BLOCKSIZE, imgWidth = IMGWIDTH, imgHeight = IMGHEIGHT, searchRange = SEARCHRANGE))).
            withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (j <- 0 until 2*4*IMGWIDTH by 4) {
                c.io.enq.valid.poke(true.B)
                val data = gen_write_data(BigInt(j))
                c.io.enq.bits.poke(data.U)
                while (!c.io.enq.ready.peek().litToBoolean) {
                    c.clock.step()
                }
                c.clock.step()
            }
            c.clock.step()
            for (j <- 0 until IMGWIDTH/4){
                while (!c.io.deq.valid.peek().litToBoolean) {
                    c.clock.step()
                }
                c.io.deq.ready.poke(true.B)
                print_read_data(c.io.deq.bits.peek().litValue)
                c.clock.step()
                c.io.deq.ready.poke(false.B)
            }
            for (i <- 0 until ITER_COUNT) {
                for (j <- 0 until 2*IMGWIDTH by 4) {
                    c.io.enq.valid.poke(true.B)
                    val data = gen_write_data(BigInt(j))
                    c.io.enq.bits.poke(data.U)
                    while (!c.io.enq.ready.peek().litToBoolean) {
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
    }
}