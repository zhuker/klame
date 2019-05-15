/*
 *      LAME MP3 encoding engine
 *
 *      Copyright (c) 1999-2000 Mark Taylor
 *      Copyright (c) 2003 Olcios
 *      Copyright (c) 2008 Robert Hegemann
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
package mpg

import jdk.assert
import mp3.Enc
import mp3.MP3Data
import mp3.PlottingData
import mpg.Decode.Factory

class MPGLib {

    internal lateinit var interf: Interface
    internal lateinit var common: Common

    fun setModules(i: Interface, c: Common) {
        interf = i
        common = c
    }

    class buf {
        var pnt: ByteArray? = null
        var size: Int = 0
        var pos: Int = 0
        var next: buf? = null
        var prev: buf? = null
    }

    class framebuf {
        internal var buf: buf? = null
        internal var pos: Long = 0
        internal var next: Frame? = null
        internal var prev: Frame? = null
    }


    class mpstr_tag {
        var head: buf? = null
        var tail: buf? = null /* buffer linked list pointers, tail points to oldest buffer */
        var vbr_header: Boolean = false      /* 1 if valid Xing vbr header detected */
        var num_frames: Int = 0      /* set if vbr header present */
        var enc_delay: Int = 0       /* set if vbr header present */
        var enc_padding: Int = 0     /* set if vbr header present */
        /* header_parsed, side_parsed and data_parsed must be all set 1
	       before the full frame has been parsed */
        var header_parsed: Boolean = false   /* 1 = header of current frame has been parsed */
        var side_parsed: Boolean = false     /* 1 = header of sideinfo of current frame has been parsed */
        var data_parsed: Boolean = false
        var free_format: Boolean = false     /* 1 = free format frame */
        var old_free_format: Boolean = false /* 1 = last frame was free format */
        var bsize: Int = 0
        var framesize: Int = 0
        var ssize: Int = 0           /* number of bytes used for side information, including 2 bytes for CRC-16 if present */
        var dsize: Int = 0
        var fsizeold: Int = 0        /* size of previous frame, -1 for first */
        var fsizeold_nopadding: Int = 0
        var fr = Frame()         /* holds the parameters decoded from the header */
        var bsspace = Array(2) { ByteArray(MPG123.MAXFRAMESIZE + 1024) } /* bit stream space used ???? */ /* MAXFRAMESIZE */
        var hybrid_block = Array(2) { Array(2) { FloatArray(MPG123.SBLIMIT * MPG123.SSLIMIT) } }
        var hybrid_blc = IntArray(2)
        var header: Long = 0
        var bsnum: Int = 0
        var synth_buffs = Array(2) { Array(2) { FloatArray(0x110) } }
        var synth_bo: Int = 0
        var sync_bitstream: Int = 0  /* 1 = bitstream is yet to be synchronized */
        var bitindex: Int = 0
        var wordpointer: ByteArray? = null
        var wordpointerPos: Int = 0
        var pinfo: PlottingData? = null
    }

    /* copy mono samples */
    protected fun <DST_TYPE, SRC_TYPE> COPY_MONO(pcm_l: Array<DST_TYPE>,
                                                 pcm_lPos: Int, processed_samples: Int, p: Array<SRC_TYPE>) {
        var pcm_lPos = pcm_lPos
        var p_samples = 0
        for (i in 0 until processed_samples)
            pcm_l[pcm_lPos++] = p[p_samples++] as DST_TYPE
    }

    /* copy stereo samples */
    protected fun <DST_TYPE, SRC_TYPE> COPY_STEREO(pcm_l: Array<DST_TYPE>,
                                                   pcm_lPos: Int, pcm_r: Array<DST_TYPE>, pcm_rPos: Int,
                                                   processed_samples: Int, p: Array<SRC_TYPE>) {
        var pcm_lPos = pcm_lPos
        var pcm_rPos = pcm_rPos
        var p_samples = 0
        for (i in 0 until processed_samples) {
            pcm_l[pcm_lPos++] = p[p_samples++] as DST_TYPE
            pcm_r[pcm_rPos++] = p[p_samples++] as DST_TYPE
        }
    }

    class ProcessedBytes {
        var pb: Int = 0
    }

    internal interface IDecoder {
        fun <T> decode(mp: mpstr_tag, `in`: ByteArray, bufferPos: Int, isize: Int, out: Array<T>, osize: Int, done: ProcessedBytes, tFactory: Factory<T>): Int
    }

    /*
     * For lame_decode:  return code
     * -1     error
     *  0     ok, but need more data before outputing any samples
     *  n     number of samples output.  either 576 or 1152 depending on MP3 file.
     */

    private fun <S, T> decode1_headersB_clipchoice(pmp: mpstr_tag, buffer: ByteArray,
                                                   bufferPos: Int, len: Int, pcm_l: Array<S>, pcm_lPos: Int, pcm_r: Array<S>, pcm_rPos: Int, mp3data: MP3Data,
                                                   enc: Enc, p: Array<T>, psize: Int,
                                                   decodeMP3_ptr: IDecoder, tFactory: Factory<T>): Int {

        var processed_samples: Int /* processed samples per channel */
        val ret: Int

        mp3data.header_parsed = false

        val pb = ProcessedBytes()
        ret = decodeMP3_ptr.decode(pmp, buffer, bufferPos, len, p, psize, pb, tFactory)
        processed_samples = pb.pb
        /* three cases:
         * 1. headers parsed, but data not complete
         *       pmp.header_parsed==1
         *       pmp.framesize=0
         *       pmp.fsizeold=size of last frame, or 0 if this is first frame
         *
         * 2. headers, data parsed, but ancillary data not complete
         *       pmp.header_parsed==1
         *       pmp.framesize=size of frame
         *       pmp.fsizeold=size of last frame, or 0 if this is first frame
         *
         * 3. frame fully decoded:
         *       pmp.header_parsed==0
         *       pmp.framesize=0
         *       pmp.fsizeold=size of frame (which is now the last frame)
         *
         */
        if (pmp.header_parsed || pmp.fsizeold > 0 || pmp.framesize > 0) {
            mp3data.header_parsed = true
            mp3data.stereo = pmp.fr.stereo
            mp3data.samplerate = Common.freqs[pmp.fr.sampling_frequency]
            mp3data.mode = pmp.fr.mode
            mp3data.mode_ext = pmp.fr.mode_ext
            mp3data.framesize = smpls[pmp.fr.lsf][pmp.fr.lay]

            /* free format, we need the entire frame before we can determine
             * the bitrate.  If we haven't gotten the entire frame, bitrate=0 */
            if (pmp.fsizeold > 0)
            /* works for free format and fixed, no overrun, temporal results are < 400.e6 */
                mp3data.bitrate = (8 * (4 + pmp.fsizeold) * mp3data.samplerate / (1e3 * mp3data.framesize) + 0.5).toInt()
            else if (pmp.framesize > 0)
                mp3data.bitrate = (8 * (4 + pmp.framesize) * mp3data.samplerate / (1e3 * mp3data.framesize) + 0.5).toInt()
            else
                mp3data.bitrate = Common.tabsel_123[pmp.fr.lsf][pmp.fr.lay - 1][pmp.fr.bitrate_index]



            if (pmp.num_frames > 0) {
                /* Xing VBR header found and num_frames was set */
                mp3data.totalframes = pmp.num_frames
                mp3data.nsamp = mp3data.framesize * pmp.num_frames
                enc.enc_delay = pmp.enc_delay
                enc.enc_padding = pmp.enc_padding
            }
        }

        when (ret) {
            MP3_OK -> when (pmp.fr.stereo) {
                1 -> COPY_MONO(pcm_l, pcm_lPos, processed_samples, p)
                2 -> {
                    processed_samples = processed_samples shr 1
                    COPY_STEREO(pcm_l, pcm_lPos, pcm_r, pcm_rPos, processed_samples, p)
                }
                else -> {
                    processed_samples = -1
                    assert(false)
                }
            }

            MP3_NEED_MORE -> processed_samples = 0

            MP3_ERR -> processed_samples = -1

            else -> {
                processed_samples = -1
                assert(false)
            }
        }

        /*fprintf(stderr,"ok, more, err:  %i %i %i\n", MP3_OK, MP3_NEED_MORE, MP3_ERR ); */
        /*fprintf(stderr,"ret = %i out=%i\n", ret, processed_samples ); */
        return processed_samples
    }

    fun hip_decode_init(): mpstr_tag {
        return interf.InitMP3()
    }


    fun hip_decode_exit(hip: mpstr_tag?): Int {
        var hip = hip
        if (hip != null) {
            interf.ExitMP3(hip)
            hip = null
        }
        return 0
    }

    /*
	 * same as hip_decode1 (look in lame.h), but returns unclipped raw
	 * floating-point samples. It is declared here, not in lame.h, because it
	 * returns LAME's internal type sample_t. No more than 1152 samples per
	 * channel are allowed.
	 */
    fun hip_decode1_unclipped(hip: mpstr_tag?, buffer: ByteArray, bufferPos: Int,
                              len: Int, pcm_l: FloatArray, pcm_r: FloatArray): Int {

        val mp3data = MP3Data()
        val enc = Enc()

        if (hip != null) {
            val dec = object : IDecoder {

                override fun <X> decode(mp: mpstr_tag, `in`: ByteArray, bufferPos: Int, isize: Int,
                                        out: Array<X>, osize: Int, done: ProcessedBytes, tFactory: Factory<X>): Int {
                    return interf.decodeMP3_unclipped(mp, `in`, bufferPos, isize, out, osize, done, tFactory)
                }
            }
            val out = Array<Float>(OUTSIZE_UNCLIPPED) { 0f }
            val tFactory = object : Factory<Float> {
                override fun create(x: Float): Float {
                    return x
                }
            }
            // XXX should we avoid the primitive type version?
            val pcmL = Array<Float>(pcm_l.size) { i -> pcm_l[i] }
            val pcmR = Array<Float>(pcm_r.size) { i -> pcm_r[i] }
            val decode1_headersB_clipchoice = decode1_headersB_clipchoice<Float, Float>(hip, buffer, bufferPos, len,
                    pcmL, 0, pcmR, 0, mp3data, enc, out, OUTSIZE_UNCLIPPED, dec, tFactory)
            for (i in pcmL.indices) {
                pcm_l[i] = pcmL[i]
            }
            for (i in pcmR.indices) {
                pcm_r[i] = pcmR[i]
            }
            return decode1_headersB_clipchoice
        }
        return 0
    }

    /*
	 * For lame_decode:  return code
	 *  -1     error
	 *   0     ok, but need more data before outputing any samples
	 *   n     number of samples output.  Will be at most one frame of
	 *         MPEG data.
	 */
    fun hip_decode1_headers(hip: mpstr_tag, buffer: ByteArray,
                            len: Int, pcm_l: ShortArray, pcm_r: ShortArray, mp3data: MP3Data): Int {
        val enc = Enc()
        return hip_decode1_headersB(hip, buffer, len, pcm_l, pcm_r, mp3data, enc)
    }

    fun hip_decode1_headersB(hip: mpstr_tag?, buffer: ByteArray,
                             len: Int,
                             pcm_l: ShortArray, pcm_r: ShortArray, mp3data: MP3Data,
                             enc: Enc): Int {
        if (hip != null) {
            val dec = object : IDecoder {

                override fun <X> decode(mp: mpstr_tag, `in`: ByteArray, bufferPos: Int, isize: Int,
                                        out: Array<X>, osize: Int, done: ProcessedBytes, tFactory: Factory<X>): Int {
                    return interf.decodeMP3(mp, `in`, bufferPos, isize, out, osize, done, tFactory)
                }
            }
            val out = Array<Short>(OUTSIZE_CLIPPED) { 0.toShort() }
            val tFactory = object : Factory<Short> {
                override fun create(x: Float): Short {
                    return x.toShort()
                }
            }
            // XXX should we avoid the primitive type version?
            val pcmL = Array<Short>(pcm_l.size) { i -> pcm_l[i] }
            val pcmR = Array<Short>(pcm_r.size) { i -> pcm_r[i] }
            val decode1_headersB_clipchoice = decode1_headersB_clipchoice<Short, Short>(hip, buffer, 0, len, pcmL, 0, pcmR, 0, mp3data,
                    enc, out, OUTSIZE_CLIPPED,
                    dec, tFactory)
            for (i in pcmL.indices) {
                pcm_l[i] = pcmL[i]
            }
            for (i in pcmR.indices) {
                pcm_r[i] = pcmR[i]
            }
            return decode1_headersB_clipchoice
        }
        return -1
    }

    internal fun hip_set_pinfo(hip: mpstr_tag?, pinfo: PlottingData) {
        if (hip != null) {
            hip.pinfo = pinfo
        }
    }

    companion object {

        const val MP3_ERR = -1
        const val MP3_OK = 0
        const val MP3_NEED_MORE = 1

        private val smpls = arrayOf(
                /* Layer   I    II   III */
                intArrayOf(0, 384, 1152, 1152), /* MPEG-1     */
                intArrayOf(0, 384, 1152, 576) /* MPEG-2(.5) */)

        private const val OUTSIZE_CLIPPED = 4096

        /* we forbid input with more than 1152 samples per channel for output in the unclipped mode */
        private const val OUTSIZE_UNCLIPPED = 1152 * 2
    }

}
