/*
 * interface.c
 *
 * Copyright (C) 1999-2010 The L.A.M.E. project
 *
 * Initially written by Michael Hipp, see also AUTHORS and README.
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
/* $Id: Interface.java,v 1.12 2011/08/27 18:57:09 kenchis Exp $ */

package mpg

import jdk.System
import jdk.and
import mp3.VBRTag
import mpg.Decode.Factory
import mpg.MPGLib.ProcessedBytes
import mpg.MPGLib.buf
import mpg.MPGLib.mpstr_tag

class Interface {

    private var vbr: VBRTag? = null
    private var common: Common? = null

    protected var decode = Decode()
    private val layer1 = Layer1()
    private val layer2 = Layer2()
    private val layer3 = Layer3()
    private val dct = DCT64()
    private val tab = TabInit()

    fun setModules(v: VBRTag, c: Common) {
        vbr = v
        common = c
        layer1.setModules(c, decode)
        layer2.setModules(c)
        layer3.setModules(c)
        decode.setModules(tab, dct)
        dct.setModules(tab)
    }

    internal fun InitMP3(): mpstr_tag {
        val mp = mpstr_tag()

        mp.framesize = 0
        mp.num_frames = 0
        mp.enc_delay = -1
        mp.enc_padding = -1
        mp.vbr_header = false
        mp.header_parsed = false
        mp.side_parsed = false
        mp.data_parsed = false
        mp.free_format = false
        mp.old_free_format = false
        mp.ssize = 0
        mp.dsize = 0
        mp.fsizeold = -1
        mp.bsize = 0
        mp.tail = null
        mp.head = mp.tail
        mp.fr.single = -1
        mp.bsnum = 0
        mp.wordpointer = mp.bsspace[mp.bsnum]
        mp.wordpointerPos = 512
        mp.bitindex = 0
        mp.synth_bo = 1
        mp.sync_bitstream = 1

        tab.make_decode_tables(32767)

        layer3.init_layer3(MPG123.SBLIMIT)

        layer2.init_layer2()

        return mp
    }

    internal fun ExitMP3(mp: mpstr_tag) {
        var b: buf?
        var bn: buf?

        b = mp.tail
        while (b != null) {
            b.pnt = null
            bn = b.next
            b = bn
        }
    }

    internal fun addbuf(mp: mpstr_tag, buf: ByteArray, bufPos: Int, size: Int): buf {
        val nbuf: buf

        nbuf = buf()
        nbuf.pnt = ByteArray(size)
        nbuf.size = size
        System.arraycopy(buf, bufPos, nbuf.pnt!!, 0, size)
        nbuf.next = null
        nbuf.prev = mp.head
        nbuf.pos = 0

        if (null == mp.tail) {
            mp.tail = nbuf
        } else {
            mp.head!!.next = nbuf
        }

        mp.head = nbuf
        mp.bsize += size

        return nbuf
    }

    internal fun remove_buf(mp: mpstr_tag) {
        var buf = mp.tail

        mp.tail = buf!!.next
        if (mp.tail != null)
            mp.tail!!.prev = null
        else {
            mp.head = null
            mp.tail = mp.head
        }

        buf.pnt = null
        buf = null

    }

    internal fun read_buf_byte(mp: mpstr_tag): Int {
        val b: Int

        var pos: Int


        pos = mp.tail!!.pos
        while (pos >= mp.tail!!.size) {
            remove_buf(mp)
            if (null == mp.tail) {
                throw RuntimeException("hip: Fatal error! tried to read past mp buffer")
            }
            pos = mp.tail!!.pos
        }

        b = mp.tail!!.pnt!![pos] and 0xff
        mp.bsize--
        mp.tail!!.pos++


        return b
    }

    internal fun read_head(mp: mpstr_tag) {
        var head: Long

        head = read_buf_byte(mp).toLong()
        head = head shl 8
        head = head or read_buf_byte(mp).toLong()
        head = head shl 8
        head = head or read_buf_byte(mp).toLong()
        head = head shl 8
        head = head or read_buf_byte(mp).toLong()

        mp.header = head
    }

    internal fun copy_mp(mp: mpstr_tag, size: Int, ptr: ByteArray?, ptrPos: Int) {
        var len = 0

        while (len < size && mp.tail != null) {
            val nlen: Int
            val blen = mp.tail!!.size - mp.tail!!.pos
            if (size - len <= blen) {
                nlen = size - len
            } else {
                nlen = blen
            }
            System.arraycopy(mp.tail!!.pnt!!, mp.tail!!.pos, ptr!!, ptrPos + len, nlen)
            len += nlen
            mp.tail!!.pos += nlen
            mp.bsize -= nlen
            if (mp.tail!!.pos == mp.tail!!.size) {
                remove_buf(mp)
            }
        }
    }

    /*
	traverse mp data structure without changing it
	(just like sync_buffer)
	pull out Xing bytes
	call vbr header check code from LAME
	if we find a header, parse it and also compute the VBR header size
	if no header, do nothing.

	bytes = number of bytes before MPEG header.  skip this many bytes
	before starting to read
	return value: number of bytes in VBR header, including syncword
	*/
    internal fun check_vbr_header(mp: mpstr_tag, bytes: Int): Int {
        var i: Int
        var pos: Int
        var buf = mp.tail
        val xing = ByteArray(XING_HEADER_SIZE)

        pos = buf!!.pos
        /* skip to valid header */
        i = 0
        while (i < bytes) {
            while (pos >= buf!!.size) {
                buf = buf.next
                if (null == buf)
                    return -1 /* fatal error */
                pos = buf.pos
            }
            ++pos
            ++i
        }
        /* now read header */
        i = 0
        while (i < XING_HEADER_SIZE) {
            while (pos >= buf!!.size) {
                buf = buf.next
                if (null == buf)
                    return -1 /* fatal error */
                pos = buf.pos
            }
            xing[i] = buf.pnt!![pos]
            ++pos
            ++i
        }

        /* check first bytes for Xing header */
        val pTagData = vbr!!.getVbrTag(xing)
        mp.vbr_header = pTagData != null
        if (mp.vbr_header) {
            mp.num_frames = pTagData!!.frames
            mp.enc_delay = pTagData.encDelay
            mp.enc_padding = pTagData.encPadding

            /* fprintf(stderr,"hip: delays: %i %i \n",mp.enc_delay,mp.enc_padding); */
            /* fprintf(stderr,"hip: Xing VBR header dectected.  MP3 file has %i frames\n", pTagData.frames); */
            return if (pTagData.headersize < 1) 1 else pTagData.headersize
        }
        return 0
    }

    internal fun sync_buffer(mp: mpstr_tag, free_match: Boolean): Int {
        /* traverse mp structure without modifying pointers, looking
	     * for a frame valid header.
	     * if free_format, valid header must also have the same
	     * samplerate.
	     * return number of bytes in mp, before the header
	     * return -1 if header is not found
	     */
        val b = intArrayOf(0, 0, 0, 0)
        var i: Int
        var pos: Int
        var h: Boolean
        var buf: buf? = mp.tail ?: return -1

        pos = buf!!.pos
        i = 0
        while (i < mp.bsize) {
            /* get 4 bytes */

            b[0] = b[1]
            b[1] = b[2]
            b[2] = b[3]
            while (pos >= buf!!.size) {
                buf = buf.next
                pos = buf!!.pos
            }
            b[3] = buf.pnt!![pos] and 0xff
            ++pos

            if (i >= 3) {
                val fr = mp.fr
                var head: Long

                head = b[0].toLong()
                head = head shl 8
                head = head or b[1].toLong()
                head = head shl 8
                head = head or b[2].toLong()
                head = head shl 8
                head = head or b[3].toLong()
                h = common!!.head_check(head, fr.lay)

                if (h && free_match) {
                    /* just to be even more thorough, match the sample rate */
                    val mode: Int
                    val stereo: Int
                    val sampling_frequency: Int
                    val lsf: Int
                    val mpeg25: Boolean

                    if (head and (1 shl 20) != 0L) {
                        lsf = if (head and (1 shl 19) != 0L) 0x0 else 0x1
                        mpeg25 = false
                    } else {
                        lsf = 1
                        mpeg25 = true
                    }

                    mode = (head shr 6 and 0x3).toInt()
                    stereo = if (mode == MPG123.MPG_MD_MONO) 1 else 2

                    if (mpeg25)
                        sampling_frequency = (6 + (head shr 10 and 0x3)).toInt()
                    else
                        sampling_frequency = ((head shr 10 and 0x3) + lsf * 3).toInt()
                    h = stereo == fr.stereo && lsf == fr.lsf && mpeg25 == fr.mpeg25 &&
                            sampling_frequency == fr.sampling_frequency
                }

                if (h) {
                    return i - 3
                }
            }
            i++
        }
        return -1
    }

    internal fun decode_reset(): mpstr_tag {
        return InitMP3()        /* Less error prone to just to reinitialise. */
    }

    internal fun audiodata_precedesframes(mp: mpstr_tag): Int {
        return if (mp.fr.lay == 3)
            layer3.layer3_audiodata_precedesframes(mp)
        else
            0       /* For Layer 1 & 2 the audio data starts at the frame that describes it, so no audio data precedes. */
    }

    interface ISynth {
        fun <T> synth_1to1_mono_ptr(mp: mpstr_tag, `in`: FloatArray, inPos: Int, out: Array<T>, p: ProcessedBytes, tFactory: Factory<T>): Int
        fun <T> synth_1to1_ptr(mp: mpstr_tag, `in`: FloatArray, inPos: Int, i: Int, out: Array<T>, p: ProcessedBytes, tFactory: Factory<T>): Int
    }

    internal fun <T> decodeMP3_clipchoice(mp: mpstr_tag, `in`: ByteArray?, inPos: Int, isize: Int, out: Array<T>, done: ProcessedBytes,
                                          synth: ISynth, tFactory: Factory<T>): Int {
        var i: Int
        var iret: Int
        var bits: Int
        var bytes: Int

        if (`in` != null && isize != 0 && addbuf(mp, `in`, inPos, isize) == null)
            return MPGLib.MP3_ERR

        /* First decode header */
        if (!mp.header_parsed) {

            if (mp.fsizeold == -1 || mp.sync_bitstream != 0) {
                val vbrbytes: Int
                mp.sync_bitstream = 0

                /* This is the very first call.   sync with anything */
                /* bytes= number of bytes before header */
                bytes = sync_buffer(mp, false)

                /* now look for Xing VBR header */
                if (mp.bsize >= bytes + XING_HEADER_SIZE) {
                    /* vbrbytes = number of bytes in entire vbr header */
                    vbrbytes = check_vbr_header(mp, bytes)
                } else {
                    /* not enough data to look for Xing header */
                    //	#ifdef HIP_DEBUG
                    //	                fprintf(stderr, "hip: not enough data to look for Xing header\n");
                    //	#endif
                    return MPGLib.MP3_NEED_MORE
                }

                if (mp.vbr_header) {
                    /* do we have enough data to parse entire Xing header? */
                    if (bytes + vbrbytes > mp.bsize) {
                        /* fprintf(stderr,"hip: not enough data to parse entire Xing header\n"); */
                        return MPGLib.MP3_NEED_MORE
                    }

                    /* read in Xing header.  Buffer data in case it
	                 * is used by a non zero main_data_begin for the next
	                 * frame, but otherwise dont decode Xing header */
                    //	#ifdef HIP_DEBUG
                    //	                fprintf(stderr, "hip: found xing header, skipping %i bytes\n", vbrbytes + bytes);
                    //	#endif
                    i = 0
                    while (i < vbrbytes + bytes) {
                        read_buf_byte(mp)
                        ++i
                    }
                    /* now we need to find another syncword */
                    /* just return and make user send in more data */

                    return MPGLib.MP3_NEED_MORE
                }
            } else {
                /* match channels, samplerate, etc, when syncing */
                bytes = sync_buffer(mp, true)
            }

            /* buffer now synchronized */
            if (bytes < 0) {
                /* fprintf(stderr,"hip: need more bytes %d\n", bytes); */
                return MPGLib.MP3_NEED_MORE
            }
            if (bytes > 0) {
                /* there were some extra bytes in front of header.
	             * bitstream problem, but we are now resynced
	             * should try to buffer previous data in case new
	             * frame has nonzero main_data_begin, but we need
	             * to make sure we do not overflow buffer
	             */
                var size: Int
                System.err.printf("hip: bitstream problem, resyncing skipping %d bytes...\n", bytes)
                mp.old_free_format = false

                /* FIXME: correct ??? */
                mp.sync_bitstream = 1

                /* skip some bytes, buffer the rest */
                size = mp.wordpointerPos - 512

                if (size > MPG123.MAXFRAMESIZE) {
                    /* wordpointer buffer is trashed.  probably cant recover, but try anyway */
                    System.err.printf("hip: wordpointer trashed.  size=%i (%i)  bytes=%i \n",
                            size, MPG123.MAXFRAMESIZE, bytes)
                    size = 0
                    mp.wordpointer = mp.bsspace[mp.bsnum]
                    mp.wordpointerPos = 512
                }

                /* buffer contains 'size' data right now
	               we want to add 'bytes' worth of data, but do not
	               exceed MAXFRAMESIZE, so we through away 'i' bytes */
                i = size + bytes - MPG123.MAXFRAMESIZE
                while (i > 0) {
                    --bytes
                    read_buf_byte(mp)
                    --i
                }

                copy_mp(mp, bytes, mp.wordpointer, mp.wordpointerPos)
                mp.fsizeold += bytes
            }

            read_head(mp)
            common!!.decode_header(mp.fr, mp.header)
            mp.header_parsed = true
            mp.framesize = mp.fr.framesize
            mp.free_format = mp.framesize == 0

            if (mp.fr.lsf != 0)
                mp.ssize = if (mp.fr.stereo == 1) 9 else 17
            else
                mp.ssize = if (mp.fr.stereo == 1) 17 else 32
            if (mp.fr.error_protection)
                mp.ssize += 2

            mp.bsnum = 1 - mp.bsnum /* toggle buffer */
            mp.wordpointer = mp.bsspace[mp.bsnum]
            mp.wordpointerPos = 512
            mp.bitindex = 0

            /* for very first header, never parse rest of data */
            if (mp.fsizeold == -1) {
                //	#ifdef HIP_DEBUG
                //	            fprintf(stderr, "hip: not parsing the rest of the data of the first header\n");
                //	#endif
                return MPGLib.MP3_NEED_MORE
            }
        }                   /* end of header parsing block */

        /* now decode side information */
        if (!mp.side_parsed) {

            /* Layer 3 only */
            if (mp.fr.lay == 3) {
                if (mp.bsize < mp.ssize)
                    return MPGLib.MP3_NEED_MORE

                copy_mp(mp, mp.ssize, mp.wordpointer, mp.wordpointerPos)

                if (mp.fr.error_protection)
                    common!!.getbits(mp, 16)
                bits = layer3.do_layer3_sideinfo(mp)
                /* bits = actual number of bits needed to parse this frame */
                /* can be negative, if all bits needed are in the reservoir */
                if (bits < 0)
                    bits = 0

                /* read just as many bytes as necessary before decoding */
                mp.dsize = (bits + 7) / 8

                //	#ifdef HIP_DEBUG
                //	            fprintf(stderr,
                //	                    "hip: %d bits needed to parse layer III frame, number of bytes to read before decoding dsize = %d\n",
                //	                    bits, mp.dsize);
                //	#endif

                /* this will force mpglib to read entire frame before decoding */
                /* mp.dsize= mp.framesize - mp.ssize; */

            } else {
                /* Layers 1 and 2 */

                /* check if there is enough input data */
                if (mp.fr.framesize > mp.bsize)
                    return MPGLib.MP3_NEED_MORE

                /* takes care that the right amount of data is copied into wordpointer */
                mp.dsize = mp.fr.framesize
                mp.ssize = 0
            }

            mp.side_parsed = true
        }

        /* now decode main data */
        iret = MPGLib.MP3_NEED_MORE
        if (!mp.data_parsed) {
            if (mp.dsize > mp.bsize) {
                return MPGLib.MP3_NEED_MORE
            }

            copy_mp(mp, mp.dsize, mp.wordpointer, mp.wordpointerPos)

            done.pb = 0

            /*do_layer3(&mp.fr,(unsigned char *) out,done); */
            when (mp.fr.lay) {
                1 -> {
                    if (mp.fr.error_protection)
                        common!!.getbits(mp, 16)

                    layer1.do_layer1(mp, out, done, tFactory)
                }

                2 -> {
                    if (mp.fr.error_protection)
                        common!!.getbits(mp, 16)

                    layer2.do_layer2(mp, out, done, synth, tFactory)
                }

                3 -> layer3.do_layer3(mp, out, done, synth, tFactory)
                else -> System.err.printf("hip: invalid layer %d\n", mp.fr.lay)
            }

            mp.wordpointer = mp.bsspace[mp.bsnum]
            mp.wordpointerPos = 512 + mp.ssize + mp.dsize

            mp.data_parsed = true
            iret = MPGLib.MP3_OK
        }


        /* remaining bits are ancillary data, or reservoir for next frame
	     * If free format, scan stream looking for next frame to determine
	     * mp.framesize */
        if (mp.free_format) {
            if (mp.old_free_format) {
                /* free format.  bitrate must not vary */
                mp.framesize = mp.fsizeold_nopadding + mp.fr.padding
            } else {
                bytes = sync_buffer(mp, true)
                if (bytes < 0)
                    return iret
                mp.framesize = bytes + mp.ssize + mp.dsize
                mp.fsizeold_nopadding = mp.framesize - mp.fr.padding
                /*
	               fprintf(stderr,"hip: freeformat bitstream:  estimated bitrate=%ikbs  \n",
	               8*(4+mp.framesize)*freqs[mp.fr.sampling_frequency]/
	               (1000*576*(2-mp.fr.lsf)));
	             */
            }
        }

        /* buffer the ancillary data and reservoir for next frame */
        bytes = mp.framesize - (mp.ssize + mp.dsize)
        if (bytes > mp.bsize) {
            return iret
        }

        if (bytes > 0) {
            val size: Int
            copy_mp(mp, bytes, mp.wordpointer, mp.wordpointerPos)
            mp.wordpointerPos += bytes

            size = mp.wordpointerPos - 512
            if (size > MPG123.MAXFRAMESIZE) {
                System.err.printf("hip: fatal error.  MAXFRAMESIZE not large enough.\n")
            }

        }

        /* the above frame is completely parsed.  start looking for next frame */
        mp.fsizeold = mp.framesize
        mp.old_free_format = mp.free_format
        mp.framesize = 0
        mp.header_parsed = false
        mp.side_parsed = false
        mp.data_parsed = false

        return iret
    }

    internal fun <T> decodeMP3(mp: MPGLib.mpstr_tag, `in`: ByteArray, bufferPos: Int, isize: Int, out: Array<T>, osize: Int, done: ProcessedBytes, tFactory: Factory<T>): Int {
        if (osize < 2304) {
            System.err.printf("hip: Insufficient memory for decoding buffer %d\n", osize)
            return MPGLib.MP3_ERR
        }

        /* passing pointers to the functions which clip the samples */
        val synth = object : ISynth {

            override fun <X> synth_1to1_mono_ptr(mp: mpstr_tag, `in`: FloatArray,
                                                 inPos: Int, out: Array<X>, p: ProcessedBytes,
                                                 tFactory: Factory<X>): Int {
                return decode.synth_1to1_mono(mp, `in`, inPos, out, p, tFactory)
            }

            override fun <X> synth_1to1_ptr(mp: mpstr_tag, `in`: FloatArray, inPos: Int,
                                            i: Int, out: Array<X>, p: ProcessedBytes,
                                            tFactory: Factory<X>): Int {
                return decode.synth_1to1(mp, `in`, inPos, i, out, p, tFactory)
            }

        }
        return decodeMP3_clipchoice(mp, `in`, bufferPos, isize, out, done, synth, tFactory)
    }

    internal fun <T> decodeMP3_unclipped(mp: MPGLib.mpstr_tag, `in`: ByteArray, bufferPos: Int, isize: Int, out: Array<T>, osize: Int, done: ProcessedBytes, tFactory: Factory<T>): Int {
        /* we forbid input with more than 1152 samples per channel for output in unclipped mode */
        if (osize < 1152 * 2) {
            System.err.printf("hip: out space too small for unclipped mode\n")
            return MPGLib.MP3_ERR
        }

        val synth = object : ISynth {

            override fun <X> synth_1to1_mono_ptr(mp: mpstr_tag, `in`: FloatArray,
                                                 inPos: Int, out: Array<X>, p: ProcessedBytes,
                                                 tFactory: Factory<X>): Int {
                return decode.synth_1to1_mono_unclipped(mp, `in`, inPos, out, p, tFactory)
            }

            override fun <X> synth_1to1_ptr(mp: mpstr_tag, `in`: FloatArray, inPos: Int,
                                            i: Int, out: Array<X>, p: ProcessedBytes,
                                            tFactory: Factory<X>): Int {
                return decode.synth_1to1_unclipped(mp, `in`, inPos, i, out, p, tFactory)
            }

        }
        /* passing pointers to the functions which don't clip the samples */
        return decodeMP3_clipchoice(mp, `in`, bufferPos, isize, out, done, synth, tFactory)
    }

    companion object {

        /* number of bytes needed by GetVbrTag to parse header */
        val XING_HEADER_SIZE = 194
    }
}
