package stereoacc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.chipsalliance.cde.config.{Config, Parameters}

class EdgeDetAccTester(implicit val p: Parameters) extends Module {
    val th = Module(new EdgeDetAccTestHarness)
}

abstract class AbstractEdgeDetAccTest (config: Config) extends AnyFlatSpec with ChiselScalatestTester {

    behavior of "EdgeDetAcc"
 
    it should "do some compute" in {
        implicit val p: Parameters = config
        test (new EdgeDetAccTester).
            withAnnotations(Seq(VcsBackendAnnotation, WriteFsdbAnnotation)).runUntilStop(timeout = 1000)

    }
}

class EdgeDetAccTest extends AbstractEdgeDetAccTest(new EdgeDetAccTestConfig)
