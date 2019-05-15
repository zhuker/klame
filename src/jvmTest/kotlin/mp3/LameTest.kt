package mp3

import mpg.Common
import mpg.Interface
import mpg.MPGLib
import org.junit.Assert
import org.junit.Test
import java.io.*

import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.util.*
import sun.security.krb5.Confounder.bytes
import com.sun.codemodel.internal.JOp.shl
import jdk.and
import jdk.shl


class LameTest {
    @Test
    fun testEncodeChugai() {
        val lame = Lame()
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()

        val mpg = MPGLib()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)

        val gfp = lame.lame_init()!!

        gfp.num_channels = 2
        gfp.in_samplerate = 48000
        gfp.brate = 128
        gfp.mode = MPEGMode.STEREO
        gfp.quality = 3
        gfp.bWriteVbrTag = false
        gfp.disable_reservoir = true
        gfp.write_id3tag_automatic = false

        val retcode = lame.lame_init_params(gfp)
        println("DONE1 $retcode")

        //        File file = new File("/Users/zhukov/git/tle1.3x/test-data/wav/440880.wav");
        val file = File("testdata/c00.wav")
        val samples = samples(file)
        //        samples.limit(1152 * 8);
        Assert.assertEquals(0x0089, samples.get(0).toLong())
        Assert.assertEquals(0x00D0, samples.get(1).toLong())
        val remaining = samples.remaining()

        val left = IntArray(remaining)
        for (i in 0 until remaining)
            left[i] = samples.get(i).toInt()

        val mp3buf_size = (1.25 * remaining + 7200).toInt()
        val mp3buf = ByteArray(mp3buf_size)
        var mp3bufPos = 0

        var _sz = lame.lame_encode_buffer_int(
            gfp, left, left, remaining,
            mp3buf, mp3bufPos, mp3buf_size
        )
        mp3bufPos += _sz
        println("lame_encode_buffer: $_sz")

        _sz = lame.lame_encode_flush(gfp, mp3buf, mp3bufPos, mp3buf_size)
        mp3bufPos += _sz
        println("lame_encode_flush: $_sz")

        val out = BufferedOutputStream(FileOutputStream("generated_java.mp3"))
        out.write(mp3buf, 0, mp3bufPos)
        out.close()

        val mappedByteBuffer = mmapReadonly(File("testdata/c00.mp3"))
        val expected = ByteArray(mappedByteBuffer.capacity())
        mappedByteBuffer.get(expected)
        val actual = Arrays.copyOf(mp3buf, mp3bufPos)
        Assert.assertArrayEquals(expected, actual)
    }

    private fun samples(file: File): ShortBuffer {
        val map = mmapReadonly(file)
        map.order(ByteOrder.LITTLE_ENDIAN)
        val dataStart = 0x4a
        val dataLen = map.getInt(dataStart)
        map.position(dataStart + 4)
        map.limit(dataStart + 4 + dataLen)

        val samples = map.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return samples!!
    }


    private fun mmapReadonly(file: File): MappedByteBuffer {
        return FileInputStream(file).channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    }

    @Test
    fun arabic() {
        val file = File("/Users/zhukov/audioconform/arabic01.wav")
        val samples = samples(file)
        println(samples)
        val duplicate = samples.duplicate()
        duplicate.position(262144)
        duplicate.limit(294911)
        val slice = duplicate.slice()
        val remaining = slice.remaining()
        val shorts = ShortArray(remaining)
        slice.get(shorts)
        println(shorts.contentToString())

    }

    @Test
    fun ga() {
        val lo = -113 and 0xff
        val hi = -2048 and 0xffff
        val sa = hi or lo
        println(Integer.toHexString(hi))
        println(Integer.toHexString(lo))
        println(Integer.toHexString(sa))
        println(Integer.toHexString(sa and 0xffff))
        println(sa)
        println(sa.toShort())
        println((sa and 0xffff).toShort())

        println(to16(-113, -8))

    }

    fun to16(low: Byte, high: Byte): Int {
        val lo = low and 0xff
        val hi = (high shl 8)
        val sample = hi or lo
        return sample
    }
}
