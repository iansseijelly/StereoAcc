package stereoacc

import freechips.rocketchip.tile._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{BaseSubsystem, CacheBlockBytes}
import freechips.rocketchip.regmapper.RegField.r

object StereoAccISA{
    // funct
    val CONFIG_CMD = 0.U(7.W)
    val COMPUTE_CMD = 1.U(7.W)
    // rs1[2:0] for config
    val CONFIG_DMA_R_ADDR = 0.U(3.W)
    val CONFIG_DMA_W_ADDR = 1.U(3.W)
    // unimplemented
    val CONFIG_IMG_WIDTH =  2.U(3.W)
    val CONFIG_IMG_HEIGHT = 3.U(3.W)
}
import StereoAccISA._

class DefaultStereoAccConfig(
  stereoaccConfig: StereoAccParams = StereoAccParams(),
  opcodes : OpcodeSet = OpcodeSet.custom0
) extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      implicit val q = p
      val stereoaccrocc = LazyModule(new RoCCStereoAcc(opcodes, stereoaccConfig))
      stereoaccrocc
    }
  )
})

class r_dma_io extends Bundle{
  val addr = Input(UInt(32.W))
  val enable = Input(Bool())
  val data = Decoupled(UInt(32.W))
  val done = Output(Bool())
}

class w_dma_io extends Bundle{
  val addr = Input(UInt(32.W))
  val enable = Input(Bool())
  val data = Flipped(Decoupled(UInt(32.W)))
  val done = Output(Bool())
}
class stereoaccReadDMA(stereoaccConfig: StereoAccParams)(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "stereoacc-r-dma", sourceId = IdRange(0, 1)))))
  )
  lazy val module = new stereoaccReadDMAModuleImpl(this)
  class stereoaccReadDMAModuleImpl(outer: stereoaccReadDMA) extends LazyModuleImp(outer){
    val io = IO(new r_dma_io)
  
    withClockAndReset(clock, reset){
      val (mem, edge) = outer.node.out(0)
      val addrBits = edge.bundle.addressBits
      val blockBytes = p(CacheBlockBytes)

      val queue = Module(new Queue(UInt(32.W), 32))
      queue.io.deq <> io.data
      queue.io.enq.bits := mem.d.bits.data
      queue.io.enq.valid := mem.d.valid

      val (r_count, r_done) = Counter(mem.a.fire, stereoaccConfig.imgSize)
      
      val mIdle :: mRead :: mResp :: Nil = Enum(3)
      val mstate = RegInit(mIdle)

      mem.a.valid := mstate === mRead
      mem.a.bits := edge.Get(
        fromSource = 0.U,
        toAddress = io.addr + r_count,
        lgSize = log2Ceil(32).U - 3.U
      )._2
      mem.d.ready := mstate === mResp && queue.io.enq.ready

      switch (mstate){
        is (mIdle){
          mstate := Mux(io.enable, mRead, mIdle)
        }
        is (mRead){
          mstate := Mux(mem.a.fire, mResp, mRead)
        }
        is (mResp){
          mstate := Mux(mem.d.fire, mIdle, mResp)
        }
      }
    }
  }
}

class stereoaccWriteDMA(stereoaccConfig: StereoAccParams)(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "stereoacc-w-dma", sourceId = IdRange(0, 1)))))
  )
  lazy val module = new stereoaccWriteDMAModuleImpl(this)
  class stereoaccWriteDMAModuleImpl(outer: stereoaccWriteDMA) extends LazyModuleImp(outer){
    val io = IO(new w_dma_io)
  
    withClockAndReset(clock, reset){
      val (mem, edge) = outer.node.out(0)
      val addrBits = edge.bundle.addressBits
      val blockBytes = p(CacheBlockBytes)

      val queue = Module(new Queue(UInt(32.W), 32))
      queue.io.enq <> io.data

      val (r_count, r_done) = Counter(mem.a.fire, stereoaccConfig.imgSize)
      
      val mIdle :: mWrite :: mResp :: Nil = Enum(3)
      val mstate = RegInit(mIdle)

      mem.a.valid := mstate === mWrite && queue.io.deq.valid
      queue.io.deq.ready := mstate === mWrite && mem.a.ready

      mem.a.bits := edge.Put(
        fromSource = 0.U,
        toAddress = io.addr + r_count,
        lgSize = log2Ceil(32).U - 3.U,
        data = queue.io.deq.bits
      )._2
      mem.d.ready := mstate === mResp 

      switch (mstate){
        is (mIdle){
          mstate := Mux(io.enable, mWrite, mIdle)
        }
        is (mWrite){
          mstate := Mux(mem.a.fire, mResp, mWrite)
        }
        is (mResp){
          mstate := Mux(mem.d.fire, mIdle, mResp)
        }
      }
    }
  }
}

class RoCCStereoAcc(opcodes: OpcodeSet, stereoaccConfig: StereoAccParams)(implicit p: Parameters) extends LazyRoCC(opcodes){
    override lazy val module = new RoCCStereoAccModule(this)
    val w_dma = LazyModule(new stereoaccWriteDMA(stereoaccConfig))
    val r_dma = LazyModule(new stereoaccReadDMA(stereoaccConfig))
    w_dma.module.io <> module.dma_io.w_dma
    r_dma.module.io <> module.dma_io.r_dma
}

class RoCCStereoAccModule(outer: RoCCStereoAcc) extends LazyRoCCModuleImp(outer) {
  val dma_io = IO(new Bundle {
    val r_dma = new r_dma_io
    val w_dma = new w_dma_io
  })

  chisel3.dontTouch(io)

  withClockAndReset(clock, reset){
    val cmd_q = Queue(io.cmd)
    val ctrl = Module(new CtrlModule()(p))
  } 
}

class CtrlModule ()(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val rocc_req_val = Input(Bool())
    val rocc_req_rdy = Output(Bool())
    val rocc_funct = Input(UInt(7.W))
    val rocc_rs1 = Input(UInt(64.W))
    val rocc_rs2 = Input(UInt(64.W))
    val rocc_rd = Input(UInt(5.W))

    val busy = Output(Bool())

    val r_dma = new r_dma_io
    val w_dma = new w_dma_io
  })

  val s_idle :: s_compute :: Nil = Enum(2)
  val state = RegInit(s_idle)

  val r_addr = Reg(UInt(32.W))
  val w_addr = Reg(UInt(32.W))

  when (state === s_idle && io.rocc_req_val && io.rocc_funct === CONFIG_CMD){
    switch(io.rocc_rs1(2,0)){
      is(CONFIG_DMA_R_ADDR){
        r_addr := io.rocc_rs2
      }
      is(CONFIG_DMA_W_ADDR){
        w_addr := io.rocc_rs2
      }
    }
  }
  
  io.busy := state === s_compute
  io.rocc_req_rdy := state === s_idle

  switch(state){
    is(s_idle){
      state := Mux(io.rocc_req_val && io.rocc_funct === COMPUTE_CMD, s_compute, s_idle)
    }
    is(s_compute){
      state := Mux(io.w_dma.done, s_idle, s_compute)
    }
  }
}
