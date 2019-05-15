/* 
 * layer2.c: Mpeg Layer-2 audio decoder 
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
/* $Id: Layer2.java,v 1.4 2011/05/31 19:42:01 kenchis Exp $ */

package mpg

import jdk.Math
import mpg.Decode.Factory
import mpg.Interface.ISynth
import mpg.MPGLib.ProcessedBytes
import mpg.MPGLib.mpstr_tag

class Layer2 {

    private var common: Common? = null

    private val grp_3tab = IntArray(32 * 3) /* used: 27 */
    private val grp_5tab = IntArray(128 * 3) /* used: 125 */
    private val grp_9tab = IntArray(1024 * 3) /* used: 729 */
    private val base = arrayOf(intArrayOf(1, 0, 2), intArrayOf(17, 18, 0, 19, 20), intArrayOf(21, 1, 22, 23, 0, 24, 25, 2, 26))

    private val tablen = intArrayOf(3, 5, 9)
    private val tables = arrayOf(grp_3tab, grp_5tab, grp_9tab)
    private var itable: Int = 0

    private val scfsi_buf = IntArray(64)

    private val EMPTY = IntArray(0)
    private val table = arrayOf<IntArray>(EMPTY, EMPTY, EMPTY, grp_3tab, EMPTY, grp_5tab, EMPTY, EMPTY, EMPTY, grp_9tab)

    private val tables2 = arrayOf(L2Tables.alloc_0, L2Tables.alloc_1, L2Tables.alloc_2, L2Tables.alloc_3, L2Tables.alloc_4)
    private val sblims = intArrayOf(27, 30, 8, 12, 30)

    fun setModules(c: Common) {
        common = c
    }

    fun init_layer2() {
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        var len: Int
        var table: FloatArray

        i = 0
        while (i < 3) {
            itable = 0
            len = tablen[i]
            j = 0
            while (j < len) {
//                {
                    k = 0
                    while (k < len) {
//                        {
                            l = 0
                            while (l < len) {
                                tables[i][itable++] = base[i][l]
                                tables[i][itable++] = base[i][k]
                                tables[i][itable++] = base[i][j]
                                l++
                            }
//                        }
                        k++
                    }
//                }
                j++
            }
            i++
        }

        k = 0
        while (k < 27) {
            val m = mulmul[k]
            table = common!!.muls[k]
            var tablePos = 0
            j = 3
            i = 0
            while (i < 63) {
                table[tablePos++] = (m * Math.pow(2.0, j.toDouble() / 3.0)).toFloat()
                i++
                j--
            }
            table[tablePos++] = 0.0f
            k++
        }
    }

    private fun II_step_one(mp: mpstr_tag, bit_alloc: IntArray,
                            scale: IntArray, fr: Frame) {
        var scalePos = 0
        val stereo = fr.stereo - 1
        val sblimit = fr.II_sblimit
        val jsbound = fr.jsbound
        val sblimit2 = fr.II_sblimit shl stereo
        var alloc1 = 0
        var i: Int
        var scfsi: Int
        var bita: Int
        var sc: Int
        var step: Int

        bita = 0
        if (stereo != 0) {
            i = jsbound
            while (i != 0) {
                step = fr.alloc!![alloc1].bits.toInt()
                bit_alloc[bita++] = common!!.getbits(mp, step).toChar().toInt()
                bit_alloc[bita++] = common!!.getbits(mp, step).toChar().toInt()
                i--
                alloc1 += 1 shl step
            }
            i = sblimit - jsbound
            while (i != 0) {
                step = fr.alloc!![alloc1].bits.toInt()
                bit_alloc[bita + 0] = common!!.getbits(mp, step).toChar().toInt()
                bit_alloc[bita + 1] = bit_alloc[bita + 0]
                bita += 2
                i--
                alloc1 += 1 shl step
            }
            bita = 0
            scfsi = 0
            i = sblimit2
            while (i != 0) {
                if (bit_alloc[bita++] != 0)
                    scfsi_buf[scfsi++] = common!!.getbits_fast(mp, 2).toChar().toInt()
                i--
            }
        } else {              /* mono */

            i = sblimit
            while (i != 0) {
                step = fr.alloc!![alloc1].bits.toInt()
                bit_alloc[bita++] = common!!.getbits(mp, step).toChar().toInt()
                i--
                alloc1 += 1 shl step
            }
            bita = 0
            scfsi = 0
            i = sblimit
            while (i != 0) {
                if (bit_alloc[bita++] != 0)
                    scfsi_buf[scfsi++] = common!!.getbits_fast(mp, 2).toChar().toInt()
                i--
            }
        }

        bita = 0
        scfsi = 0
        i = sblimit2
        while (i != 0) {
            if (bit_alloc[bita++] != 0)
                when (scfsi_buf[scfsi++]) {
                    0 -> {
                        scale[scalePos++] = common!!.getbits_fast(mp, 6)
                        scale[scalePos++] = common!!.getbits_fast(mp, 6)
                        scale[scalePos++] = common!!.getbits_fast(mp, 6)
                    }
                    1 -> {
                        sc = common!!.getbits_fast(mp, 6)
                        scale[scalePos++] = sc
                        scale[scalePos++] = sc
                        scale[scalePos++] = common!!.getbits_fast(mp, 6)
                    }
                    2 -> {
                        sc = common!!.getbits_fast(mp, 6)
                        scale[scalePos++] = sc
                        scale[scalePos++] = sc
                        scale[scalePos++] = sc
                    }
                    else   /* case 3 */ -> {
                        scale[scalePos++] = common!!.getbits_fast(mp, 6)
                        sc = common!!.getbits_fast(mp, 6)
                        scale[scalePos++] = sc
                        scale[scalePos++] = sc
                    }
                }
            i--
        }

    }

    private fun II_step_two(mp: mpstr_tag, bit_alloc: IntArray,
                            fraction: Array<Array<FloatArray>>, scale: IntArray, fr: Frame,
                            x1: Int) {
        var scalePos = 0
        var i: Int
        var j: Int
        var k: Int
        var ba: Int
        val stereo = fr.stereo
        val sblimit = fr.II_sblimit
        val jsbound = fr.jsbound
        var alloc2: Int
        var alloc1 = 0
        var bita = 0
        var d1: Int
        var step: Int

        i = 0
        while (i < jsbound) {
            step = fr.alloc!![alloc1].bits.toInt()
            j = 0
            while (j < stereo) {
                ba = bit_alloc[bita++]
                if (ba != 0) {
                    alloc2 = alloc1 + ba
                    k = fr.alloc!![alloc2].bits.toInt()
                    d1 = fr.alloc!![alloc2].d.toInt()
                    if ((d1) < 0) {
                        val cm = common!!.muls[k][scale[scalePos + x1]]
                        fraction[j][0][i] = (common!!.getbits(mp, k) + d1).toFloat() * cm
                        fraction[j][1][i] = (common!!.getbits(mp, k) + d1).toFloat() * cm
                        fraction[j][2][i] = (common!!.getbits(mp, k) + d1).toFloat() * cm
                    } else {
                        val idx: Int
                        var tab: Int
                        val m = scale[scalePos + x1]
                        idx = common!!.getbits(mp, k)
                        tab = idx + idx + idx
                        fraction[j][0][i] = common!!.muls[table[d1][tab++]][m]
                        fraction[j][1][i] = common!!.muls[table[d1][tab++]][m]
                        fraction[j][2][i] = common!!.muls[table[d1][tab]][m]
                    }
                    scalePos += 3
                } else {
                    fraction[j][2][i] = 0.0f
                    fraction[j][1][i] = fraction[j][2][i]
                    fraction[j][0][i] = fraction[j][1][i]
                }
                j++
            }
            i++
            alloc1 += 1 shl step
        }

        i = jsbound
        while (i < sblimit) {
            step = fr.alloc!![alloc1].bits.toInt()
            bita++         /* channel 1 and channel 2 bitalloc are the same */
            ba = bit_alloc[bita++]
            if (ba != 0) {
                alloc2 = alloc1 + ba
                k = fr.alloc!![alloc2].bits.toInt()
                d1 = fr.alloc!![alloc2].d.toInt()
                if (d1 < 0) {
                    var cm: Float
                    cm = common!!.muls[k][scale[scalePos + x1 + 3]]
                    fraction[0][0][i] = (common!!.getbits(mp, k) + d1).toFloat()
                    fraction[1][0][i] = (fraction[0][0][i]) * cm
                    fraction[0][1][i] = (common!!.getbits(mp, k) + d1).toFloat()
                    fraction[1][1][i] = (fraction[0][1][i]) * cm
                    fraction[0][2][i] = (common!!.getbits(mp, k) + d1).toFloat()
                    fraction[1][2][i] = (fraction[0][2][i]) * cm
                    cm = common!!.muls[k][scale[scalePos + x1]]
                    fraction[0][0][i] *= cm
                    fraction[0][1][i] *= cm
                    fraction[0][2][i] *= cm
                } else {
                    val idx: Int
                    var tab: Int
                    val m1: Int
                    val m2: Int
                    m1 = scale[scalePos + x1]
                    m2 = scale[scalePos + x1 + 3]
                    idx = common!!.getbits(mp, k)
                    tab = idx + idx + idx
                    fraction[0][0][i] = common!!.muls[table[d1][tab]][m1]
                    fraction[1][0][i] = common!!.muls[table[d1][tab++]][m2]
                    fraction[0][1][i] = common!!.muls[table[d1][tab]][m1]
                    fraction[1][1][i] = common!!.muls[table[d1][tab++]][m2]
                    fraction[0][2][i] = common!!.muls[table[d1][tab]][m1]
                    fraction[1][2][i] = common!!.muls[table[d1][tab]][m2]
                }
                scalePos += 6
            } else {
                fraction[1][2][i] = 0.0f
                fraction[1][1][i] = fraction[1][2][i]
                fraction[1][0][i] = fraction[1][1][i]
                fraction[0][2][i] = fraction[1][0][i]
                fraction[0][1][i] = fraction[0][2][i]
                fraction[0][0][i] = fraction[0][1][i]
            }
            i++
            alloc1 += 1 shl step
            /*
	       should we use individual scalefac for channel 2 or
	       is the current way the right one , where we just copy channel 1 to
	       channel 2 ??
	       The current 'strange' thing is, that we throw away the scalefac
	       values for the second channel ...!!
	    . changed .. now we use the scalefac values of channel one !!
	    */
        }
        /*  if(sblimit > (fr.down_sample_sblimit) ) */
        /*    sblimit = fr.down_sample_sblimit; */

        i = sblimit
        while (i < MPG123.SBLIMIT) {
//            {
                j = 0
                while (j < stereo) {
                    fraction[j][2][i] = 0.0f
                    fraction[j][1][i] = fraction[j][2][i]
                    fraction[j][0][i] = fraction[j][1][i]
                    j++
                }
//            }
            i++
        }

    }

    private fun II_select_table(fr: Frame) {
        val table: Int
        val sblim: Int

        if (fr.lsf != 0)
            table = 4
        else
            table = translate[fr.sampling_frequency][2 - fr.stereo][fr.bitrate_index]
        sblim = sblims[table]

        fr.alloc = tables2[table]
        fr.II_sblimit = sblim
    }

    fun <T> do_layer2(mp: mpstr_tag, pcm_sample: Array<T>,
                      pcm_point: ProcessedBytes, synth: ISynth,
                      tFactory: Factory<T>): Int {
        var clip = 0
        var i: Int
        var j: Int
        val fraction = Array(2) { Array(4) { FloatArray(MPG123.SBLIMIT) } } /* pick_table clears unused subbands */
        val bit_alloc = IntArray(64)
        val scale = IntArray(192)
        val fr = mp.fr
        val stereo = fr.stereo
        var single = fr.single

        II_select_table(fr)
        fr.jsbound = if (fr.mode == MPG123.MPG_MD_JOINT_STEREO) (fr.mode_ext shl 2) + 4 else fr.II_sblimit

        if (stereo == 1 || single == 3)
            single = 0

        II_step_one(mp, bit_alloc, scale, fr)

        i = 0
        while (i < MPG123.SCALE_BLOCK) {
            II_step_two(mp, bit_alloc, fraction, scale, fr, i shr 2)
            j = 0
            while (j < 3) {
                if (single >= 0) {
                    clip += synth.synth_1to1_mono_ptr(mp, fraction[single][j], 0, pcm_sample, pcm_point, tFactory)
                } else {
                    val p1 = ProcessedBytes()
                    p1.pb = pcm_point.pb
                    clip += synth.synth_1to1_ptr(mp, fraction[0][j], 0, 0, pcm_sample, p1, tFactory)
                    clip += synth.synth_1to1_ptr(mp, fraction[1][j], 0, 1, pcm_sample, pcm_point, tFactory)
                }
                j++
            }
            i++
        }

        return clip
    }

    companion object {

        private val mulmul = doubleArrayOf(0.0, -2.0 / 3.0, 2.0 / 3.0, 2.0 / 7.0, 2.0 / 15.0, 2.0 / 31.0, 2.0 / 63.0, 2.0 / 127.0, 2.0 / 255.0, 2.0 / 511.0, 2.0 / 1023.0, 2.0 / 2047.0, 2.0 / 4095.0, 2.0 / 8191.0, 2.0 / 16383.0, 2.0 / 32767.0, 2.0 / 65535.0, -4.0 / 5.0, -2.0 / 5.0, 2.0 / 5.0, 4.0 / 5.0, -8.0 / 9.0, -4.0 / 9.0, -2.0 / 9.0, 2.0 / 9.0, 4.0 / 9.0, 8.0 / 9.0)

        private val translate = arrayOf(arrayOf(intArrayOf(0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 1, 1, 1, 1, 1, 0), intArrayOf(0, 2, 2, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0)), arrayOf(intArrayOf(0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)), arrayOf(intArrayOf(0, 3, 3, 3, 3, 3, 3, 0, 0, 0, 1, 1, 1, 1, 1, 0), intArrayOf(0, 3, 3, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0)))
    }
}
