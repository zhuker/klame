/*
 * common.c: some common bitstream operations
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

/* $Id: Common.java,v 1.6 2011/08/27 18:57:09 kenchis Exp $ */

package mpg

import jdk.System
import jdk.and
import mpg.MPGLib.mpstr_tag

class Common {

    var muls = Array(27) { FloatArray(64) }

    fun head_check(head: Long, check_layer: Int): Boolean {
        /**
         * <PRE>
         * look for a valid header.
         * if check_layer > 0, then require that
         * nLayer = check_layer.
        </PRE> *
         */

        /* bits 13-14 = layer 3 */
        val nLayer = (4 - (head shr 17 and 3)).toInt()

        if (head and 0xffe00000L != 0xffe00000L) {
            /* syncword */
            return false
        }

        if (nLayer == 4)
            return false

        if (check_layer > 0 && nLayer != check_layer)
            return false

        if (head shr 12 and 0xf == 0xfL) {
            /* bits 16,17,18,19 = 1111 invalid bitrate */
            return false
        }
        if (head shr 10 and 0x3 == 0x3L) {
            /* bits 20,21 = 11 invalid sampling freq */
            return false
        }
        return if (head and 0x3 == 0x2L) false else true
    }

    /**
     * decode a header and write the information into the frame structure
     */
    fun decode_header(fr: Frame, newhead: Long): Int {

        if (newhead and (1 shl 20) != 0L) {
            fr.lsf = if (newhead and (1 shl 19) != 0L) 0x0 else 0x1
            fr.mpeg25 = false
        } else {
            fr.lsf = 1
            fr.mpeg25 = true
        }

        fr.lay = (4 - (newhead shr 17 and 3)).toInt()
        if (newhead shr 10 and 0x3 == 0x3L) {
            throw RuntimeException("Stream error")
        }
        if (fr.mpeg25) {
            fr.sampling_frequency = (6 + (newhead shr 10 and 0x3)).toInt()
        } else
            fr.sampling_frequency = ((newhead shr 10 and 0x3) + fr.lsf * 3).toInt()

        fr.error_protection = newhead shr 16 and 0x1 == 0L

        if (fr.mpeg25)
        /* allow Bitrate change for 2.5 ... */
            fr.bitrate_index = (newhead shr 12 and 0xf).toInt()

        fr.bitrate_index = (newhead shr 12 and 0xf).toInt()
        fr.padding = (newhead shr 9 and 0x1).toInt()
        fr.extension = (newhead shr 8 and 0x1).toInt()
        fr.mode = (newhead shr 6 and 0x3).toInt()
        fr.mode_ext = (newhead shr 4 and 0x3).toInt()
        fr.copyright = (newhead shr 3 and 0x1).toInt()
        fr.original = (newhead shr 2 and 0x1).toInt()
        fr.emphasis = (newhead and 0x3).toInt()

        fr.stereo = if (fr.mode == MPG123.MPG_MD_MONO) 1 else 2

        when (fr.lay) {
            1 -> {
                fr.framesize = tabsel_123[fr.lsf][0][fr.bitrate_index] * 12000
                fr.framesize /= freqs[fr.sampling_frequency]
                fr.framesize = (fr.framesize + fr.padding shl 2) - 4
                fr.down_sample = 0
                fr.down_sample_sblimit = MPG123.SBLIMIT shr fr.down_sample
            }

            2 -> {
                fr.framesize = tabsel_123[fr.lsf][1][fr.bitrate_index] * 144000
                fr.framesize /= freqs[fr.sampling_frequency]
                fr.framesize += fr.padding - 4
                fr.down_sample = 0
                fr.down_sample_sblimit = MPG123.SBLIMIT shr fr.down_sample
            }

            3 -> {
                if (fr.framesize > MAX_INPUT_FRAMESIZE) {
                    System.err.printf("Frame size too big.\n")
                    fr.framesize = MAX_INPUT_FRAMESIZE
                    return 0
                }

                if (fr.bitrate_index == 0)
                    fr.framesize = 0
                else {
                    fr.framesize = tabsel_123[fr.lsf][2][fr.bitrate_index] * 144000
                    fr.framesize /= freqs[fr.sampling_frequency] shl fr.lsf
                    fr.framesize = fr.framesize + fr.padding - 4
                }
            }
            else -> {
                System.err.printf("Sorry, layer %d not supported\n", fr.lay)
                return 0
            }
        }
        /* print_header(fr); */

        return 1
    }

    fun print_header(fr: Frame) {

        System.err
                .printf("MPEG %s, Layer: %s, Freq: %d, mode: %s, modext: %d, BPF : %d\n",
                        if (fr.mpeg25) "2.5" else if (fr.lsf != 0) "2.0" else "1.0",
                        layers[fr.lay], freqs[fr.sampling_frequency],
                        modes[fr.mode], fr.mode_ext, fr.framesize + 4)
        System.err
                .printf("Channels: %d, copyright: %s, original: %s, CRC: %s, emphasis: %d.\n",
                        fr.stereo, if (fr.copyright != 0) "Yes" else "No",
                        if (fr.original != 0) "Yes" else "No",
                        if (fr.error_protection) "Yes" else "No", fr.emphasis)
        System.err.printf("Bitrate: %d Kbits/s, Extension value: %d\n",
                tabsel_123[fr.lsf][fr.lay - 1][fr.bitrate_index], fr.extension)
    }

    fun print_header_compact(fr: Frame) {
        System.err.printf("MPEG %s layer %s, %d kbit/s, %d Hz %s\n",
                if (fr.mpeg25) "2.5" else if (fr.lsf != 0) "2.0" else "1.0",
                layers[fr.lay],
                tabsel_123[fr.lsf][fr.lay - 1][fr.bitrate_index],
                freqs[fr.sampling_frequency], modes[fr.mode])
    }

    fun getbits(mp: mpstr_tag, number_of_bits: Int): Int {
        var rval: Long

        if (number_of_bits <= 0 || null == mp.wordpointer)
            return 0

        run {
            rval = (mp.wordpointer!![mp.wordpointerPos + 0] and 0xff).toLong()
            rval = rval shl 8
            rval = rval or (mp.wordpointer!![mp.wordpointerPos + 1] and 0xff).toLong()
            rval = rval shl 8
            rval = rval or (mp.wordpointer!![mp.wordpointerPos + 2] and 0xff).toLong()
            rval = rval shl mp.bitindex
            rval = rval and 0xffffffL

            mp.bitindex += number_of_bits

            rval = rval shr (24 - number_of_bits)

            mp.wordpointerPos += mp.bitindex shr 3
            mp.bitindex = mp.bitindex and 7
        }
        return rval.toInt()
    }

    fun getbits_fast(mp: mpstr_tag, number_of_bits: Int): Int {
        var rval: Long

        run {
            rval = (mp.wordpointer!![mp.wordpointerPos + 0] and 0xff).toLong()
            rval = rval shl 8
            rval = rval or (mp.wordpointer!![mp.wordpointerPos + 1] and 0xff).toLong()
            rval = rval shl mp.bitindex //.toLong()
            rval = rval and 0xffffL
            mp.bitindex += number_of_bits

            rval = rval shr (16 - number_of_bits) //.toLong()

            mp.wordpointerPos += mp.bitindex shr 3
            mp.bitindex = mp.bitindex and 7
        }
        return rval.toInt()
    }

    fun set_pointer(mp: mpstr_tag, backstep: Int): Int {
        if (mp.fsizeold < 0 && backstep > 0) {
            System.err.printf("hip: Can't step back %d bytes!\n", backstep)
            return MPGLib.MP3_ERR
        }
        val bsbufold = mp.bsspace[1 - mp.bsnum]
        val bsbufoldPos = 512
        mp.wordpointerPos -= backstep
        if (backstep != 0)
            System.arraycopy(bsbufold, bsbufoldPos + mp.fsizeold - backstep,
                    mp.wordpointer!!, mp.wordpointerPos, backstep)
        mp.bitindex = 0
        return MPGLib.MP3_OK
    }

    companion object {

        val tabsel_123 = arrayOf(arrayOf(intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448), intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384), intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)),

                arrayOf(intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256), intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160), intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)))

        val freqs = intArrayOf(44100, 48000, 32000, 22050, 24000, 16000, 11025, 12000, 8000)

        private const val MAX_INPUT_FRAMESIZE = 4096

        private val modes = arrayOf("Stereo", "Joint-Stereo", "Dual-Channel", "Single-Channel")
        private val layers = arrayOf("Unknown", "I", "II", "III")
    }
}
