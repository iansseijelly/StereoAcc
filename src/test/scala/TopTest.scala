package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.chipsalliance.cde.config.{Config, Parameters}

class StereoAccTester(implicit val p: Parameters) extends Module {
    val th = Module(new TestHarness)
}

@deprecated("Implmentation is not complete", "1.0")
class StereoAccImgTester(implicit val p: Parameters) extends Module {
    val th = Module(new ImgTestHarness)
}

abstract class AbstractTopTest (config: Config) extends AnyFlatSpec with ChiselScalatestTester {
    behavior of "StereoAcc"
    it should "do some computation" in {
        implicit val p: Parameters = config
        val param = p(StereoAccKey)
        test (new StereoAccTester).
            withAnnotations(Seq(WriteVcdAnnotation)).runUntilStop(timeout = 1000)
    }
}

class BaseStereoAccTest extends AbstractTopTest(new TestConfig)