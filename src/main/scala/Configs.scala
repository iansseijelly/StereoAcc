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


case object Pool2DKey extends Field[Pool2DParams]

class Pool2DConfig (p: Pool2DParams) extends Config((site, here, up) => {
    case Pool2DKey => p })

class Avg2DConfig extends Pool2DConfig(Pool2DParams(
    blockSize = 2,
    imgWidth = 32,
    imgHeight = 32,
    reduction = "avg"
)) 

class Max2DConfig extends Pool2DConfig(Pool2DParams(
    blockSize = 2,
    imgWidth = 32,
    imgHeight = 32,
    reduction = "max"
))

case object EdgeDetAccKey extends Field[EdgeDetAccParams]

class EdgeDetAccConfig (p: EdgeDetAccParams) extends Config((site, here, up) => {
    case EdgeDetAccKey => p })

class EdgeDetAccTestConfig extends EdgeDetAccConfig(EdgeDetAccParams(
    imgHeight = 32,
    imgWidth = 32))
