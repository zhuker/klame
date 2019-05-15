package mp3

import jdk.assert
import mpg.MPGLib
import kotlin.js.JsName

class Mp3Encoder(val channels: Int = 1, samplerate: Int = 44100, val kbps: Int = 128) {
    private val lame = Lame()
    private val gfp: LameGlobalFlags
    private var maxSamples = 1152
    private var mp3buf_size: Int
    private var mp3buf: ByteArray

    init {
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

        gfp = lame.lame_init()!!

        gfp.num_channels = channels
        gfp.in_samplerate = samplerate
        gfp.brate = kbps
        gfp.mode = MPEGMode.STEREO
        gfp.quality = 3
        gfp.bWriteVbrTag = false
        gfp.disable_reservoir = true
        gfp.write_id3tag_automatic = false

        val retcode = lame.lame_init_params(gfp)
        assert(0 == retcode)
        mp3buf_size = (1.25 * maxSamples + 7200).toInt()
        mp3buf = ByteArray(mp3buf_size)
    }

    @JsName("encodeBuffer")
    fun encodeBuffer(left: ShortArray, right: ShortArray): ByteArray {
        val _left = left
        val _right = if (channels == 1) left else right

        assert(_left.size == _right.size)
        if (_left.size > maxSamples) {
            maxSamples = _left.size
            mp3buf_size = (1.25 * maxSamples + 7200).toInt()
            mp3buf = ByteArray(mp3buf_size)
        }

        val _sz = lame.lame_encode_buffer(gfp, _left, _right, _left.size, mp3buf, 0, mp3buf_size)
        return mp3buf.copyOfRange(0, _sz)
    }

    /** samples are assumed to be in -32768..32767 range */
    @JsName("encodeSamples")
    fun encodeSamples(left: FloatArray, right: FloatArray): ByteArray {
        val _left = left
        val _right = if (channels == 1) left else right

        assert(_left.size == _right.size)
        if (_left.size > maxSamples) {
            maxSamples = _left.size
            mp3buf_size = (1.25 * maxSamples + 7200).toInt()
            mp3buf = ByteArray(mp3buf_size)
        }

        val _sz = lame.lame_encode_buffer_sample(gfp, _left, _right, _left.size, mp3buf, 0, mp3buf_size)
        return mp3buf.copyOfRange(0, _sz)
    }

    @JsName("flush")
    fun flush(): ByteArray {
        val _sz = lame.lame_encode_flush(gfp, mp3buf, 0, mp3buf_size)
        return mp3buf.copyOfRange(0, _sz)
    }
}