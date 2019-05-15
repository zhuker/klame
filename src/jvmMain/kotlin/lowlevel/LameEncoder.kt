package lowlevel

import jdk.and
import javax.sound.sampled.AudioSystem.NOT_SPECIFIED

import java.nio.ByteOrder
import java.util.Arrays
import java.util.HashMap

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioFormat.Encoding

import mp3.BRHist
import mp3.BitStream
import mp3.GainAnalysis
import mp3.GetAudio
import mp3.ID3Tag
import mp3.Lame
import mp3.LameGlobalFlags
import mp3.MPEGMode
import mp3.Parse
import mp3.Presets
import mp3.Quantize
import mp3.QuantizePVT
import mp3.Reservoir
import mp3.Takehiro
import mp3.Util
import mp3.VBRTag
import mp3.VbrMode
import mp3.Version
import mpg.Common
import mpg.Interface
import mpg.MPGLib

/**
 * Wrapper for the jump3r encoder.
 *
 * @author Ken Handel
 */
class LameEncoder {

    // encoding source values
    private var sourceEncoding: Encoding? = null
    private var sourceByteOrder: ByteOrder? = null
    private var sourceChannels: Int = 0
    private var sourceSampleRate: Int = 0
    private var sourceSampleSizeInBits: Int = 0
    private var sourceIsBigEndian: Boolean = false

    private var quality = DEFAULT_QUALITY
    private var bitRate = DEFAULT_BITRATE
    private var vbrMode = DEFAULT_VBR
    private var chMode = DEFAULT_CHANNEL_MODE

    // these fields are set upon successful initialization to show effective
    // values.
    private var effQuality: Int = 0
    var effectiveBitRate: Int = 0
        private set
    private var effVbr: Int = 0
    var effectiveChannelMode: Int = 0
        private set
    var effectiveSampleRate: Int = 0
        private set
    private val effEncoding: Int = 0
    private var gfp: LameGlobalFlags? = null
    private var sourceFormat: AudioFormat? = null
    private var targetFormat: AudioFormat? = null

    internal lateinit var gaud: GetAudio
    internal lateinit var id3: ID3Tag
    internal lateinit var lame: Lame
    internal lateinit var ga: GainAnalysis
    internal lateinit var bs: BitStream
    internal lateinit var p: Presets
    internal lateinit var qupvt: QuantizePVT
    internal lateinit var qu: Quantize
    internal lateinit var vbr: VBRTag
    internal lateinit var ver: Version
    internal var util: Util? = null
    internal lateinit var rv: Reservoir
    internal lateinit var tak: Takehiro
    internal lateinit var parse: Parse
    internal lateinit var hist: BRHist

    internal lateinit var mpg: MPGLib
    internal lateinit var intf: Interface
    internal lateinit var common: Common

    /**
     * returns -1 if string is too short or returns one of the exception
     * constants if everything OK, returns the length of the string
     */
    val encoderVersion: String
        get() = ver.lameVersion

    /**
     * Returns the buffer needed pcm buffer size. The passed parameter is a
     * wished buffer size. The implementation of the encoder may return a lower
     * or higher buffer size. The encoder must be initalized (i.e. not closed)
     * at this point. A return value of <0 denotes an error.
     */
    val pcmBufferSize: Int
        get() = DEFAULT_PCM_BUFFER_SIZE

    // bad estimate :)
    val mP3BufferSize: Int
        get() = pcmBufferSize / 2 + 1024


    val inputBufferSize: Int
        get() = pcmBufferSize

    val outputBufferSize: Int
        get() = mP3BufferSize

    /**
     * Return the audioformat representing the encoded mp3 stream. The format
     * object will have the following properties:
     *
     *  * quality: an Integer, 1 (highest) to 9 (lowest)
     *  * bitrate: an Integer, 32...320 kbit/s
     *  * chmode: channel mode, a String, one of &quot;jointstereo&quot;,
     * &quot;dual&quot;, &quot;mono&quot;, &quot;auto&quot; (default).
     *  * vbr: a Boolean
     *  * encoder.version: a string with the version of the encoder
     *  * encoder.name: a string with the name of the encoder
     *
     */
    // first gather properties
    // map.put(P_SAMPLERATE, getEffectiveSampleRate());
    // map.put(P_ENCODING,getEffectiveEncoding());
    val effectiveFormat: AudioFormat
        get() {
            val map = HashMap<String, Any>()
            map[P_QUALITY] = effectiveQuality
            map[P_BITRATE] = effectiveBitRate
            map[P_CHMODE] = chmode2string(effectiveChannelMode)
            map[P_VBR] = effectiveVBR
            map["encoder.name"] = "LAME"
            map["encoder.version"] = encoderVersion
            var channels = 2
            if (chMode == CHANNEL_MODE_MONO) {
                channels = 1
            }
            return AudioFormat(effectiveEncoding,
                    effectiveSampleRate.toFloat(), NOT_SPECIFIED, channels,
                    NOT_SPECIFIED, NOT_SPECIFIED.toFloat(), false, map)
        }

    val effectiveQuality: Int
        get() {
            if (effQuality >= QUALITY_LOWEST) {
                return QUALITY_LOWEST
            } else if (effQuality >= QUALITY_LOW) {
                return QUALITY_LOW
            } else if (effQuality >= QUALITY_MIDDLE) {
                return QUALITY_MIDDLE
            } else if (effQuality >= QUALITY_HIGH) {
                return QUALITY_HIGH
            }
            return QUALITY_HIGHEST
        }

    val effectiveVBR: Boolean
        get() = effVbr != 0

    // default
    val effectiveEncoding: AudioFormat.Encoding
        get() {
            if (effEncoding == MPEG_VERSION_2) {
                return if (effectiveSampleRate < 16000) {
                    MPEG2DOT5L3
                } else MPEG2L3
            } else if (effEncoding == MPEG_VERSION_2DOT5) {
                return MPEG2DOT5L3
            }
            return MPEG1L3
        }


    constructor() {

    }

    /**
     * Initializes the encoder with the given source/PCM format. The default mp3
     * encoding parameters are used, see DEFAULT_BITRATE, DEFAULT_CHANNEL_MODE,
     * DEFAULT_QUALITY, and DEFAULT_VBR.
     *
     * @throws IllegalArgumentException
     * when parameters are not supported by LAME.
     */


    constructor(sourceFormat: AudioFormat) {
        readParams(sourceFormat, null)
        setFormat(sourceFormat, null)
    }

    /**
     * Initializes the encoder with the given source/PCM format. The mp3
     * parameters are read from the targetFormat's properties. For any parameter
     * that is not set, global system properties are queried for backwards
     * tritonus compatibility. Last, parameters will use the default values
     * DEFAULT_BITRATE, DEFAULT_CHANNEL_MODE, DEFAULT_QUALITY, and DEFAULT_VBR.
     *
     * @throws IllegalArgumentException
     * when parameters are not supported by LAME.
     */
    constructor(sourceFormat: AudioFormat, targetFormat: AudioFormat) {
        readParams(sourceFormat, targetFormat.properties())
        setFormat(sourceFormat, targetFormat)
    }


    /**
     * Initializes the encoder, overriding any parameters set in the audio
     * format's properties or in the system properties.
     *
     * @throws IllegalArgumentException
     * when parameters are not supported by LAME.
     */
    constructor(sourceFormat: AudioFormat, bitRate: Int, channelMode: Int, quality: Int, VBR: Boolean) {
        this.bitRate = bitRate
        this.chMode = channelMode
        this.quality = quality
        this.vbrMode = VBR
        setFormat(sourceFormat, null)
    }

    private fun readParams(sourceFormat: AudioFormat, props: Map<String, Any>?) {
        if (props != null) {
            readProps(props)
        }
    }

    fun setSourceFormat(sourceFormat: AudioFormat) {
        setFormat(sourceFormat, null)
    }

    fun setTargetFormat(targetFormat: AudioFormat) {
        setFormat(null, targetFormat)
    }

    fun setFormat(sourceFormat: AudioFormat?, targetFormat: AudioFormat?) {
        this.sourceFormat = sourceFormat
        if (sourceFormat != null) {
            sourceEncoding = sourceFormat.encoding
            sourceSampleSizeInBits = sourceFormat.sampleSizeInBits
            sourceByteOrder = if (sourceFormat.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            sourceChannels = sourceFormat.channels
            sourceSampleRate = Math.round(sourceFormat.sampleRate)
            sourceIsBigEndian = sourceFormat.isBigEndian
            // simple check that bitrate is not too high for MPEG2 and MPEG2.5
            // todo: exception ?
            if (sourceFormat.sampleRate < 32000 && bitRate > 160) {
                bitRate = 160
            }
        }
        //-1 means do not change the sample rate
        var targetSampleRate = -1
        this.targetFormat = targetFormat
        if (targetFormat != null) {
            targetSampleRate = Math.round(targetFormat.sampleRate)
        }
        val result = nInitParams(sourceChannels,
                sourceSampleRate,
                targetSampleRate,
                bitRate,
                chMode,
                quality,
                vbrMode,
                sourceIsBigEndian)
        if (result < 0) {
            throw IllegalArgumentException("parameters not supported by LAME (returned $result)")
        }
    }

    /**
     * Initializes the lame encoder. Throws IllegalArgumentException when
     * parameters are not supported by LAME.
     */
    private fun nInitParams(channels: Int, inSampleRate: Int, outSampleRate: Int, bitrate: Int,
                            mode: Int, quality: Int, VBR: Boolean, bigEndian: Boolean): Int {
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
        hist = BRHist()

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
        gfp!!.num_channels = channels
        gfp!!.in_samplerate = inSampleRate
        if (outSampleRate >= 0)
            gfp!!.out_samplerate = outSampleRate
        if (mode != CHANNEL_MODE_AUTO) {
            gfp!!.mode = mode(mode)
        }
        if (VBR) {
            gfp!!.VBR = VbrMode.vbr_default
            gfp!!.VBR_q = quality
        } else {
            if (bitrate != BITRATE_AUTO) {
                gfp!!.brate = bitrate
            }
        }
        gfp!!.quality = quality

        id3.id3tag_init(gfp!!)
        /*
		 * turn off automatic writing of ID3 tag data into mp3 stream we have to
		 * call it before 'lame_init_params', because that function would spit
		 * out ID3v2 tag data.
		 */
        gfp!!.write_id3tag_automatic = false
        gfp!!.findReplayGain = true

        val rc = lame.lame_init_params(gfp!!)
        // return effective values
        effectiveSampleRate = gfp!!.out_samplerate
        effectiveBitRate = gfp!!.brate
        effectiveChannelMode = gfp!!.mode.ordinal
        effVbr = gfp!!.VBR!!.ordinal
        effQuality = if (VBR) gfp!!.VBR_q else gfp!!.quality
        return rc
    }

    private fun doEncodeBuffer(pcm: ByteArray, offset: Int, length: Int, encoded: ByteArray): Int {
        val bytes_per_sample = sourceSampleSizeInBits shr 3
        var samples_read = length / bytes_per_sample


        val sample_buffer = IntArray(samples_read)

        var sample_index = samples_read
        if (sourceEncoding!!.toString() != "PCM_FLOAT") {
            if (sourceByteOrder == ByteOrder.LITTLE_ENDIAN) {
                if (bytes_per_sample == 1) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = pcm[offset + i] and 0xff shl 24
                        i -= bytes_per_sample
                    }
                }
                if (bytes_per_sample == 2) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = pcm[offset + i] and 0xff shl 16 or (pcm[offset + i + 1] and 0xff shl 24)
                        i -= bytes_per_sample
                    }
                }
                if (bytes_per_sample == 3) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = (pcm[offset + i] and 0xff shl 8
                                or (pcm[offset + i + 1] and 0xff shl 16)
                                or (pcm[offset + i + 2] and 0xff shl 24))
                        i -= bytes_per_sample
                    }
                }
                if (bytes_per_sample == 4) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = (pcm[offset + i] and 0xff
                                or (pcm[offset + i + 1] and 0xff shl 8)
                                or (pcm[offset + i + 2] and 0xff shl 16)
                                or (pcm[offset + i + 3] and 0xff shl 24))
                        i -= bytes_per_sample
                    }
                }
            } else {
                if (bytes_per_sample == 1) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = pcm[offset + i] and 0xff xor 0x80 shl 24 or (0x7f shl 16)
                        i -= bytes_per_sample
                    }
                }
                if (bytes_per_sample == 2) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = pcm[offset + i] and 0xff shl 24 or (pcm[offset + i + 1] and 0xff shl 16)
                        i -= bytes_per_sample
                    }
                }
                if (bytes_per_sample == 3) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = (pcm[offset + i] and 0xff shl 24
                                or (pcm[offset + i + 1] and 0xff shl 16)
                                or (pcm[offset + i + 2] and 0xff shl 8))
                        i -= bytes_per_sample
                    }
                }
                if (bytes_per_sample == 4) {
                    var i = samples_read * bytes_per_sample
                    i -= bytes_per_sample
                    while (i >= 0) {
                        sample_buffer[--sample_index] = (pcm[offset + i] and 0xff shl 24
                                or (pcm[offset + i + 1] and 0xff shl 16)
                                or (pcm[offset + i + 2] and 0xff shl 8)
                                or (pcm[offset + i + 3] and 0xff))
                        i -= bytes_per_sample
                    }
                }
            }
        } else {
            if (bytes_per_sample == 4) {
                var i = samples_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    val sample = ByteArray(4)
                    sample[0] = pcm[offset + i]
                    sample[1] = pcm[offset + i + 1]
                    sample[2] = pcm[offset + i + 2]
                    sample[3] = pcm[offset + i + 3]
                    val amlitude = convertByteArrayToFloat(sample, 0, sourceByteOrder)
                    if (Math.abs(amlitude) >= 1.0) continue
                    val sampleInt = Math.round(Integer.MAX_VALUE * amlitude)
                    sample_buffer[--sample_index] = sampleInt
                    //					System.out.print(sample_buffer[--sample_index]=sampleInt);
                    //					System.out.println(Arrays.toString(sample));
                    i -= bytes_per_sample
                }
            }
        }
        var p = samples_read
        samples_read /= gfp!!.num_channels

        val buffer = Array(2) { IntArray(samples_read) }
        if (gfp!!.num_channels == 2) {
            var i = samples_read
            while (--i >= 0) {
                buffer[1][i] = sample_buffer[--p]
                buffer[0][i] = sample_buffer[--p]
            }
        } else if (gfp!!.num_channels == 1) {
            Arrays.fill(buffer[1], 0, samples_read, 0)
            var i = samples_read
            while (--i >= 0) {
                buffer[1][i] = sample_buffer[--p]
                buffer[0][i] = buffer[1][i]
            }
        }

        return lame.lame_encode_buffer_int(gfp!!, buffer[0], buffer[1], samples_read, encoded, 0, encoded.size)
    }

    /**
     * Encode a block of data. Throws IllegalArgumentException when parameters
     * are wrong. When the `encoded` array is too small, an
     * ArrayIndexOutOfBoundsException is thrown. `length` should be
     * the value returned by getPCMBufferSize.
     *
     * @return the number of bytes written to `encoded`. May be 0.
     */
    @Throws(ArrayIndexOutOfBoundsException::class)
    fun encodeBuffer(pcm: ByteArray, offset: Int, length: Int, encoded: ByteArray): Int {
        if (length < 0 || offset + length > pcm.size) {
            throw IllegalArgumentException("inconsistent parameters")
        }
        val result = doEncodeBuffer(pcm, offset, length, encoded)
        if (result < 0) {
            if (result == -1) {
                throw ArrayIndexOutOfBoundsException(
                        "Encode buffer too small")
            }
            throw RuntimeException("crucial error in encodeBuffer.")
        }
        return result
    }

    fun encodeFinish(encoded: ByteArray): Int {
        return lame.lame_encode_flush(gfp!!, encoded, 0, encoded.size)
    }

    fun close() {
        lame.lame_close(gfp)
    }

    // properties
    private fun readProps(props: Map<String, Any>) {
        var q: Any? = props[P_QUALITY]
        if (q is String) {
            quality = string2quality(q.toLowerCase(), quality)
        } else if (q is Int) {
            quality = (q as Int?)!!
        } else if (q != null) {
            throw IllegalArgumentException(
                    "illegal type of quality property: $q")
        }
        q = props[P_BITRATE]
        if (q is String) {
            bitRate = Integer.parseInt((q as String?)!!)
        } else if (q is Int) {
            bitRate = (q as Int?)!!
        } else if (q != null) {
            throw IllegalArgumentException(
                    "illegal type of bitrate property: $q")
        }
        q = props[P_CHMODE]
        if (q is String) {
            chMode = string2chmode(q.toLowerCase(), chMode)
        } else if (q != null) {
            throw IllegalArgumentException(
                    "illegal type of chmode property: $q")
        }
        q = props[P_VBR]
        if (q is String) {
            vbrMode = string2bool(q)
        } else if (q is Boolean) {
            vbrMode = (q as Boolean?)!!
        } else if (q != null) {
            throw IllegalArgumentException("illegal type of vbr property: $q")
        }
    }

    private fun string2quality(quality: String, def: Int): Int {
        if (quality == "lowest") {
            return QUALITY_LOWEST
        } else if (quality == "low") {
            return QUALITY_LOW
        } else if (quality == "middle") {
            return QUALITY_MIDDLE
        } else if (quality == "high") {
            return QUALITY_HIGH
        } else if (quality == "highest") {
            return QUALITY_HIGHEST
        }
        return def
    }


    private fun string2chmode(chmode: String, def: Int): Int {
        if (chmode == "stereo") {
            return CHANNEL_MODE_STEREO
        } else if (chmode == "jointstereo") {
            return CHANNEL_MODE_JOINT_STEREO
        } else if (chmode == "dual") {
            return CHANNEL_MODE_DUAL_CHANNEL
        } else if (chmode == "mono") {
            return CHANNEL_MODE_MONO
        } else if (chmode == "auto") {
            return CHANNEL_MODE_AUTO
        }
        return def
    }

    fun convertByteArrayToFloat(bytes: ByteArray, offset: Int, byteOrder: ByteOrder?): Float {
        val byte0 = bytes[offset + 0].toInt()
        val byte1 = bytes[offset + 1].toInt()
        val byte2 = bytes[offset + 2].toInt()
        val byte3 = bytes[offset + 3].toInt()
        val bits: Int
        if (byteOrder == ByteOrder.BIG_ENDIAN)
        //big endian
        {
            bits = 0xff and byte0 shl 24 or (0xff and byte1 shl 16) or (0xff and byte2 shl 8) or (0xff and byte3 shl 0)
        } else {
            // little endian
            bits = 0xff and byte3 shl 24 or (0xff and byte2 shl 16) or (0xff and byte1 shl 8) or (0xff and byte0 shl 0)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }

    fun getSourceFormat(): AudioFormat? {
        return sourceFormat
    }

    fun getTargetFormat(): AudioFormat? {
        return targetFormat
    }

    companion object {

        val MPEG1L3 = AudioFormat.Encoding(
                "MPEG1L3")
        // Lame converts automagically to MPEG2 or MPEG2.5, if necessary.
        val MPEG2L3 = AudioFormat.Encoding(
                "MPEG2L3")
        val MPEG2DOT5L3 = AudioFormat.Encoding(
                "MPEG2DOT5L3")

        // property constants
        /**
         * property key to read/set the VBR mode: an instance of Boolean (default:
         * false)
         */
        val P_VBR = "vbr"
        /**
         * property key to read/set the channel mode: a String, one of
         * &quot;jointstereo&quot;, &quot;dual&quot;, &quot;mono&quot;,
         * &quot;auto&quot; (default).
         */
        val P_CHMODE = "chmode"
        /**
         * property key to read/set the bitrate: an Integer value. Set to -1 for
         * default bitrate.
         */
        val P_BITRATE = "bitrate"
        /**
         * property key to read/set the quality: an Integer from 1 (highest) to 9
         * (lowest).
         */
        val P_QUALITY = "quality"

        // constants from lame.h
        val MPEG_VERSION_2 = 0 // MPEG-2
        val MPEG_VERSION_1 = 1 // MPEG-1
        val MPEG_VERSION_2DOT5 = 2 // MPEG-2.5

        // low mean bitrate in VBR mode
        val QUALITY_LOWEST = 9
        val QUALITY_LOW = 7
        val QUALITY_MIDDLE = 5
        val QUALITY_HIGH = 2
        // quality==0 not yet coded in LAME (3.83alpha)
        // high mean bitrate in VBR // mode
        val QUALITY_HIGHEST = 1

        val CHANNEL_MODE_STEREO = 0
        val CHANNEL_MODE_JOINT_STEREO = 1
        val CHANNEL_MODE_DUAL_CHANNEL = 2
        val CHANNEL_MODE_MONO = 3

        // channel mode has no influence on mono files.
        val CHANNEL_MODE_AUTO = -1
        val BITRATE_AUTO = -1

        // suggested maximum buffer size for an mpeg frame
        private val DEFAULT_PCM_BUFFER_SIZE = 2048 * 16

        // frame size=576 for MPEG2 and MPEG2.5
        // =576*2 for MPEG1

        private val DEFAULT_QUALITY = QUALITY_MIDDLE
        private val DEFAULT_BITRATE = BITRATE_AUTO
        private val DEFAULT_CHANNEL_MODE = CHANNEL_MODE_AUTO
        // in VBR mode, bitrate is ignored.
        private val DEFAULT_VBR = false

        fun chmode2string(chmode: Int): String {
            if (chmode == CHANNEL_MODE_STEREO) {
                return "stereo"
            } else if (chmode == CHANNEL_MODE_JOINT_STEREO) {
                return "jointstereo"
            } else if (chmode == CHANNEL_MODE_DUAL_CHANNEL) {
                return "dual"
            } else if (chmode == CHANNEL_MODE_MONO) {
                return "mono"
            } else if (chmode == CHANNEL_MODE_AUTO) {
                return "auto"
            }
            return "auto"
        }

        fun mode(chmode: Int): MPEGMode {

            if (chmode == CHANNEL_MODE_STEREO) {
                return MPEGMode.STEREO
            } else if (chmode == CHANNEL_MODE_JOINT_STEREO) {
                return MPEGMode.JOINT_STEREO
            } else if (chmode == CHANNEL_MODE_DUAL_CHANNEL) {
                return MPEGMode.DUAL_CHANNEL
            } else if (chmode == CHANNEL_MODE_MONO) {
                return MPEGMode.MONO
            } else if (chmode == CHANNEL_MODE_AUTO) {
                return MPEGMode.NOT_SET
            }
            return MPEGMode.NOT_SET
        }

        /**
         * @return true if val is starts with t or y or on, false if val starts with
         * f or n or off.
         * @throws IllegalArgumentException
         * if val is neither true nor false
         */
        private fun string2bool(`val`: String): Boolean {
            if (`val`.length > 0) {
                if (`val`[0] == 'f' // false

                        || `val`[0] == 'n' // no

                        || `val` == "off") {
                    return false
                }
                if (`val`[0] == 't' // true

                        || `val`[0] == 'y' // yes

                        || `val` == "on") {
                    return true
                }
            }
            throw IllegalArgumentException(
                    "wrong string for boolean property: $`val`")
        }
    }

    /** * Lame.java **  */

}
