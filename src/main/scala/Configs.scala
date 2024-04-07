package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters, Field}

case object StereoAccKey extends Field[StereoAccParams]

class StereoAccMasterConfig (p: StereoAccParams) extends Config((site, here, up) => {
    case StereoAccKey => p })

class BasicConfig extends StereoAccMasterConfig(StereoAccParams(
    blockSize = 4,
    fuWidth = 4,
    costFunct = "SAD",
    imgWidth = 32,
    imgHeight = 32,
    useSRAM = true,
    searchRange = 8
))