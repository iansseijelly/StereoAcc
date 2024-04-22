package stereoacc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Config, Parameters, Field}

// *** configs for stand-alone accelerators ***
case object StereoAccKey extends Field[StereoAccParams]

class StereoAccLoneConfig (p: StereoAccParams) extends Config((site, here, up) => {
    case StereoAccKey => p })

class TestConfig extends StereoAccLoneConfig(StereoAccParams(
    blockSize = 4,
    fuWidth = 4,
    costFunct = "SAD",
    imgWidth = 32,
    imgHeight = 5,
    useSRAM = true,
    searchRange = 8
))

class SmallSADConfig extends StereoAccLoneConfig(StereoAccParams(
    blockSize = 4,
    fuWidth = 16,
    costFunct = "SAD",
    imgWidth = 256,
    imgHeight = 256,
    useSRAM = true,
    searchRange = 12
))

// *** configs for near-sensor coupled accelerators ***
