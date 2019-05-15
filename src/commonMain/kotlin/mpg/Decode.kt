/*
 * decode_i396.c: Mpeg Layer-1,2,3 audio decoder
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
 *
 *
 * Slighlty optimized for machines without autoincrement/decrement.
 * The performance is highly compiler dependend. Maybe
 * the decode.c version for 'normal' processor may be faster
 * even for Intel processors.
 */

/* $Id: Decode.java,v 1.10 2011/05/31 03:33:59 kenchis Exp $ */

package mpg

import mpg.MPGLib.ProcessedBytes
import mpg.MPGLib.mpstr_tag

class Decode {

    private lateinit var tab: TabInit
    private lateinit var dct64: DCT64

    fun setModules(t: TabInit, d: DCT64) {
        tab = t
        dct64 = d
    }

    interface Factory<T> {
        fun create(x: Float): T
    }

    /* old WRITE_SAMPLE_CLIPPED */
    private fun <T> WRITE_SAMPLE_CLIPPED(samples: Int, sum: Float, clip: Int,
                                         out: Array<T>, tFactory: Factory<T>): Int {
        var clip = clip
        /* old WRITE_SAMPLE_CLIPPED */
        if (sum > 32767.0) {
            out[samples] = tFactory.create(32767f)
            clip++
        } else if (sum < -32768.0) {
            out[samples] = tFactory.create(-32768f)
            clip++
        } else {
            out[samples] = tFactory.create((if (sum > 0)
                sum + 0.5
            else
                sum - 0.5).toInt().toFloat())
        }
        return clip
    }

    private fun <T> WRITE_SAMPLE_UNCLIPPED(samples: Int, sum: Float, clip: Int,
                                           out: Array<T>, tFactory: Factory<T>) {
        out[samples] = tFactory.create(sum)
    }

    fun <T> synth_1to1_mono(mp: mpstr_tag, bandPtr: FloatArray, bandPos: Int, out: Array<T>,
                                     pnt: ProcessedBytes, tFactory: Factory<T>): Int {
        val samples_tmp = arrayOfNulls<Any>(64) as Array<T>
        var tmp1 = 0
        var i: Int
        val ret: Int
        val pnt1 = ProcessedBytes()

        ret = synth_1to1(mp, bandPtr, bandPos, 0, samples_tmp, pnt1, tFactory)
        var outPos = pnt.pb

        i = 0
        while (i < 32) {
            out[outPos++] = samples_tmp[tmp1]
            tmp1 += 2
            i++
        }
        pnt.pb += 32

        return ret
    }

    internal fun <T> synth_1to1_mono_unclipped(mp: mpstr_tag, bandPtr: FloatArray,
                                               bandPos: Int, out: Array<T>, pnt: ProcessedBytes,
                                               tFactory: Factory<T>): Int {
        val samples_tmp = arrayOfNulls<Any>(64) as Array<T>
        var tmp1 = 0
        var i: Int
        val ret: Int
        val pnt1 = ProcessedBytes()

        ret = synth_1to1_unclipped(mp, bandPtr, bandPos, 0, samples_tmp,
                pnt1, tFactory)
        var outPos = pnt.pb

        i = 0
        while (i < 32) {
            out[outPos++] = samples_tmp[tmp1]
            tmp1 += 2
            i++
        }
        pnt.pb += 32

        return ret
    }

    fun <T> synth_1to1(mp: mpstr_tag, bandPtr: FloatArray, bandPos: Int, channel: Int,
                                out: Array<T>, pnt: ProcessedBytes, tFactory: Factory<T>): Int {
        var bo: Int
        var samples = pnt.pb
        val b0: FloatArray
        var b0Pos: Int
        val buf: Array<FloatArray>
        var clip = 0
        val bo1: Int

        bo = mp.synth_bo

        if (0 == channel) {
            bo--
            bo = bo and 0xf
            buf = mp.synth_buffs[0]
        } else {
            samples++
            buf = mp.synth_buffs[1]
        }

        if (bo and 0x1 != 0) {
            b0 = buf[0]
            b0Pos = 0
            bo1 = bo
            dct64.dct64(buf[1], bo + 1 and 0xf, buf[0], bo, bandPtr, bandPos)
        } else {
            b0 = buf[1]
            b0Pos = 0
            bo1 = bo + 1
            dct64.dct64(buf[0], bo, buf[1], bo + 1, bandPtr, bandPos)
        }

        mp.synth_bo = bo

        run {
            var j: Int
            var window = 16 - bo1

            j = 16
            while (j != 0) {
                var sum: Float
                sum = tab.decwin[window + 0x0] * b0[b0Pos + 0x0]
                sum -= tab.decwin[window + 0x1] * b0[b0Pos + 0x1]
                sum += tab.decwin[window + 0x2] * b0[b0Pos + 0x2]
                sum -= tab.decwin[window + 0x3] * b0[b0Pos + 0x3]
                sum += tab.decwin[window + 0x4] * b0[b0Pos + 0x4]
                sum -= tab.decwin[window + 0x5] * b0[b0Pos + 0x5]
                sum += tab.decwin[window + 0x6] * b0[b0Pos + 0x6]
                sum -= tab.decwin[window + 0x7] * b0[b0Pos + 0x7]
                sum += tab.decwin[window + 0x8] * b0[b0Pos + 0x8]
                sum -= tab.decwin[window + 0x9] * b0[b0Pos + 0x9]
                sum += tab.decwin[window + 0xA] * b0[b0Pos + 0xA]
                sum -= tab.decwin[window + 0xB] * b0[b0Pos + 0xB]
                sum += tab.decwin[window + 0xC] * b0[b0Pos + 0xC]
                sum -= tab.decwin[window + 0xD] * b0[b0Pos + 0xD]
                sum += tab.decwin[window + 0xE] * b0[b0Pos + 0xE]
                sum -= tab.decwin[window + 0xF] * b0[b0Pos + 0xF]
                clip = WRITE_SAMPLE_CLIPPED(samples, sum, clip, out, tFactory)
                j--
                b0Pos += 0x10
                window += 0x20
                samples += step
            }

            run {
                var sum: Float
                sum = tab.decwin[window + 0x0] * b0[b0Pos + 0x0]
                sum += tab.decwin[window + 0x2] * b0[b0Pos + 0x2]
                sum += tab.decwin[window + 0x4] * b0[b0Pos + 0x4]
                sum += tab.decwin[window + 0x6] * b0[b0Pos + 0x6]
                sum += tab.decwin[window + 0x8] * b0[b0Pos + 0x8]
                sum += tab.decwin[window + 0xA] * b0[b0Pos + 0xA]
                sum += tab.decwin[window + 0xC] * b0[b0Pos + 0xC]
                sum += tab.decwin[window + 0xE] * b0[b0Pos + 0xE]
                clip = WRITE_SAMPLE_CLIPPED(samples, sum, clip, out, tFactory)
                b0Pos -= 0x10
                window -= 0x20
                samples += step
            }
            window += bo1 shl 1

            j = 15
            while (j != 0) {
                var sum: Float
                sum = -tab.decwin[window + -0x1] * b0[b0Pos + 0x0]
                sum -= tab.decwin[window + -0x2] * b0[b0Pos + 0x1]
                sum -= tab.decwin[window + -0x3] * b0[b0Pos + 0x2]
                sum -= tab.decwin[window + -0x4] * b0[b0Pos + 0x3]
                sum -= tab.decwin[window + -0x5] * b0[b0Pos + 0x4]
                sum -= tab.decwin[window + -0x6] * b0[b0Pos + 0x5]
                sum -= tab.decwin[window + -0x7] * b0[b0Pos + 0x6]
                sum -= tab.decwin[window + -0x8] * b0[b0Pos + 0x7]
                sum -= tab.decwin[window + -0x9] * b0[b0Pos + 0x8]
                sum -= tab.decwin[window + -0xA] * b0[b0Pos + 0x9]
                sum -= tab.decwin[window + -0xB] * b0[b0Pos + 0xA]
                sum -= tab.decwin[window + -0xC] * b0[b0Pos + 0xB]
                sum -= tab.decwin[window + -0xD] * b0[b0Pos + 0xC]
                sum -= tab.decwin[window + -0xE] * b0[b0Pos + 0xD]
                sum -= tab.decwin[window + -0xF] * b0[b0Pos + 0xE]
                sum -= tab.decwin[window + -0x0] * b0[b0Pos + 0xF]

                clip = WRITE_SAMPLE_CLIPPED(samples, sum, clip, out, tFactory)
                j--
                b0Pos -= 0x10
                window -= 0x20
                samples += step
            }
        }
        pnt.pb += 64

        return clip
    }

    internal fun <T> synth_1to1_unclipped(mp: mpstr_tag, bandPtr: FloatArray, bandPos: Int, channel: Int, out: Array<T>, pnt: ProcessedBytes, tFactory: Factory<T>): Int {
        var bo: Int
        var samples = pnt.pb
        val b0: FloatArray
        var b0Pos: Int
        val buf: Array<FloatArray>
        val clip = 0
        val bo1: Int

        bo = mp.synth_bo

        if (0 == channel) {
            bo--
            bo = bo and 0xf
            buf = mp.synth_buffs[0]
        } else {
            samples++
            buf = mp.synth_buffs[1]
        }

        if (bo and 0x1 != 0) {
            b0 = buf[0]
            b0Pos = 0
            bo1 = bo
            dct64!!.dct64(buf[1], bo + 1 and 0xf, buf[0], bo, bandPtr, bandPos)
        } else {
            b0 = buf[1]
            b0Pos = 0
            bo1 = bo + 1
            dct64!!.dct64(buf[0], bo, buf[1], bo + 1, bandPtr, bandPos)
        }

        mp.synth_bo = bo

        run {
            var j: Int
            var window = 16 - bo1

            j = 16
            while (j != 0) {
                var sum: Float
                sum = tab.decwin[window + 0x0] * b0[b0Pos + 0x0]
                sum -= tab.decwin[window + 0x1] * b0[b0Pos + 0x1]
                sum += tab.decwin[window + 0x2] * b0[b0Pos + 0x2]
                sum -= tab.decwin[window + 0x3] * b0[b0Pos + 0x3]
                sum += tab.decwin[window + 0x4] * b0[b0Pos + 0x4]
                sum -= tab.decwin[window + 0x5] * b0[b0Pos + 0x5]
                sum += tab.decwin[window + 0x6] * b0[b0Pos + 0x6]
                sum -= tab.decwin[window + 0x7] * b0[b0Pos + 0x7]
                sum += tab.decwin[window + 0x8] * b0[b0Pos + 0x8]
                sum -= tab.decwin[window + 0x9] * b0[b0Pos + 0x9]
                sum += tab.decwin[window + 0xA] * b0[b0Pos + 0xA]
                sum -= tab.decwin[window + 0xB] * b0[b0Pos + 0xB]
                sum += tab.decwin[window + 0xC] * b0[b0Pos + 0xC]
                sum -= tab.decwin[window + 0xD] * b0[b0Pos + 0xD]
                sum += tab.decwin[window + 0xE] * b0[b0Pos + 0xE]
                sum -= tab.decwin[window + 0xF] * b0[b0Pos + 0xF]
                WRITE_SAMPLE_UNCLIPPED(samples, sum, clip, out, tFactory)
                j--
                b0Pos += 0x10
                window += 0x20
                samples += step
            }

            run {
                var sum: Float
                sum = tab.decwin[window + 0x0] * b0[b0Pos + 0x0]
                sum += tab.decwin[window + 0x2] * b0[b0Pos + 0x2]
                sum += tab.decwin[window + 0x4] * b0[b0Pos + 0x4]
                sum += tab.decwin[window + 0x6] * b0[b0Pos + 0x6]
                sum += tab.decwin[window + 0x8] * b0[b0Pos + 0x8]
                sum += tab.decwin[window + 0xA] * b0[b0Pos + 0xA]
                sum += tab.decwin[window + 0xC] * b0[b0Pos + 0xC]
                sum += tab.decwin[window + 0xE] * b0[b0Pos + 0xE]
                WRITE_SAMPLE_UNCLIPPED(samples, sum, clip, out, tFactory)
                b0Pos -= 0x10
                window -= 0x20
                samples += step
            }
            window += bo1 shl 1

            j = 15
            while (j != 0) {
                var sum: Float
                sum = -tab.decwin[window + -0x1] * b0[b0Pos + 0x0]
                sum -= tab.decwin[window + -0x2] * b0[b0Pos + 0x1]
                sum -= tab.decwin[window + -0x3] * b0[b0Pos + 0x2]
                sum -= tab.decwin[window + -0x4] * b0[b0Pos + 0x3]
                sum -= tab.decwin[window + -0x5] * b0[b0Pos + 0x4]
                sum -= tab.decwin[window + -0x6] * b0[b0Pos + 0x5]
                sum -= tab.decwin[window + -0x7] * b0[b0Pos + 0x6]
                sum -= tab.decwin[window + -0x8] * b0[b0Pos + 0x7]
                sum -= tab.decwin[window + -0x9] * b0[b0Pos + 0x8]
                sum -= tab.decwin[window + -0xA] * b0[b0Pos + 0x9]
                sum -= tab.decwin[window + -0xB] * b0[b0Pos + 0xA]
                sum -= tab.decwin[window + -0xC] * b0[b0Pos + 0xB]
                sum -= tab.decwin[window + -0xD] * b0[b0Pos + 0xC]
                sum -= tab.decwin[window + -0xE] * b0[b0Pos + 0xD]
                sum -= tab.decwin[window + -0xF] * b0[b0Pos + 0xE]
                sum -= tab.decwin[window + -0x0] * b0[b0Pos + 0xF]

                WRITE_SAMPLE_UNCLIPPED(samples, sum, clip, out, tFactory)
                j--
                b0Pos -= 0x10
                window -= 0x20
                samples += step
            }
        }
        pnt.pb += 64

        return clip
    }

    companion object {

        private val step = 2
    }
}
