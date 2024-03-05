package revelio

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import treadle.executable.Big



class ImgBufferTest extends AnyFlatSpec with ChiselScalatestTester {

    val BLOCKSIZE: Int = 4
    
    def gen_write_data(index: BigInt): BigInt = {
        val data : BigInt = (index%256|(((index+1)%256)<<8)|(((index+2)%256)<<16)|(((index+3)%256)<<24))
        data
    }

    behavior of "ImgBuffer" 
    // it should "instantiate SRAMImgBuffer" in {
    //     test(new SRAMImgBuffer(nRows=4, imgWidth=32, imgHeight=32)) { c =>
    //         c.clock.step()
    //         println("Instantiation successful!")
    //     }
    // }

    it should "accept writes until stable" in {
        test(new SRAMImgBuffer(nRows=BLOCKSIZE, imgWidth=32, imgHeight=32)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (i <- 0 until 32*(BLOCKSIZE+1)/4 by 4) {
                c.io.write.valid.poke(true.B)
                val data = gen_write_data(BigInt(i))
                c.io.write.bits.poke(data.U)
                while (!c.io.write.ready.peek().litToBoolean) {
                    c.clock.step()
                }
                c.clock.step()
            }
            c.io.write.ready.expect(false.B)
        }
    }

    it should "full read write with conservative writes" in {
        test(new SRAMImgBuffer(nRows=4, imgWidth=32, imgHeight=32)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (i <- 0 until 32*(BLOCKSIZE) by 4) {
                c.io.write.valid.poke(true.B)
                val data = gen_write_data(BigInt(i))
                c.io.write.bits.poke(data.U)
                while (!c.io.write.ready.peek().litToBoolean) {
                    c.clock.step()
                }
                c.clock.step()
                c.io.write.valid.poke(false.B)
                c.clock.step()
            }
            c.io.write.valid.poke(false.B)
            // c.io.write.ready.expect(false.B)
            println("Stable state reached!")
            for (i <- 0 until 32-(BLOCKSIZE)){
                for (j <- 0 until 32){
                    val index = (i*32+j)
                    c.io.read.request.valid.poke(true.B)
                    while(!c.io.read.request.ready.peek().litToBoolean){
                        c.clock.step()
                    }
                    c.clock.step()
                    c.io.read.response.valid.expect(true.B)
                    c.io.read.response.bits(0).expect(( index    %256).U)
                    c.io.read.response.bits(1).expect(((index+32)%256).U)
                    c.io.read.response.bits(2).expect(((index+64)%256).U)
                    c.io.read.response.bits(3).expect(((index+96)%256).U)
                    println(s"Read index: $index")
                }
                c.io.read.request.valid.poke(false.B)
                for (k <- 0 until 32 by 4){
                    val index = ((i+(BLOCKSIZE))*32+k)%256
                    c.io.write.valid.poke(true.B)
                    val data = gen_write_data(BigInt(index))
                    c.io.write.bits.poke(data.U)
                    // wait for the write to be accepted
                    while (!c.io.write.ready.peek().litToBoolean) {
                        c.clock.step()
                    }
                    println(s"Write $i")
                    c.clock.step() 
                    c.io.write.valid.poke(false.B)
                    c.clock.step()
                }
            }
            c.io.read.request.
        }
    }

    it should "full read write with excessive writes" in {
        test(new SRAMImgBuffer(nRows=4, imgWidth=32, imgHeight=32)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            for (i <- 0 until 32*(BLOCKSIZE+1) by 4) {
                c.io.write.valid.poke(true.B)
                val data = gen_write_data(BigInt(i))
                c.io.write.bits.poke(data.U)
                while (!c.io.write.ready.peek().litToBoolean) {
                    c.clock.step()
                }
                c.clock.step()
                c.io.write.valid.poke(false.B)
                c.clock.step()
            }
            c.io.write.valid.poke(false.B)
            // c.io.write.ready.expect(false.B)
            println("Stable state reached!")
            for (i <- 0 until 32-(BLOCKSIZE+1)){
                for (j <- 0 until 32){
                    val index = (i*32+j)
                    c.io.read.request.valid.poke(true.B)
                    c.clock.step()
                    c.io.read.response.valid.expect(true.B)
                    c.io.read.response.bits(0).expect(( index    %256).U)
                    c.io.read.response.bits(1).expect(((index+32)%256).U)
                    c.io.read.response.bits(2).expect(((index+64)%256).U)
                    c.io.read.response.bits(3).expect(((index+96)%256).U)
                    // println(s"Read index: $index")
                }
                c.io.read.request.valid.poke(false.B)
                for (k <- 0 until 32 by 4){
                    val index = ((i+(BLOCKSIZE+1))*32+k)%256
                    c.io.write.valid.poke(true.B)
                    val data = gen_write_data(BigInt(index))
                    c.io.write.bits.poke(data.U)
                    // wait for the write to be accepted
                    while (!c.io.write.ready.peek().litToBoolean) {
                        c.clock.step()
                    }
                    // println(s"Write $i")
                    c.clock.step() 
                }
            }
        }
    }
}
