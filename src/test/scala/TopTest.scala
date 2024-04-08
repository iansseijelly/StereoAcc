package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.chipsalliance.cde.config.{Config, Parameters}

class StereoAccTester(implicit val p: Parameters) extends Module {
    val th = Module(new TestHarness)
    when (th.io.success) { stop() }
}

abstract class AbstractTopTest (config: Config) extends AnyFlatSpec with ChiselScalatestTester {

    behavior of "StereoAcc"
    it should "do some computation" in {
        implicit val p: Parameters = config
        val param = p(StereoAccKey)

        val ITER_COUNT: Int = 1

        test (new StereoAccTester).
            withAnnotations(Seq(WriteVcdAnnotation)).runUntilStop(timeout = 1000 * 1000)
    }
}

class BaseStereoAccTest extends AbstractTopTest(new BasicConfig)