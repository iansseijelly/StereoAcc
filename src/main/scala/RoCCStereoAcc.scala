package stereoacc

import freechips.rocketchip.tile._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{BaseSubsystem, CacheBlockBytes}
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper.RegField.r
import freechips.rocketchip.rocket.TLB

object StereoAccISA{
    // funct
    val COMPUTE_CMD = 0.U(7.W)
    val CONFIG_DMA_R_ADDR = 1.U(7.W)
    val CONFIG_DMA_W_ADDR = 2.U(7.W)
}
import StereoAccISA._

class WithDefaultStereoAccConfig(
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

      val (r_count, r_done) = Counter(mem.a.fire, stereoaccConfig.imgSize/4)
      
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
      io.done := r_done
    }
  }
}

class stereoaccWriteDMA(stereoaccConfig: StereoAccParams)(implicit p: Parameters) extends LazyModule {
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "stereoacc-w-dma", sourceId = IdRange(0, 1))))))
  override lazy val module = new stereoaccWriteDMAModuleImpl(this)
  class stereoaccWriteDMAModuleImpl(outer: stereoaccWriteDMA) extends LazyModuleImp(this) {
    val io = IO(new w_dma_io)
  
    withClockAndReset(clock, reset){
      val (mem, edge) = outer.node.out(0)
      val addrBits = edge.bundle.addressBits
      val blockBytes = p(CacheBlockBytes)

      val queue = Module(new Queue(UInt(32.W), 32))
      queue.io.enq <> io.data

      val (w_count, w_done) = Counter(mem.a.fire, stereoaccConfig.imgSize/4)
      
      val mIdle :: mWrite :: mResp :: Nil = Enum(3)
      val mstate = RegInit(mIdle)

      mem.a.valid := mstate === mWrite && queue.io.deq.valid
      queue.io.deq.ready := mstate === mWrite && mem.a.ready

      mem.a.bits := edge.Put(
        fromSource = 0.U,
        toAddress = io.addr + w_count,
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
      io.done := w_done
    }
  }
}

class RoCCStereoAcc(opcodes: OpcodeSet, stereoaccConfig: StereoAccParams)(implicit p: Parameters) extends LazyRoCC(opcodes){
    val scfg = stereoaccConfig
    override lazy val module = new RoCCStereoAccModule(this)
    val w_dma = LazyModule(new stereoaccWriteDMA(stereoaccConfig))
    val r_dma = LazyModule(new stereoaccReadDMA(stereoaccConfig))

    val id_node = TLIdentityNode()
    val xbar_node = TLXbar()   

    xbar_node := TLBuffer() := r_dma.node
    xbar_node := TLBuffer() := w_dma.node
    id_node := TLBuffer() := xbar_node

    override val tlNode = id_node
    val node = id_node
}

class RoCCStereoAccModule(outer: RoCCStereoAcc) extends LazyRoCCModuleImp(outer) {
  chisel3.dontTouch(io)

  withClockAndReset(clock, reset){
    val cmd_q = Queue(io.cmd)
    val core = Module(new CoreModule(outer.scfg)(p))
    outer.w_dma.module.io <> core.io.w_dma
    outer.r_dma.module.io <> core.io.r_dma

    core.io.rocc_req_val := cmd_q.valid
    core.io.rocc_funct := cmd_q.bits.inst.funct
    core.io.rocc_rs1 := cmd_q.bits.rs1
    core.io.rocc_rs2 := cmd_q.bits.rs2
    core.io.rocc_rd := cmd_q.bits.inst.rd
    cmd_q.ready := core.io.rocc_req_rdy
    io.busy := core.io.busy
  } 
}

class CoreModule(stereoaccConfig: StereoAccParams)(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val rocc_req_val = Input(Bool())
    val rocc_req_rdy = Output(Bool())
    val rocc_funct = Input(UInt(7.W))
    val rocc_rs1 = Input(UInt(64.W))
    val rocc_rs2 = Input(UInt(64.W))
    val rocc_rd = Input(UInt(5.W))

    val busy = Output(Bool())

    val r_dma = Flipped(new r_dma_io)
    val w_dma = Flipped(new w_dma_io)
  })

  val s_idle :: s_compute :: Nil = Enum(2)
  val state = RegInit(s_idle)

  val r_addr = Reg(UInt(32.W))
  val w_addr = Reg(UInt(32.W))

  when (state === s_idle && io.rocc_req_val){
    switch(io.rocc_funct){
      is(CONFIG_DMA_R_ADDR){
        r_addr := io.rocc_rs1
      }
      is(CONFIG_DMA_W_ADDR){
        w_addr := io.rocc_rs1
      }
    }
  }

  val stereoacc = Module(new StereoAcc(stereoaccConfig))
  val dma_enable = RegInit(false.B)
  when (state === s_idle && io.rocc_req_val && io.rocc_funct === COMPUTE_CMD){
    dma_enable := true.B
  }
  Pulsify(dma_enable)

  io.r_dma.addr := r_addr
  io.r_dma.enable := dma_enable
  io.r_dma.data <> stereoacc.io.enq  
  
  io.w_dma.addr := w_addr
  io.w_dma.enable := dma_enable
  stereoacc.io.deq <> io.w_dma.data

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
