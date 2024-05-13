package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.chipsalliance.cde.config.{Config, Parameters}

class pool2DTester(implicit val p: Parameters) extends Module {
    val th = Module(new Pool2DTestHarness)
}

abstract class Pool2DTest (config: Config) extends AnyFlatSpec with ChiselScalatestTester {

    val BLOCKSIZE: Int = 4
    val IMGWIDTH : Int = 32
    val IMGHEIGHT : Int = 32

    def gen_write_data(index: BigInt): BigInt = {
        val data : BigInt = (index%256|(((index+1)%256)<<8)|(((index+2)%256)<<16)|(((index+3)%256)<<24))
        data
    }

    behavior of "Pool2D" 

    it should "do some compute" in {
        implicit val p: Parameters = config
        test (new pool2DTester).
            withAnnotations(Seq(WriteVcdAnnotation)).runUntilStop(timeout = 1000)

    }
}

class Avg2DPoolTest extends Pool2DTest(new Avg2DConfig)
class Max2DPoolTest extends Pool2DTest(new Max2DConfig)