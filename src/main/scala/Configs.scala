package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters, Field}
import rose.{BuildDataflow, CParam, CParam_Container}

// *** configs for stand-alone accelerators ***
case object StereoAccKey extends Field[StereoAccParams]

class StereoAccMasterConfig (p: StereoAccParams) extends Config((site, here, up) => {
    case StereoAccKey => p })

class TestConfig extends StereoAccMasterConfig(StereoAccParams(
    blockSize = 4,
    fuWidth = 4,
    costFunct = "SAD",
    imgWidth = 32,
    imgHeight = 5,
    useSRAM = true,
    searchRange = 8
))

class SmallSADConfig extends StereoAccMasterConfig(StereoAccParams(
    blockSize = 4,
    fuWidth = 16,
    costFunct = "SAD",
    imgWidth = 256,
    imgHeight = 256,
    useSRAM = true,
    searchRange = 12
))

// *** configs for coupled accelerators ***
class StereoAccDataflowConfig (gen_params: StereoAccParams) extends Config((site, here, up) => {
    case BuildDataflow => up(BuildDataflow) ++ Seq(
        (p: Parameters) => {
            implicit val q = p
            val stereoacc = Module(new StereoAcc(gen_params))
            stereoacc
        }
    )
})

class SmallStereoAccDataflowConfg extends Config(
    new StereoAccDataflowConfig(StereoAccParams(
        blockSize = 4,
        fuWidth = 16,
        costFunct = "SAD",
        imgWidth = 256,
        imgHeight = 256,
        useSRAM = true,
        searchRange = 12
    ))
)

// *** channel parameters using stereoacc ***
class SingleChannelStereAccParam extends CParam_Container(Seq(
  CParam(port_type = "reqrsp", name = "reqrsp0", df_keys = Some(new SmallStereoAccDataflowConfg))
))