package stereoacc

import chisel3._
import chisel3.util._
import rose.BaseDataflowParameter

case class StereoAccParams(
    // The size of matching block in pixels(bytes)
    blockSize: Int = 4,
    // The number of functional units
    fuWidth: Int = 4,
    // The depth of the functional units (number of blocks computed per cycle)
    // fuDepth: Int = 8,
    // The cost function used
    costFunct: String = "SAD",
    // TODO: make me MMIO
    // The width of the target image
    imgWidth: Int = 256,
    // TODO: make me MMIO
    // The height of the target image
    imgHeight: Int = 256,
    // Use SRAM for the row buffer
    useSRAM: Boolean = true,
    // search range
    // TODO: make me MMIO?
    searchRange : Int = 16,
    // *** BaseDataflow Parameters ***
    dfChannelWidth: Int = 32
) extends BaseDataflowParameter(channelWidth = dfChannelWidth) {
    // require(fuDepth % 4 == 0, "The depth of the functional units must be a multiple of 4")
    require(Seq("SAD", "SSD", "NCC", "EU_SAD").contains(costFunct), "The cost function must be one of SAD, SSD, NCC")
    require(imgWidth % 4 == 0, "The width of the image must be a multiple of 4bytes, 32 bits")
    require(searchRange < imgWidth, "The search range must be less than the width of the image")
    require(fuWidth % 4 == 0, "The width of the functional units must be a multiple of 4")
    // //FIXME: formalize these requirements
    require((imgWidth-searchRange-blockSize) % fuWidth == 0,
        "The width of the functional units must be a multiple of the image width minus the search range")
    // require(fuWidth % blockSize == 0, "The width of the functional units must be a multiple of the block size")
    // def numBlocksPerIter: Int = fuDepth / blockSize
    def numIterPerRow: Int = (imgWidth-searchRange-blockSize)/fuWidth
    def imgSize = imgHeight * imgWidth
    def stereoImgSize = (imgWidth - searchRange) * (imgHeight - blockSize)
    override def elaborate: StereoAcc = Module(new StereoAcc(this))
}

case class Pool2DParams(
    // the size of block being resized to one pixel
    blockSize: Int = 2,
    // the width of the target image
    imgWidth: Int = 256,
    // the height of the target image
    imgHeight: Int = 256,
    // reduction function
    reduction: String = "max",
    // *** BaseDataflow Parameters ***
    dfChannelWidth: Int = 32 
) extends BaseDataflowParameter(channelWidth = dfChannelWidth) {
    require(imgWidth % blockSize == 0, "The width of the image must be a multiple of the block size")
    require(imgHeight % blockSize == 0, "The height of the image must be a multiple of the block size")
    require(Seq("max", "avg").contains(reduction), "The reduction function must be one of max, avg")
    override def elaborate: Pool2D = Module(new Pool2D(this))
}

case class EdgeDetAccParams (
    imgWidth: Int = 256,
    imgHeight: Int = 256,
    dfChannelWidth: Int = 32
) extends BaseDataflowParameter(channelWidth = dfChannelWidth) {
    require(imgWidth % 4 == 0, "The width of the image must be a multiple of 4bytes, 32 bits")
    require(imgHeight % 4 == 0, "The height of the image must be a multiple of 4bytes, 32 bits")
    override def elaborate: EdgeDetAcc = Module(new EdgeDetAcc(this))
    def blockSize: Int = 3
}