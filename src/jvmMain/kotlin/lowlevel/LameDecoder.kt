package lowlevel

import jdk.and
import java.nio.ByteBuffer

import mp3.BitStream
import mp3.Enc
import mp3.GainAnalysis
import mp3.GetAudio
import mp3.ID3Tag
import mp3.Lame
import mp3.LameGlobalFlags
import mp3.Parse
import mp3.Presets
import mp3.Quantize
import mp3.QuantizePVT
import mp3.Reservoir
import mp3.Takehiro
import mp3.VBRTag
import mp3.Version
import mpg.Common
import mpg.Interface
import mpg.MPGLib

class LameDecoder(mp3File: String) {

    private val gaud: GetAudio
    private val id3: ID3Tag
    private val lame: Lame
    private val ga: GainAnalysis
    private val bs: BitStream
    private val p: Presets
    private val qupvt: QuantizePVT
    private val qu: Quantize
    private val vbr: VBRTag
    private val ver: Version
    private val rv: Reservoir
    private val tak: Takehiro
    private val parse: Parse

    private val mpg: MPGLib
    private val intf: Interface
    private val common: Common

    private var wavsize: Int = 0
    private val buffer = Array(2) { ShortArray(1152) }
    // private DataOutput outf;
    private val gfp: LameGlobalFlags?

    init {
        // encoder modules
        lame = Lame()
        gaud = GetAudio()
        ga = GainAnalysis()
        bs = BitStream()
        p = Presets()
        qupvt = QuantizePVT()
        qu = Quantize()
        vbr = VBRTag()
        ver = Version()
        id3 = ID3Tag()
        rv = Reservoir()
        tak = Takehiro()
        parse = Parse()

        mpg = MPGLib()
        intf = Interface()
        common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, p)

        // decoder modules
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        gfp = lame.lame_init()

        /*
		 * turn off automatic writing of ID3 tag data into mp3 stream we have to
		 * call it before 'lame_init_params', because that function would spit
		 * out ID3v2 tag data.
		 */
        gfp!!.write_id3tag_automatic = false

        /*
		 * Now that all the options are set, lame needs to analyze them and set
		 * some more internal options and check for problems
		 */
        lame.lame_init_params(gfp)

        parse.input_format = GetAudio.sound_file_format.sf_mp3

        val inPath = StringBuilder(mp3File)
        val enc = Enc()

        gaud.init_infile(gfp, inPath.toString(), enc)

        var skip_start = 0
        var skip_end = 0

        if (parse.silent < 10)
            System.out.printf("\rinput:  %s%s(%g kHz, %d channel%s, ", inPath,
                    if (inPath.length > 26) "\n\t" else "  ",
                    gfp.in_samplerate / 1e3, gfp.num_channels,
                    if (gfp.num_channels != 1) "s" else "")

        if (enc.enc_delay > -1 || enc.enc_padding > -1) {
            if (enc.enc_delay > -1)
                skip_start = enc.enc_delay + 528 + 1
            if (enc.enc_padding > -1)
                skip_end = enc.enc_padding - (528 + 1)
        } else
            skip_start = gfp.encoder_delay + 528 + 1

        System.out.printf("MPEG-%d%s Layer %s", 2 - gfp.version,
                if (gfp.out_samplerate < 16000) ".5" else "", "III")

        System.out.printf(")\noutput: (16 bit, Microsoft WAVE)\n")

        if (skip_start > 0)
            System.out.printf(
                    "skipping initial %d samples (encoder+decoder delay)\n",
                    skip_start)
        if (skip_end > 0)
            System.out
                    .printf("skipping final %d samples (encoder padding-decoder delay)\n",
                            skip_end)

        wavsize = -(skip_start + skip_end)
        parse.mp3input_data.totalframes = parse.mp3input_data.nsamp / parse.mp3input_data.framesize

        assert(gfp.num_channels >= 1 && gfp.num_channels <= 2)
    }

    fun decode(sampleBuffer: ByteBuffer, playOriginal: Boolean) {
        val iread = gaud.get_audio16(gfp!!, buffer)
        if (iread >= 0) {
            parse.mp3input_data.framenum += iread / parse.mp3input_data.framesize
            wavsize += iread

            for (i in 0 until iread) {
                if (playOriginal) {
                    // We put mp3 data into the sample buffer here!
                    sampleBuffer.array()[i * 2] = (buffer[0][i] and 0xff).toByte()
                    sampleBuffer.array()[i * 2 + 1] = (buffer[0][i] and 0xffff shr 8 and 0xff).toByte()
                }

                if (gfp.num_channels == 2) {
                    // gaud.write16BitsLowHigh(outf, buffer[1][i] & 0xffff);
                    // TODO two channels?
                }
            }
        }

    }

    fun close() {
        lame.lame_close(gfp)
    }
}
