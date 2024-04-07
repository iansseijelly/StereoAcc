package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.chipsalliance.cde.config.{Config, Parameters}

abstract class AbstractTopTest (config: Config) extends AnyFlatSpec with ChiselScalatestTester {

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
    it should "do some computation" in {
        implicit val p: Parameters = config
        val param = p(StereoAccKey)

        val ITER_COUNT: Int = 1

        test (new StereoAcc(param)).
            withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (j <- 0 until 2*4*param.imgWidth by 4) {
                c.io.enq.valid.poke(true.B)
                val data = gen_write_data(BigInt(j))
                c.io.enq.bits.poke(data.U)
                while (!c.io.enq.ready.peek().litToBoolean) {
                    c.clock.step()
                }
                c.clock.step()
            }
            c.clock.step()
            for (j <- 0 until param.imgWidth/4){
                while (!c.io.deq.valid.peek().litToBoolean) {
                    c.clock.step()
                }
                c.io.deq.ready.poke(true.B)
                print_read_data(c.io.deq.bits.peek().litValue)
                c.clock.step()
                c.io.deq.ready.poke(false.B)
            }
            for (i <- 0 until ITER_COUNT) {
                for (j <- 0 until 2*param.imgWidth by 4) {
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

class BaseStereoAccTest extends AbstractTopTest(new BasicConfig)