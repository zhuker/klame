/* 
 * layer3.c: Mpeg Layer-3 audio decoder 
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
/* $Id: Layer3.java,v 1.19 2011/06/17 05:26:42 kenchis Exp $ */
package mpg

import jdk.Math
import jdk.System
import jdk.and
import jdk.shr
import mpg.Decode.Factory
import mpg.Interface.ISynth
import mpg.MPG123.III_sideinfo
import mpg.MPG123.gr_info_s
import mpg.MPGLib.ProcessedBytes
import mpg.MPGLib.mpstr_tag

class Layer3 {
    private var common: Common? = null

    private val ispow = FloatArray(8207)
    private val aa_ca = FloatArray(8)
    private val aa_cs = FloatArray(8)
    private val COS1 = Array(12) { FloatArray(6) }
    private val win = Array(4) { FloatArray(36) }
    private val win1 = Array(4) { FloatArray(36) }
    private val gainpow2 = FloatArray(256 + 118 + 4)
    private val COS9 = FloatArray(9)
    private var COS6_1: Float = 0.toFloat()
    private var COS6_2: Float = 0.toFloat()
    private val tfcos36 = FloatArray(9)
    private val tfcos12 = FloatArray(3)

    private val longLimit = Array(9) { IntArray(23) }
    private val shortLimit = Array(9) { IntArray(14) }

    private val mapbuf0 = Array(9) { IntArray(152) }
    private val mapbuf1 = Array(9) { IntArray(156) }
    private val mapbuf2 = Array(9) { IntArray(44) }
    private val map = Array<Array<IntArray>>(9) { Array(3) { IntArray(0) } }
    private val mapend = Array(9) { IntArray(3) }

    /**
     * MPEG 2.0 slen for 'normal' mode.
     */
    private val n_slen2 = IntArray(512)
    /**
     * MPEG 2.0 slen for intensity stereo.
     */
    private val i_slen2 = IntArray(256)

    private val tan1_1 = FloatArray(16)
    private val tan2_1 = FloatArray(16)
    private val tan1_2 = FloatArray(16)
    private val tan2_2 = FloatArray(16)
    private val pow1_1 = Array(2) { FloatArray(16) }
    private val pow2_1 = Array(2) { FloatArray(16) }
    private val pow1_2 = Array(2) { FloatArray(16) }
    private val pow2_2 = Array(2) { FloatArray(16) }

    /*
	 * main layer3 handler
	 */
    private val sideinfo = III_sideinfo()

    private val hybridIn = Array(2) { FloatArray(MPG123.SBLIMIT * MPG123.SSLIMIT) }
    private val hybridOut = Array(2) { FloatArray(MPG123.SSLIMIT * MPG123.SBLIMIT) }

    fun setModules(c: Common) {
        common = c
    }

    private class bandInfoStruct(lIdx: ShortArray, lDiff: ShortArray,
                                 sIdx: ShortArray, sDiff: ShortArray) {

        internal var longIdx = ShortArray(23)
        internal var longDiff = ShortArray(22)
        internal var shortIdx = ShortArray(14)
        internal var shortDiff = ShortArray(13)

        init {
            longIdx = lIdx
            longDiff = lDiff
            shortIdx = sIdx
            shortDiff = sDiff
        }
    }

    private fun get1bit(mp: mpstr_tag): Int {
        var rval = mp.wordpointer!![mp.wordpointerPos] and 0xff shl mp.bitindex
        rval = rval and 0xff
        mp.bitindex++
        mp.wordpointerPos += mp.bitindex shr 3
        mp.bitindex = mp.bitindex and 7

        return rval shr 7
    }

    /*
	 * init tables for layer-3
	 */
    fun init_layer3(down_sample_sblimit: Int) {
        for (i in -256 until 118 + 4)
            gainpow2[i + 256] = Math.pow(2.0, -0.25 * (i + 210).toDouble()).toFloat()

        for (i in 0..8206)
            ispow[i] = Math.pow(i.toDouble(), 4.0 / 3.0).toFloat()

        for (i in 0..7) {
            val sq = Math.sqrt(1.0 + Ci[i] * Ci[i])
            aa_cs[i] = (1.0 / sq).toFloat()
            aa_ca[i] = (Ci[i] / sq).toFloat()
        }

        for (i in 0..17) {
            win[1][i] = (0.5 * Math.sin(MPG123.M_PI / 72.0 * (2 * (i + 0) + 1).toDouble()) / Math.cos(MPG123.M_PI * (2 * (i + 0) + 19).toDouble() / 72.0)).toFloat()
            win[0][i] = win[1][i]
            win[3][i + 18] = (0.5 * Math
                    .sin(MPG123.M_PI / 72.0 * (2 * (i + 18) + 1).toDouble()) / Math
                    .cos(MPG123.M_PI * (2 * (i + 18) + 19).toDouble() / 72.0)).toFloat()
            win[0][i + 18] = win[3][i + 18]
        }
        for (i in 0..5) {
            win[1][i + 18] = (0.5 / Math.cos(MPG123.M_PI * (2 * (i + 18) + 19).toDouble() / 72.0)).toFloat()
            win[3][i + 12] = (0.5 / Math.cos(MPG123.M_PI * (2 * (i + 12) + 19).toDouble() / 72.0)).toFloat()
            win[1][i + 24] = (0.5 * Math.sin(MPG123.M_PI / 24.0 * (2 * i + 13).toDouble()) / Math.cos(MPG123.M_PI * (2 * (i + 24) + 19).toDouble() / 72.0)).toFloat()
            win[3][i] = 0.0f
            win[1][i + 30] = win[3][i]
            win[3][i + 6] = (0.5 * Math.sin(MPG123.M_PI / 24.0 * (2 * i + 1).toDouble()) / Math.cos(MPG123.M_PI * (2 * (i + 6) + 19).toDouble() / 72.0)).toFloat()
        }

        for (i in 0..8)
            COS9[i] = Math.cos(MPG123.M_PI / 18.0 * i.toDouble()).toFloat()

        for (i in 0..8)
            tfcos36[i] = (0.5 / Math.cos(MPG123.M_PI * (i * 2 + 1).toDouble() / 36.0)).toFloat()
        for (i in 0..2)
            tfcos12[i] = (0.5 / Math.cos(MPG123.M_PI * (i * 2 + 1).toDouble() / 12.0)).toFloat()

        COS6_1 = Math.cos(MPG123.M_PI / 6.0 * 1.toDouble()).toFloat()
        COS6_2 = Math.cos(MPG123.M_PI / 6.0 * 2.toDouble()).toFloat()

        for (i in 0..11) {
            win[2][i] = (0.5 * Math.sin(MPG123.M_PI / 24.0 * (2 * i + 1).toDouble()) / Math.cos(MPG123.M_PI * (2 * i + 7).toDouble() / 24.0)).toFloat()
            for (j in 0..5)
                COS1[i][j] = Math.cos(MPG123.M_PI / 24.0 * ((2 * i + 7) * (2 * j + 1)).toDouble()).toFloat()
        }

        for (j in 0..3) {
            run {
                var i = 0
                while (i < len[j]) {
                    win1[j][i] = +win[j][i]
                    i += 2
                }
            }
            var i = 1
            while (i < len[j]) {
                win1[j][i] = -win[j][i]
                i += 2
            }
        }

        for (i in 0..15) {
            val t = Math.tan(i.toDouble() * MPG123.M_PI / 12.0)
            tan1_1[i] = (t / (1.0 + t)).toFloat()
            tan2_1[i] = (1.0 / (1.0 + t)).toFloat()
            tan1_2[i] = (MPG123.M_SQRT2 * t / (1.0 + t)).toFloat()
            tan2_2[i] = (MPG123.M_SQRT2 / (1.0 + t)).toFloat()

            for (j in 0..1) {
                val base = Math.pow(2.0, -0.25 * (j + 1.0))
                var p1 = 1.0
                var p2 = 1.0
                if (i > 0) {
                    if (i and 1 != 0)
                        p1 = Math.pow(base, (i + 1.0) * 0.5)
                    else
                        p2 = Math.pow(base, i * 0.5)
                }
                pow1_1[j][i] = p1.toFloat()
                pow2_1[j][i] = p2.toFloat()
                pow1_2[j][i] = (MPG123.M_SQRT2 * p1).toFloat()
                pow2_2[j][i] = (MPG123.M_SQRT2 * p2).toFloat()
            }
        }

        for (j in 0..8) {
            val bi = bandInfo[j]
            var mp: Int
            var cb: Int
            var lwin: Int
            var bdf: Int

            map[j][0] = mapbuf0[j]
            mp = 0
            bdf = 0
            var i: Int
            i = 0
            cb = 0
            while (cb < 8) {
                map[j][0][mp++] = bi.longDiff[bdf] shr 1
                map[j][0][mp++] = i
                map[j][0][mp++] = 3
                map[j][0][mp++] = cb
                cb++
                i += bi.longDiff[bdf++].toInt()
            }
            bdf = +3
            cb = 3
            while (cb < 13) {
                val l = bi.shortDiff[bdf++] shr 1
                lwin = 0
                while (lwin < 3) {
                    map[j][0][mp++] = l
                    map[j][0][mp++] = i + lwin
                    map[j][0][mp++] = lwin
                    map[j][0][mp++] = cb
                    lwin++
                }
                i += 6 * l
                cb++
            }
            mapend[j][0] = mp

            map[j][1] = mapbuf1[j]
            mp = 0
            bdf = 0
            i = 0
            cb = 0
            while (cb < 13) {
                val l = bi.shortDiff[bdf++] shr 1
                lwin = 0
                while (lwin < 3) {
                    map[j][1][mp++] = l
                    map[j][1][mp++] = i + lwin
                    map[j][1][mp++] = lwin
                    map[j][1][mp++] = cb
                    lwin++
                }
                i += 6 * l
                cb++
            }
            mapend[j][1] = mp

            map[j][2] = mapbuf2[j]
            mp = 0
            bdf = 0
            cb = 0
            while (cb < 22) {
                map[j][2][mp++] = bi.longDiff[bdf++] shr 1
                map[j][2][mp++] = cb
                cb++
            }
            mapend[j][2] = mp

        }

        for (j in 0..8) {
            for (i in 0..22) {
                longLimit[j][i] = (bandInfo[j].longIdx[i] - 1 + 8) / 18 + 1
                if (longLimit[j][i] > down_sample_sblimit)
                    longLimit[j][i] = down_sample_sblimit
            }
            for (i in 0..13) {
                shortLimit[j][i] = (bandInfo[j].shortIdx[i] - 1) / 18 + 1
                if (shortLimit[j][i] > down_sample_sblimit)
                    shortLimit[j][i] = down_sample_sblimit
            }
        }

        for (i in 0..4) {
            for (j in 0..5) {
                for (k in 0..5) {
                    val n = k + j * 6 + i * 36
                    i_slen2[n] = i or (j shl 3) or (k shl 6) or (3 shl 12)
                }
            }
        }
        for (i in 0..3) {
            for (j in 0..3) {
                for (k in 0..3) {
                    val n = k + j * 4 + i * 16
                    i_slen2[n + 180] = i or (j shl 3) or (k shl 6) or (4 shl 12)
                }
            }
        }
        for (i in 0..3) {
            for (j in 0..2) {
                val n = j + i * 3
                i_slen2[n + 244] = i or (j shl 3) or (5 shl 12)
                n_slen2[n + 500] = i or (j shl 3) or (2 shl 12) or (1 shl 15)
            }
        }

        for (i in 0..4) {
            for (j in 0..4) {
                for (k in 0..3) {
                    var l: Int
                    l = 0
                    while (l < 4) {
                        val n = l + k * 4 + j * 16 + i * 80
                        n_slen2[n] = (i or (j shl 3) or (k shl 6) or (l shl 9)
                                or (0 shl 12))
                        l++
                    }
                }
            }
        }
        for (i in 0..4) {
            for (j in 0..4) {
                for (k in 0..3) {
                    val n = k + j * 4 + i * 20
                    n_slen2[n + 400] = i or (j shl 3) or (k shl 6) or (1 shl 12)
                }
            }
        }
    }

    /*
	 * read additional side information
	 */

    private fun III_get_side_info_1(mp: mpstr_tag, si: III_sideinfo,
                                    stereo: Int, ms_stereo: Int, sfreq: Int,
                                    single: Int) {
        var ch: Int
        var gr: Int
        val powdiff = if (single == 3) 4 else 0

        si.main_data_begin = common!!.getbits(mp, 9)
        if (stereo == 1)
            si.private_bits = common!!.getbits_fast(mp, 5)
        else
            si.private_bits = common!!.getbits_fast(mp, 3)

        ch = 0
        while (ch < stereo) {
            si.ch[ch].gr[0].scfsi = -1
            si.ch[ch].gr[1].scfsi = common!!.getbits_fast(mp, 4)
            ch++
        }

        gr = 0
        while (gr < 2) {
            ch = 0
            while (ch < stereo) {
                val gr_infos = si.ch[ch].gr[gr]

                gr_infos.part2_3_length = common!!.getbits(mp, 12)
                gr_infos.big_values = common!!.getbits_fast(mp, 9)
                if (gr_infos.big_values > 288) {
                    System.err.printf("big_values too large! %d\n", gr_infos.big_values)
                    gr_infos.big_values = 288
                }
                run {
                    val qss = common!!.getbits_fast(mp, 8)
                    gr_infos.pow2gain = gainpow2
                    gr_infos.pow2gainPos = 256 - qss + powdiff
                    if (mp.pinfo != null) {
                        mp.pinfo!!.qss[gr][ch] = qss
                    }
                }
                if (ms_stereo != 0)
                    gr_infos.pow2gainPos += 2
                gr_infos.scalefac_compress = common!!.getbits_fast(mp, 4)
                /* window-switching flag == 1 for block_Type != 0 .. and block-type == 0 . win-sw-flag = 0 */
                if (get1bit(mp) != 0) {
                    var i: Int
                    gr_infos.block_type = common!!.getbits_fast(mp, 2)
                    gr_infos.mixed_block_flag = get1bit(mp)
                    gr_infos.table_select[0] = common!!.getbits_fast(mp, 5)
                    gr_infos.table_select[1] = common!!.getbits_fast(mp, 5)


                    /*
	                 * table_select[2] not needed, because there is no region2,
	                 * but to satisfy some verifications tools we set it either.
	                 */
                    gr_infos.table_select[2] = 0
                    i = 0
                    while (i < 3) {
                        val sbg = common!!.getbits_fast(mp, 3) shl 3
                        gr_infos.full_gain[i] = gr_infos.pow2gain!!
                        gr_infos.full_gainPos[i] = gr_infos.pow2gainPos + sbg
                        if (mp.pinfo != null)
                            mp.pinfo!!.sub_gain[gr][ch][i] = sbg / 8
                        i++
                    }

                    if (gr_infos.block_type == 0) {
                        System.err.printf("Blocktype == 0 and window-switching == 1 not allowed.\n")
                        /* error seems to be very good recoverable, so don't exit */
                        /* exit(1); */
                    }
                    /* region_count/start parameters are implicit in this case. */
                    gr_infos.region1start = 36 shr 1
                    gr_infos.region2start = 576 shr 1
                } else {
                    var i: Int
                    val r0c: Int
                    val r1c: Int
                    i = 0
                    while (i < 3) {
                        gr_infos.table_select[i] = common!!.getbits_fast(mp, 5)
                        i++
                    }
                    r0c = common!!.getbits_fast(mp, 4)
                    r1c = common!!.getbits_fast(mp, 3)
                    gr_infos.region1start = bandInfo[sfreq].longIdx[r0c + 1] shr 1
                    gr_infos.region2start = bandInfo[sfreq].longIdx[r0c + 1 + r1c + 1] shr 1
                    gr_infos.block_type = 0
                    gr_infos.mixed_block_flag = 0
                }
                gr_infos.preflag = get1bit(mp)
                gr_infos.scalefac_scale = get1bit(mp)
                gr_infos.count1table_select = get1bit(mp)
                ch++
            }
            gr++
        }
    }

    /*
	 * Side Info for MPEG 2.0 / LSF
	 */
    private fun III_get_side_info_2(mp: mpstr_tag, si: III_sideinfo,
                                    stereo: Int, ms_stereo: Int, sfreq: Int,
                                    single: Int) {
        var ch: Int
        val powdiff = if (single == 3) 4 else 0

        si.main_data_begin = common!!.getbits(mp, 8)

        if (stereo == 1)
            si.private_bits = get1bit(mp)
        else
            si.private_bits = common!!.getbits_fast(mp, 2)

        ch = 0
        while (ch < stereo) {
            val gr_infos = si.ch[ch].gr[0]
            val qss: Int

            gr_infos.part2_3_length = common!!.getbits(mp, 12)
            gr_infos.big_values = common!!.getbits_fast(mp, 9)
            if (gr_infos.big_values > 288) {
                System.err.printf("big_values too large! %d\n", gr_infos.big_values)
                gr_infos.big_values = 288
            }
            qss = common!!.getbits_fast(mp, 8)
            gr_infos.pow2gain = gainpow2
            gr_infos.pow2gainPos = 256 - qss + powdiff
            if (mp.pinfo != null) {
                mp.pinfo!!.qss[0][ch] = qss
            }

            if (ms_stereo != 0)
                gr_infos.pow2gainPos += 2
            gr_infos.scalefac_compress = common!!.getbits(mp, 9)
            /* window-switching flag == 1 for block_Type != 0 .. and block-type == 0 . win-sw-flag = 0 */
            if (get1bit(mp) != 0) {
                var i: Int
                gr_infos.block_type = common!!.getbits_fast(mp, 2)
                gr_infos.mixed_block_flag = get1bit(mp)
                gr_infos.table_select[0] = common!!.getbits_fast(mp, 5)
                gr_infos.table_select[1] = common!!.getbits_fast(mp, 5)
                /*
	             * table_select[2] not needed, because there is no region2,
	             * but to satisfy some verifications tools we set it either.
	             */
                gr_infos.table_select[2] = 0
                i = 0
                while (i < 3) {
                    val sbg = common!!.getbits_fast(mp, 3) shl 3
                    gr_infos.full_gain[i] = gr_infos.pow2gain!!
                    gr_infos.full_gainPos[i] = gr_infos.pow2gainPos + sbg
                    if (mp.pinfo != null)
                        mp.pinfo!!.sub_gain[0][ch][i] = sbg / 8
                    i++

                }

                if (gr_infos.block_type == 0) {
                    System.err.printf("Blocktype == 0 and window-switching == 1 not allowed.\n")
                    /* error seems to be very good recoverable, so don't exit */
                    /* exit(1); */
                }
                /* region_count/start parameters are implicit in this case. */
                /* check this again! */
                if (gr_infos.block_type == 2) {
                    if (sfreq == 8)
                        gr_infos.region1start = 36
                    else
                        gr_infos.region1start = 36 shr 1
                } else if (sfreq == 8)
                /* check this for 2.5 and sfreq=8 */
                    gr_infos.region1start = 108 shr 1
                else
                    gr_infos.region1start = 54 shr 1
                gr_infos.region2start = 576 shr 1
            } else {
                var i: Int
                val r0c: Int
                val r1c: Int
                i = 0
                while (i < 3) {
                    gr_infos.table_select[i] = common!!.getbits_fast(mp, 5)
                    i++
                }
                r0c = common!!.getbits_fast(mp, 4)
                r1c = common!!.getbits_fast(mp, 3)
                gr_infos.region1start = bandInfo[sfreq].longIdx[r0c + 1] shr 1
                gr_infos.region2start = bandInfo[sfreq].longIdx[r0c + 1 + r1c + 1] shr 1
                gr_infos.block_type = 0
                gr_infos.mixed_block_flag = 0
            }
            gr_infos.scalefac_scale = get1bit(mp)
            gr_infos.count1table_select = get1bit(mp)
            ch++
        }
    }
    /*
	 * read scalefactors
	 */

    private fun III_get_scale_factors_1(mp: mpstr_tag, scf: IntArray, gr_infos: gr_info_s): Int {
        var scfPos = 0
        var numbits: Int
        val num0 = slen[0][gr_infos.scalefac_compress]
        val num1 = slen[1][gr_infos.scalefac_compress]

        if (gr_infos.block_type == 2) {
            var i = 18
            numbits = (num0 + num1) * 18

            if (gr_infos.mixed_block_flag != 0) {
                i = 8
                while (i != 0) {
                    scf[scfPos++] = common!!.getbits_fast(mp, num0)
                    i--
                }
                i = 9
                numbits -= num0 /* num0 * 17 + num1 * 18 */
            }

            while (i != 0) {
                scf[scfPos++] = common!!.getbits_fast(mp, num0)
                i--
            }
            i = 18
            while (i != 0) {
                scf[scfPos++] = common!!.getbits_fast(mp, num1)
                i--
            }
            scf[scfPos++] = 0
            scf[scfPos++] = 0
            scf[scfPos++] = 0     /* short[13][0..2] = 0 */
        } else {
            var i: Int
            val scfsi = gr_infos.scfsi

            if (scfsi < 0) { /* scfsi < 0 => granule == 0 */
                i = 11
                while (i != 0) {
                    scf[scfPos++] = common!!.getbits_fast(mp, num0)
                    i--
                }
                i = 10
                while (i != 0) {
                    scf[scfPos++] = common!!.getbits_fast(mp, num1)
                    i--
                }
                numbits = (num0 + num1) * 10 + num0
            } else {
                numbits = 0
                if (0 == scfsi and 0x8) {
                    i = 6
                    while (i != 0) {
                        scf[scfPos++] = common!!.getbits_fast(mp, num0)
                        i--
                    }
                    numbits += num0 * 6
                } else {
                    scfPos += 6
                }

                if (0 == scfsi and 0x4) {
                    i = 5
                    while (i != 0) {
                        scf[scfPos++] = common!!.getbits_fast(mp, num0)
                        i--
                    }
                    numbits += num0 * 5
                } else {
                    scfPos += 5
                }

                if (0 == scfsi and 0x2) {
                    i = 5
                    while (i != 0) {
                        scf[scfPos++] = common!!.getbits_fast(mp, num1)
                        i--
                    }
                    numbits += num1 * 5
                } else {
                    scfPos += 5
                }

                if (0 == scfsi and 0x1) {
                    i = 5
                    while (i != 0) {
                        scf[scfPos++] = common!!.getbits_fast(mp, num1)
                        i--
                    }
                    numbits += num1 * 5
                } else {
                    scfPos += 5
                }
            }

            scf[scfPos++] = 0     /* no l[21] in original sources */
        }
        return numbits
    }

    private fun III_get_scale_factors_2(mp: mpstr_tag, scf: IntArray, gr_infos: gr_info_s, i_stereo: Int): Int {
        var scfPos = 0
        val pnt: IntArray
        var i: Int
        var j: Int
        var slen: Int
        var n = 0
        var numbits = 0

        if (i_stereo != 0)
        /* i_stereo AND second channel . do_layer3() checks this */
            slen = i_slen2[gr_infos.scalefac_compress shr 1]
        else
            slen = n_slen2[gr_infos.scalefac_compress]

        gr_infos.preflag = slen shr 15 and 0x1

        n = 0
        if (gr_infos.block_type == 2) {
            n++
            if (gr_infos.mixed_block_flag != 0)
                n++
        }

        pnt = stab[n][slen shr 12 and 0x7]

        i = 0
        while (i < 4) {
            val num = slen and 0x7
            slen = slen shr 3
            if (num != 0) {
                j = 0
                while (j < pnt[i]) {
                    scf[scfPos++] = common!!.getbits_fast(mp, num)
                    j++
                }
                numbits += pnt[i] * num
            } else {
                j = 0
                while (j < pnt[i]) {
                    scf[scfPos++] = 0
                    j++
                }
            }
            i++
        }

        n = (n shl 1) + 1
        i = 0
        while (i < n) {
            scf[scfPos++] = 0
            i++
        }

        return numbits
    }

    private fun III_dequantize_sample(mp: mpstr_tag, xr: FloatArray, scf: IntArray,
                                      gr_infos: gr_info_s, sfreq: Int, part2bits: Int): Int {
        var scfPos = 0
        val shift = 1 + gr_infos.scalefac_scale
        var xrpnt = xr
        var xrpntPos = 0
        val l = IntArray(3)
        var l3: Int
        var part2remain = gr_infos.part2_3_length - part2bits
        val me: Int

        /* fprintf(stderr,"part2remain = %d, gr_infos.part2_3_length = %d, part2bits = %d\n",
	       part2remain, gr_infos.part2_3_length, part2bits); */

        run {
            var i: Int

            i = MPG123.SBLIMIT * MPG123.SSLIMIT - xrpntPos shr 1
            while (i > 0) {
                xrpnt[xrpntPos++] = 0.0f
                xrpnt[xrpntPos++] = 0.0f
                i--
            }

            xrpnt = xr
            xrpntPos = 0
        }

        run {
            val bv = gr_infos.big_values
            val region1 = gr_infos.region1start
            val region2 = gr_infos.region2start

            l3 = (576 shr 1) - bv shr 1
            /*
	 * we may lose the 'odd' bit here !!
	 * check this later again
	 */
            if (bv <= region1) {
                l[0] = bv
                l[1] = 0
                l[2] = 0
            } else {
                l[0] = region1
                if (bv <= region2) {
                    l[1] = bv - l[0]
                    l[2] = 0
                } else {
                    l[1] = region2 - l[0]
                    l[2] = bv - region2
                }
            }
        }
        /* MDH crash fix */
        run {
            var i: Int
            i = 0
            while (i < 3) {
                if (l[i] < 0) {
                    System.err.printf("hip: Bogus region length (%d)\n", l[i])
                    l[i] = 0
                }
                i++
            }
        }
        /* end MDH crash fix */

        val pow2gain = gr_infos.pow2gain!!
        if (gr_infos.block_type == 2) {
            /*
	         * decoding with short or mixed mode BandIndex table
	         */
            var i: Int
            val max = IntArray(4)
            var step = 0
            var lwin = 0
            var cb = 0
            var v = 0.0f
            val m: IntArray
            var mc: Int
            var mPos = 0
            if (gr_infos.mixed_block_flag != 0) {
                max[3] = -1
                max[2] = 2
                max[1] = max[2]
                max[0] = max[1]
                m = map[sfreq][0]
                mPos = 0
                me = mapend[sfreq][0]
            } else {
                max[3] = -1
                max[2] = max[3]
                max[1] = max[2]
                max[0] = max[1]
                /* max[3] not really needed in this case */
                m = map[sfreq][1]
                mPos = 0
                me = mapend[sfreq][1]
            }

            mc = 0
            i = 0
            val full_gain = gr_infos.full_gain
            val full_gainPos = gr_infos.full_gainPos
            while (i < 2) {
                var lp = l[i]
                val h = Huffman.ht
                val hPos = gr_infos.table_select[i]

                while (lp != 0) {
                    var x: Int
                    var y: Int
                    if (0 == mc) {
                        mc = m[mPos++]
                        xrpnt = xr
                        xrpntPos = m[mPos++]
                        lwin = m[mPos++]
                        cb = m[mPos++]
                        if (lwin == 3) {
                            v = pow2gain[gr_infos.pow2gainPos + (scf[scfPos++] shl shift)]
                            step = 1
                        } else {
                            v = full_gain[lwin][full_gainPos[lwin] + (scf[scfPos++] shl shift)]
                            step = 3
                        }
                    }
                    run {
                        val _val = h[hPos].table
                        var valPos = 0
                        y = _val[valPos++].toInt()
                        while ((y ) < 0) {
                            if (get1bit(mp) != 0)
                                valPos -= y
                            part2remain--
                            y = _val[valPos++].toInt()
                        }
                        x = y shr 4
                        y = y and 0xf
                    }
                    if (x == 15) {
                        max[lwin] = cb
                        part2remain -= h[hPos].linbits + 1
                        x += common!!.getbits(mp, h[hPos].linbits)
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos] = -ispow[x] * v
                        else
                            xrpnt[xrpntPos] = ispow[x] * v
                    } else if (x != 0) {
                        max[lwin] = cb
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos] = -ispow[x] * v
                        else
                            xrpnt[xrpntPos] = ispow[x] * v
                        part2remain--
                    } else
                        xrpnt[xrpntPos] = 0.0f
                    xrpntPos += step
                    if (y == 15) {
                        max[lwin] = cb
                        part2remain -= h[hPos].linbits + 1
                        y += common!!.getbits(mp, h[hPos].linbits)
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos] = -ispow[y] * v
                        else
                            xrpnt[xrpntPos] = ispow[y] * v
                    } else if (y != 0) {
                        max[lwin] = cb
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos] = -ispow[y] * v
                        else
                            xrpnt[xrpntPos] = ispow[y] * v
                        part2remain--
                    } else
                        xrpnt[xrpntPos] = 0.0f
                    xrpntPos += step
                    lp--
                    mc--
                }
                i++
            }
            while (l3 != 0 && part2remain > 0) {
                val h = Huffman.htc
                val hPos = gr_infos.count1table_select
                val _val = h[hPos].table
                var valPos = 0
                var a: Short

                a = _val[valPos++]
                while ((a) < 0) {
                    part2remain--
                    if (part2remain < 0) {
                        part2remain++
                        a = 0
                        break
                    }
                    if (get1bit(mp) != 0)
                        valPos -= a.toInt()
                    a = _val[valPos++]
                }
                i = 0
                while (i < 4) {
                    if (0 == i and 1) {
                        if (0 == mc) {
                            mc = m[mPos++]
                            xrpnt = xr
                            xrpntPos = m[mPos++]
                            lwin = m[mPos++]
                            cb = m[mPos++]
                            if (lwin == 3) {
                                v = pow2gain[gr_infos.pow2gainPos + (scf[scfPos++] shl shift)]
                                step = 1
                            } else {
                                v = full_gain[lwin][full_gainPos[lwin] + (scf[scfPos++] shl shift)]
                                step = 3
                            }
                        }
                        mc--
                    }
                    if (a and (0x8 shr i) != 0) {
                        max[lwin] = cb
                        part2remain--
                        if (part2remain < 0) {
                            part2remain++
                            break
                        }
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos] = -v
                        else
                            xrpnt[xrpntPos] = v
                    } else
                        xrpnt[xrpntPos] = 0.0f
                    xrpntPos += step
                    i++
                }
                l3--
            }

            while (mPos < me) {
                if (0 == mc) {
                    mc = m[mPos++]
                    xrpnt = xr
                    xrpntPos = m[mPos++]
                    if (m[mPos++] == 3)
                        step = 1
                    else
                        step = 3
                    mPos++    /* cb */
                }
                mc--
                xrpnt[xrpntPos] = 0.0f
                xrpntPos += step
                xrpnt[xrpntPos] = 0.0f
                xrpntPos += step
                /* we could add a little opt. here:
	 * if we finished a band for window 3 or a long band
	 * further bands could copied in a simple loop without a
	 * special 'map' decoding
	 */
            }

            gr_infos.maxband[0] = max[0] + 1
            gr_infos.maxband[1] = max[1] + 1
            gr_infos.maxband[2] = max[2] + 1
            gr_infos.maxbandl = max[3] + 1

            run {
                var rmax = if (max[0] > max[1]) max[0] else max[1]
                rmax = (if (rmax > max[2]) rmax else max[2]) + 1
                gr_infos.maxb = if (rmax != 0) shortLimit[sfreq][rmax] else longLimit[sfreq][max[3] + 1]
            }

        } else {
            /*
	         * decoding with 'long' BandIndex table (block_type != 2)
	         */
            val pretab = if (gr_infos.preflag != 0) pretab1 else pretab2
            var pretabPos = 0
            var i: Int
            var max = -1
            var cb = 0
            val m = map[sfreq][2]
            var mPos = 0
            var v = 0.0f
            var mc = 0

            /*
	         * long hash table values
	         */
            i = 0
            while (i < 3) {
                var lp = l[i]
                val h = Huffman.ht
                val hPos = gr_infos.table_select[i]

                while (lp != 0) {
                    var x: Int
                    var y: Int

                    if (0 == mc) {
                        mc = m[mPos++]
                        v = pow2gain[gr_infos.pow2gainPos + (scf[scfPos++] + pretab[pretabPos++] shl shift)]
                        cb = m[mPos++]
                    }
                    run {
                        val _val = h[hPos].table
                        var valPos = 0
                        y = _val[valPos++].toInt()
                        while (y < 0) {
                            if (get1bit(mp) != 0)
                                valPos -= y
                            part2remain--
                            y = _val[valPos++].toInt()
                        }
                        x = y shr 4
                        y = y and 0xf
                    }
                    if (x == 15) {
                        max = cb
                        part2remain -= h[hPos].linbits + 1
                        x += common!!.getbits(mp, h[hPos].linbits)
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos++] = -ispow[x] * v
                        else
                            xrpnt[xrpntPos++] = ispow[x] * v
                    } else if (x != 0) {
                        max = cb
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos++] = -ispow[x] * v
                        else
                            xrpnt[xrpntPos++] = ispow[x] * v
                        part2remain--
                    } else
                        xrpnt[xrpntPos++] = 0.0f

                    if (y == 15) {
                        max = cb
                        part2remain -= h[hPos].linbits + 1
                        y += common!!.getbits(mp, h[hPos].linbits)
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos++] = -ispow[y] * v
                        else
                            xrpnt[xrpntPos++] = ispow[y] * v
                    } else if (y != 0) {
                        max = cb
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos++] = -ispow[y] * v
                        else
                            xrpnt[xrpntPos++] = ispow[y] * v
                        part2remain--
                    } else
                        xrpnt[xrpntPos++] = 0.0f
                    lp--
                    mc--
                }
                i++
            }

            /*
	         * short (count1table) values
	         */
            while (l3 != 0 && part2remain > 0) {
                val h = Huffman.htc
                val hPos = gr_infos.count1table_select
                val _val = h[hPos].table
                var valPos = 0
                var a: Short

                a = _val[valPos++]
                while (a < 0) {
                    part2remain--
                    if (part2remain < 0) {
                        part2remain++
                        a = 0
                        break
                    }
                    if (get1bit(mp) != 0)
                        valPos -= a.toInt()
                    a = _val[valPos++]
                }
                i = 0
                while (i < 4) {
                    if (0 == i and 1) {
                        if (0 == mc) {
                            mc = m[mPos++]
                            cb = m[mPos++]
                            v = pow2gain[gr_infos.pow2gainPos + (scf[scfPos++] + pretab[pretabPos++] shl shift)]
                        }
                        mc--
                    }
                    if (a and (0x8 shr i) != 0) {
                        max = cb
                        part2remain--
                        if (part2remain < 0) {
                            part2remain++
                            break
                        }
                        if (get1bit(mp) != 0)
                            xrpnt[xrpntPos++] = -v
                        else
                            xrpnt[xrpntPos++] = v
                    } else
                        xrpnt[xrpntPos++] = 0.0f
                    i++
                }
                l3--
            }

            /*
	         * zero part
	         */
            i = MPG123.SBLIMIT * MPG123.SSLIMIT - xrpntPos shr 1
            while (i != 0) {
                xrpnt[xrpntPos++] = 0.0f
                xrpnt[xrpntPos++] = 0.0f
                i--
            }

            gr_infos.maxbandl = max + 1
            gr_infos.maxb = longLimit[sfreq][gr_infos.maxbandl]
        }

        while (part2remain > 16) {
            common!!.getbits(mp, 16) /* Dismiss stuffing Bits */
            part2remain -= 16
        }
        if (part2remain > 0)
            common!!.getbits(mp, part2remain)
        else if (part2remain < 0) {
            System.err.printf("hip: Can't rewind stream by %d bits!\n", -part2remain)
            return 1       /* . error */
        }
        return 0
    }

    /*
	 * III_stereo: calculate real channel values for Joint-I-Stereo-mode
	 */
    private fun III_i_stereo(xr_buf: Array<FloatArray>, scalefac: IntArray,
                             gr_infos: gr_info_s, sfreq: Int, ms_stereo: Int, lsf: Int) {
        val bi = bandInfo[sfreq]
        val tabl1: FloatArray
        val tabl2: FloatArray

        if (lsf != 0) {
            val p = gr_infos.scalefac_compress and 0x1
            if (ms_stereo != 0) {
                tabl1 = pow1_2[p]
                tabl2 = pow2_2[p]
            } else {
                tabl1 = pow1_1[p]
                tabl2 = pow2_1[p]
            }
        } else {
            if (ms_stereo != 0) {
                tabl1 = tan1_2
                tabl2 = tan2_2
            } else {
                tabl1 = tan1_1
                tabl2 = tan2_1
            }
        }

        if (gr_infos.block_type == 2) {
            var lwin: Int
            var do_l = 0
            if (gr_infos.mixed_block_flag != 0)
                do_l = 1

            lwin = 0
            while (lwin < 3) { /* process each window */
                /* get first band with zero values */
                var is_p: Int
                var sb: Int
                var idx: Int
                var sfb = gr_infos.maxband[lwin] /* sfb is minimal 3 for mixed mode */
                if (sfb > 3)
                    do_l = 0

                while (sfb < 12) {
                    is_p = scalefac[sfb * 3 + lwin - gr_infos.mixed_block_flag] /* scale: 0-15 */
                    if (is_p != 7) {
                        val t1: Float
                        val t2: Float
                        sb = bi.shortDiff[sfb].toInt()
                        idx = bi.shortIdx[sfb] + lwin
                        t1 = tabl1[is_p]
                        t2 = tabl2[is_p]
                        while (sb > 0) {
                            val v = xr_buf[0][idx]
                            xr_buf[0][idx] = v * t1
                            xr_buf[1][idx] = v * t2
                            sb--
                            idx += 3
                        }
                    }
                    sfb++
                }

                /* in the original: copy 10 to 11 , here: copy 11 to 12
	maybe still wrong??? (copy 12 to 13?) */
                is_p = scalefac[11 * 3 + lwin - gr_infos.mixed_block_flag] /* scale: 0-15 */
                sb = bi.shortDiff[12].toInt()
                idx = bi.shortIdx[12] + lwin
                if (is_p != 7) {
                    val t1: Float
                    val t2: Float
                    t1 = tabl1[is_p]
                    t2 = tabl2[is_p]
                    while (sb > 0) {
                        val v = xr_buf[0][idx]
                        xr_buf[0][idx] = v * t1
                        xr_buf[1][idx] = v * t2
                        sb--
                        idx += 3
                    }
                }
                lwin++
            }               /* end for(lwin; .. ; . ) */

            if (do_l != 0) {
                /* also check l-part, if ALL bands in the three windows are 'empty'
	 * and mode = mixed_mode
	 */
                var sfb = gr_infos.maxbandl
                var idx = bi.longIdx[sfb].toInt()

                while (sfb < 8) {
                    var sb = bi.longDiff[sfb].toInt()
                    val is_p = scalefac[sfb] /* scale: 0-15 */
                    if (is_p != 7) {
                        val t1: Float
                        val t2: Float
                        t1 = tabl1[is_p]
                        t2 = tabl2[is_p]
                        while (sb > 0) {
                            val v = xr_buf[0][idx]
                            xr_buf[0][idx] = v * t1
                            xr_buf[1][idx] = v * t2
                            sb--
                            idx++
                        }
                    } else
                        idx += sb
                    sfb++
                }
            }
        } else {              /* ((gr_infos.block_type != 2)) */

            var sfb = gr_infos.maxbandl
            var is_p: Int
            var idx = bi.longIdx[sfb].toInt()
            while (sfb < 21) {
                var sb = bi.longDiff[sfb].toInt()
                is_p = scalefac[sfb] /* scale: 0-15 */
                if (is_p != 7) {
                    val t1: Float
                    val t2: Float
                    t1 = tabl1[is_p]
                    t2 = tabl2[is_p]
                    while (sb > 0) {
                        val v = xr_buf[0][idx]
                        xr_buf[0][idx] = v * t1
                        xr_buf[1][idx] = v * t2
                        sb--
                        idx++
                    }
                } else
                    idx += sb
                sfb++
            }

            is_p = scalefac[20] /* copy l-band 20 to l-band 21 */
            if (is_p != 7) {
                var sb: Int
                val t1 = tabl1[is_p]
                val t2 = tabl2[is_p]

                sb = bi.longDiff[21].toInt()
                while (sb > 0) {
                    val v = xr_buf[0][idx]
                    xr_buf[0][idx] = v * t1
                    xr_buf[1][idx] = v * t2
                    sb--
                    idx++
                }
            }
        }                   /* ... */
    }

    private fun III_antialias(xr: FloatArray, gr_infos: gr_info_s) {
        val sblim: Int

        if (gr_infos.block_type == 2) {
            if (0 == gr_infos.mixed_block_flag)
                return
            sblim = 1
        } else {
            sblim = gr_infos.maxb - 1
        }

        /* 31 alias-reduction operations between each pair of sub-bands */
        /* with 8 butterflies between each pair                         */

        run {
            var sb: Int
            var xr1Pos = MPG123.SSLIMIT

            sb = sblim
            while (sb != 0) {
                var ss: Int
                val cs = aa_cs
                val ca = aa_ca
                var caPos = 0
                var csPos = 0
                var xr2Pos = xr1Pos

                ss = 7
                while (ss >= 0) { /* upper and lower butterfly inputs */
                    val bu = xr[--xr2Pos]
                    val bd = xr[xr1Pos]
                    xr[xr2Pos] = bu * cs[csPos] - bd * ca[caPos]
                    xr[xr1Pos++] = bd * cs[csPos++] + bu * ca[caPos++]
                    ss--
                }
                sb--
                xr1Pos += 10
            }
        }
    }

    /*
	 DCT insipired by Jeff Tsay's DCT from the maplay package
	 this is an optimized version with manual unroll.

	 References:
	 [1] S. Winograd: "On Computing the Discrete Fourier Transform",
	     Mathematics of Computation, Volume 32, Number 141, January 1978,
	     Pages 175-199
	*/

    private fun dct36(inbuf: FloatArray, inbufPos: Int, o1: FloatArray, o1Pos: Int, o2: FloatArray, o2Pos: Int, wintab: FloatArray, tsbuf: FloatArray, tsPos: Int) {
        run {

            inbuf[inbufPos + 17] += inbuf[inbufPos + 16]
            inbuf[inbufPos + 16] += inbuf[inbufPos + 15]
            inbuf[inbufPos + 15] += inbuf[inbufPos + 14]
            inbuf[inbufPos + 14] += inbuf[inbufPos + 13]
            inbuf[inbufPos + 13] += inbuf[inbufPos + 12]
            inbuf[inbufPos + 12] += inbuf[inbufPos + 11]
            inbuf[inbufPos + 11] += inbuf[inbufPos + 10]
            inbuf[inbufPos + 10] += inbuf[inbufPos + 9]
            inbuf[inbufPos + 9] += inbuf[inbufPos + 8]
            inbuf[inbufPos + 8] += inbuf[inbufPos + 7]
            inbuf[inbufPos + 7] += inbuf[inbufPos + 6]
            inbuf[inbufPos + 6] += inbuf[inbufPos + 5]
            inbuf[inbufPos + 5] += inbuf[inbufPos + 4]
            inbuf[inbufPos + 4] += inbuf[inbufPos + 3]
            inbuf[inbufPos + 3] += inbuf[inbufPos + 2]
            inbuf[inbufPos + 2] += inbuf[inbufPos + 1]
            inbuf[inbufPos + 1] += inbuf[inbufPos + 0]

            inbuf[inbufPos + 17] += inbuf[inbufPos + 15]
            inbuf[inbufPos + 15] += inbuf[inbufPos + 13]
            inbuf[inbufPos + 13] += inbuf[inbufPos + 11]
            inbuf[inbufPos + 11] += inbuf[inbufPos + 9]
            inbuf[inbufPos + 9] += inbuf[inbufPos + 7]
            inbuf[inbufPos + 7] += inbuf[inbufPos + 5]
            inbuf[inbufPos + 5] += inbuf[inbufPos + 3]
            inbuf[inbufPos + 3] += inbuf[inbufPos + 1]

            run {

                val c = COS9

                val ta33: Float
                val ta66: Float
                val tb33: Float
                val tb66: Float

                ta33 = inbuf[inbufPos + 2 * 3 + 0] * c[3]
                ta66 = inbuf[inbufPos + 2 * 6 + 0] * c[6]
                tb33 = inbuf[inbufPos + 2 * 3 + 1] * c[3]
                tb66 = inbuf[inbufPos + 2 * 6 + 1] * c[6]

                run {
                    val tmp1a: Float
                    val tmp2a: Float
                    val tmp1b: Float
                    val tmp2b: Float
                    tmp1a = inbuf[inbufPos + 2 * 1 + 0] * c[1] + ta33 + inbuf[inbufPos + 2 * 5 + 0] * c[5] + inbuf[inbufPos + 2 * 7 + 0] * c[7]
                    tmp1b = inbuf[inbufPos + 2 * 1 + 1] * c[1] + tb33 + inbuf[inbufPos + 2 * 5 + 1] * c[5] + inbuf[inbufPos + 2 * 7 + 1] * c[7]
                    tmp2a = inbuf[inbufPos + 2 * 0 + 0] + inbuf[inbufPos + 2 * 2 + 0] * c[2] + inbuf[inbufPos + 2 * 4 + 0] * c[4] + ta66 + inbuf[inbufPos + 2 * 8 + 0] * c[8]
                    tmp2b = inbuf[inbufPos + 2 * 0 + 1] + inbuf[inbufPos + 2 * 2 + 1] * c[2] + inbuf[inbufPos + 2 * 4 + 1] * c[4] + tb66 + inbuf[inbufPos + 2 * 8 + 1] * c[8]

                    //			      MACRO1(0);
                    run {
                        var sum0 = tmp1a + tmp2a
                        val sum1 = (tmp1b + tmp2b) * tfcos36[0]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 0] = (tmp) * wintab[27 + 0]
                        o2[o2Pos + 8 - 0] = tmp * wintab[26 - 0]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 0)] = o1[o1Pos + 8 - 0] + sum0 * wintab[8 - 0]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 0)] = o1[o1Pos + 9 + 0] + sum0 * wintab[9 + 0]
                    }
                    //			      MACRO2(8);
                    run {
                        var sum0: Float
                        val sum1: Float
                        sum0 = tmp2a - tmp1a
                        sum1 = (tmp2b - tmp1b) * tfcos36[8]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 8] = (tmp ) * wintab[27 + 8]
                        o2[o2Pos + 8 - 8] = tmp * wintab[26 - 8]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 8)] = o1[o1Pos + 8 - 8] + sum0 * wintab[8 - 8]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 8)] = o1[o1Pos + 9 + 8] + sum0 * wintab[9 + 8]
                    }
                }

                run {
                    val tmp1a: Float
                    val tmp2a: Float
                    val tmp1b: Float
                    val tmp2b: Float
                    tmp1a = (inbuf[inbufPos + 2 * 1 + 0] - inbuf[inbufPos + 2 * 5 + 0] - inbuf[inbufPos + 2 * 7 + 0]) * c[3]
                    tmp1b = (inbuf[inbufPos + 2 * 1 + 1] - inbuf[inbufPos + 2 * 5 + 1] - inbuf[inbufPos + 2 * 7 + 1]) * c[3]
                    tmp2a = (inbuf[inbufPos + 2 * 2 + 0] - inbuf[inbufPos + 2 * 4 + 0] - inbuf[inbufPos + 2 * 8 + 0]) * c[6] - inbuf[inbufPos + 2 * 6 + 0] + inbuf[inbufPos + 2 * 0 + 0]
                    tmp2b = (inbuf[inbufPos + 2 * 2 + 1] - inbuf[inbufPos + 2 * 4 + 1] - inbuf[inbufPos + 2 * 8 + 1]) * c[6] - inbuf[inbufPos + 2 * 6 + 1] + inbuf[inbufPos + 2 * 0 + 1]

                    //			      MACRO1(1);
                    run {
                        var sum0 = tmp1a + tmp2a
                        val sum1 = (tmp1b + tmp2b) * tfcos36[1]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 1] = (tmp ) * wintab[27 + 1]
                        o2[o2Pos + 8 - 1] = tmp * wintab[26 - 1]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 1)] = o1[o1Pos + 8 - 1] + sum0 * wintab[8 - 1]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 1)] = o1[o1Pos + 9 + 1] + sum0 * wintab[9 + 1]
                    }
                    //			      MACRO2(7);
                    run {
                        var sum0: Float
                        val sum1: Float
                        sum0 = tmp2a - tmp1a
                        sum1 = (tmp2b - tmp1b) * tfcos36[7]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 7] = (tmp) * wintab[27 + 7]
                        o2[o2Pos + 8 - 7] = tmp * wintab[26 - 7]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 7)] = o1[o1Pos + 8 - 7] + sum0 * wintab[8 - 7]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 7)] = o1[o1Pos + 9 + 7] + sum0 * wintab[9 + 7]
                    }
                }

                run {
                    val tmp1a: Float
                    val tmp2a: Float
                    val tmp1b: Float
                    val tmp2b: Float
                    tmp1a = inbuf[inbufPos + 2 * 1 + 0] * c[5] - ta33 - inbuf[inbufPos + 2 * 5 + 0] * c[7] + inbuf[inbufPos + 2 * 7 + 0] * c[1]
                    tmp1b = inbuf[inbufPos + 2 * 1 + 1] * c[5] - tb33 - inbuf[inbufPos + 2 * 5 + 1] * c[7] + inbuf[inbufPos + 2 * 7 + 1] * c[1]
                    tmp2a = inbuf[inbufPos + 2 * 0 + 0] - inbuf[inbufPos + 2 * 2 + 0] * c[8] - inbuf[inbufPos + 2 * 4 + 0] * c[2] + ta66 + inbuf[inbufPos + 2 * 8 + 0] * c[4]
                    tmp2b = inbuf[inbufPos + 2 * 0 + 1] - inbuf[inbufPos + 2 * 2 + 1] * c[8] - inbuf[inbufPos + 2 * 4 + 1] * c[2] + tb66 + inbuf[inbufPos + 2 * 8 + 1] * c[4]

                    //			      MACRO1(2);
                    run {
                        var sum0 = tmp1a + tmp2a
                        val sum1 = (tmp1b + tmp2b) * tfcos36[2]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 2] = (tmp) * wintab[27 + 2]
                        o2[o2Pos + 8 - 2] = tmp * wintab[26 - 2]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 2)] = o1[o1Pos + 8 - 2] + sum0 * wintab[8 - 2]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 2)] = o1[o1Pos + 9 + 2] + sum0 * wintab[9 + 2]
                    }
                    //			      MACRO2(6);
                    run {
                        var sum0: Float
                        val sum1: Float
                        sum0 = tmp2a - tmp1a
                        sum1 = (tmp2b - tmp1b) * tfcos36[6]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 6] = (tmp ) * wintab[27 + 6]
                        o2[o2Pos + 8 - 6] = tmp * wintab[26 - 6]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 6)] = o1[o1Pos + 8 - 6] + sum0 * wintab[8 - 6]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 6)] = o1[o1Pos + 9 + 6] + sum0 * wintab[9 + 6]
                    }
                }

                run {
                    val tmp1a: Float
                    val tmp2a: Float
                    val tmp1b: Float
                    val tmp2b: Float
                    tmp1a = inbuf[inbufPos + 2 * 1 + 0] * c[7] - ta33 + inbuf[inbufPos + 2 * 5 + 0] * c[1] - inbuf[inbufPos + 2 * 7 + 0] * c[5]
                    tmp1b = inbuf[inbufPos + 2 * 1 + 1] * c[7] - tb33 + inbuf[inbufPos + 2 * 5 + 1] * c[1] - inbuf[inbufPos + 2 * 7 + 1] * c[5]
                    tmp2a = inbuf[inbufPos + 2 * 0 + 0] - inbuf[inbufPos + 2 * 2 + 0] * c[4] + inbuf[inbufPos + 2 * 4 + 0] * c[8] + ta66 - inbuf[inbufPos + 2 * 8 + 0] * c[2]
                    tmp2b = inbuf[inbufPos + 2 * 0 + 1] - inbuf[inbufPos + 2 * 2 + 1] * c[4] + inbuf[inbufPos + 2 * 4 + 1] * c[8] + tb66 - inbuf[inbufPos + 2 * 8 + 1] * c[2]

                    //			      MACRO1(3);
                    run {
                        var sum0 = tmp1a + tmp2a
                        val sum1 = (tmp1b + tmp2b) * tfcos36[3]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 3] = (tmp ) * wintab[27 + 3]
                        o2[o2Pos + 8 - 3] = tmp * wintab[26 - 3]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 3)] = o1[o1Pos + 8 - 3] + sum0 * wintab[8 - 3]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 3)] = o1[o1Pos + 9 + 3] + sum0 * wintab[9 + 3]
                    }
                    //			      MACRO2(5);
                    run {
                        var sum0: Float
                        val sum1: Float
                        sum0 = tmp2a - tmp1a
                        sum1 = (tmp2b - tmp1b) * tfcos36[5]
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 5] = (tmp ) * wintab[27 + 5]
                        o2[o2Pos + 8 - 5] = tmp * wintab[26 - 5]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 5)] = o1[o1Pos + 8 - 5] + sum0 * wintab[8 - 5]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 5)] = o1[o1Pos + 9 + 5] + sum0 * wintab[9 + 5]
                    }
                }

                run {
                    var sum0: Float
                    val sum1: Float
                    sum0 = inbuf[inbufPos + 2 * 0 + 0] - inbuf[inbufPos + 2 * 2 + 0] + inbuf[inbufPos + 2 * 4 + 0] - inbuf[inbufPos + 2 * 6 + 0] + inbuf[inbufPos + 2 * 8 + 0]
                    sum1 = (inbuf[inbufPos + 2 * 0 + 1] - inbuf[inbufPos + 2 * 2 + 1] + inbuf[inbufPos + 2 * 4 + 1] - inbuf[inbufPos + 2 * 6 + 1] + inbuf[inbufPos + 2 * 8 + 1]) * tfcos36[4]
                    //			      MACRO0(4)
                    run {
                        val tmp: Float
                        tmp = sum0 + sum1
                        o2[o2Pos + 9 + 4] = (tmp) * wintab[27 + 4]
                        o2[o2Pos + 8 - 4] = tmp * wintab[26 - 4]
                        sum0 -= sum1
                        tsbuf[tsPos + MPG123.SBLIMIT * (8 - 4)] = o1[o1Pos + 8 - 4] + sum0 * wintab[8 - 4]
                        tsbuf[tsPos + MPG123.SBLIMIT * (9 + 4)] = o1[o1Pos + 9 + 4] + sum0 * wintab[9 + 4]
                    }
                }
            }

        }
    }

    /*
	 * new DCT12
	 */
    private fun dct12(`in`: FloatArray, inbufPos: Int, rawout1: FloatArray, rawout1Pos: Int, rawout2: FloatArray, rawout2Pos: Int, wi: FloatArray, ts: FloatArray, tsPos: Int) {
        var inbufPos = inbufPos
        run {
            var in0: Float
            var in1: Float
            var in2: Float
            var in3: Float
            var in4: Float
            var in5: Float
            ts[tsPos + MPG123.SBLIMIT * 0] = rawout1[rawout1Pos + 0]
            ts[tsPos + MPG123.SBLIMIT * 1] = rawout1[rawout1Pos + 1]
            ts[tsPos + MPG123.SBLIMIT * 2] = rawout1[rawout1Pos + 2]
            ts[tsPos + MPG123.SBLIMIT * 3] = rawout1[rawout1Pos + 3]
            ts[tsPos + MPG123.SBLIMIT * 4] = rawout1[rawout1Pos + 4]
            ts[tsPos + MPG123.SBLIMIT * 5] = rawout1[rawout1Pos + 5]

            //		DCT12_PART1
            run {
                in5 = `in`[inbufPos + 5 * 3]
                in4 = `in`[inbufPos + 4 * 3]
                in5 += (in4 )
                in3 = `in`[inbufPos + 3 * 3]
                in4 += (in3 )
                in2 = `in`[inbufPos + 2 * 3]
                in3 += (in2)
                in1 = `in`[inbufPos + 1 * 3]
                in2 += (in1)
                in0 = `in`[inbufPos + 0 * 3]
                in1 += (in0 )

                in5 += in3
                in3 += in1

                in2 *= COS6_1
                in3 *= COS6_1
            }
            run {
                val tmp0: Float
                var tmp1 = in0 - in4
                run {
                    val tmp2 = (in1 - in5) * tfcos12[1]
                    tmp0 = tmp1 + tmp2
                    tmp1 -= tmp2
                }
                ts[tsPos + (17 - 1) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 17 - 1] + tmp0 * wi[11 - 1]
                ts[tsPos + (12 + 1) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 12 + 1] + tmp0 * wi[6 + 1]
                ts[tsPos + (6 + 1) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 6 + 1] + tmp1 * wi[1]
                ts[tsPos + (11 - 1) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 11 - 1] + tmp1 * wi[5 - 1]
            }

            //		DCT12_PART2
            run {
                in0 += in4 * COS6_2

                in4 = in0 + in2
                in0 -= in2

                in1 += in5 * COS6_2

                in5 = (in1 + in3) * tfcos12[0]
                in1 = (in1 - in3) * tfcos12[2]

                in3 = in4 + in5
                in4 -= in5

                in2 = in0 + in1
                in0 -= in1
            }
            ts[tsPos + (17 - 0) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 17 - 0] + in2 * wi[11 - 0]
            ts[tsPos + (12 + 0) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 12 + 0] + in2 * wi[6 + 0]
            ts[tsPos + (12 + 2) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 12 + 2] + in3 * wi[6 + 2]
            ts[tsPos + (17 - 2) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 17 - 2] + in3 * wi[11 - 2]

            ts[tsPos + (6 + 0) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 6 + 0] + in0 * wi[0]
            ts[tsPos + (11 - 0) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 11 - 0] + in0 * wi[5 - 0]
            ts[tsPos + (6 + 2) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 6 + 2] + in4 * wi[2]
            ts[tsPos + (11 - 2) * MPG123.SBLIMIT] = rawout1[rawout1Pos + 11 - 2] + in4 * wi[5 - 2]
        }

        inbufPos++

        run {
            var in0: Float
            var in1: Float
            var in2: Float
            var in3: Float
            var in4: Float
            var in5: Float

            //		DCT12_PART1
            run {
                in5 = `in`[inbufPos + 5 * 3]
                in4 = `in`[inbufPos + 4 * 3]
                in5 += (in4 )
                in3 = `in`[inbufPos + 3 * 3]
                in4 += (in3 )
                in2 = `in`[inbufPos + 2 * 3]
                in3 += (in2 )
                in1 = `in`[inbufPos + 1 * 3]
                in2 += (in1 )
                in0 = `in`[inbufPos + 0 * 3]
                in1 += (in0 )

                in5 += in3
                in3 += in1

                in2 *= COS6_1
                in3 *= COS6_1
            }

            run {
                val tmp0: Float
                var tmp1 = in0 - in4
                run {
                    val tmp2 = (in1 - in5) * tfcos12[1]
                    tmp0 = tmp1 + tmp2
                    tmp1 -= tmp2
                }
                rawout2[rawout2Pos + 5 - 1] = tmp0 * wi[11 - 1]
                rawout2[rawout2Pos + 0 + 1] = tmp0 * wi[6 + 1]
                ts[tsPos + (12 + 1) * MPG123.SBLIMIT] += tmp1 * wi[1]
                ts[tsPos + (17 - 1) * MPG123.SBLIMIT] += tmp1 * wi[5 - 1]
            }

            //		DCT12_PART2
            run {
                in0 += in4 * COS6_2

                in4 = in0 + in2
                in0 -= in2

                in1 += in5 * COS6_2

                in5 = (in1 + in3) * tfcos12[0]
                in1 = (in1 - in3) * tfcos12[2]

                in3 = in4 + in5
                in4 -= in5

                in2 = in0 + in1
                in0 -= in1
            }

            rawout2[rawout2Pos + 5 - 0] = in2 * wi[11 - 0]
            rawout2[rawout2Pos + 0 + 0] = in2 * wi[6 + 0]
            rawout2[rawout2Pos + 0 + 2] = in3 * wi[6 + 2]
            rawout2[rawout2Pos + 5 - 2] = in3 * wi[11 - 2]

            ts[tsPos + (12 + 0) * MPG123.SBLIMIT] += in0 * wi[0]
            ts[tsPos + (17 - 0) * MPG123.SBLIMIT] += in0 * wi[5 - 0]
            ts[tsPos + (12 + 2) * MPG123.SBLIMIT] += in4 * wi[2]
            ts[tsPos + (17 - 2) * MPG123.SBLIMIT] += in4 * wi[5 - 2]
        }

        inbufPos++

        run {
            var in0: Float
            var in1: Float
            var in2: Float
            var in3: Float
            var in4: Float
            var in5: Float
            rawout2[rawout2Pos + 17] = 0.0f
            rawout2[rawout2Pos + 16] = rawout2[rawout2Pos + 17]
            rawout2[rawout2Pos + 15] = rawout2[rawout2Pos + 16]
            rawout2[rawout2Pos + 14] = rawout2[rawout2Pos + 15]
            rawout2[rawout2Pos + 13] = rawout2[rawout2Pos + 14]
            rawout2[rawout2Pos + 12] = rawout2[rawout2Pos + 13]

            //		DCT12_PART1
            run {
                in5 = `in`[inbufPos + 5 * 3]
                in4 = `in`[inbufPos + 4 * 3]
                in5 += (in4 )
                in3 = `in`[inbufPos + 3 * 3]
                in4 += (in3 )
                in2 = `in`[inbufPos + 2 * 3]
                in3 += (in2 )
                in1 = `in`[inbufPos + 1 * 3]
                in2 += (in1 )
                in0 = `in`[inbufPos + 0 * 3]
                in1 += (in0 )

                in5 += in3
                in3 += in1

                in2 *= COS6_1
                in3 *= COS6_1
            }

            run {
                val tmp0: Float
                var tmp1 = in0 - in4
                run {
                    val tmp2 = (in1 - in5) * tfcos12[1]
                    tmp0 = tmp1 + tmp2
                    tmp1 -= tmp2
                }
                rawout2[rawout2Pos + 11 - 1] = tmp0 * wi[11 - 1]
                rawout2[rawout2Pos + 6 + 1] = tmp0 * wi[6 + 1]
                rawout2[rawout2Pos + 0 + 1] += tmp1 * wi[1]
                rawout2[rawout2Pos + 5 - 1] += tmp1 * wi[5 - 1]
            }

            //		DCT12_PART2
            run {
                in0 += in4 * COS6_2

                in4 = in0 + in2
                in0 -= in2

                in1 += in5 * COS6_2

                in5 = (in1 + in3) * tfcos12[0]
                in1 = (in1 - in3) * tfcos12[2]

                in3 = in4 + in5
                in4 -= in5

                in2 = in0 + in1
                in0 -= in1
            }

            rawout2[rawout2Pos + 11 - 0] = in2 * wi[11 - 0]
            rawout2[rawout2Pos + 6 + 0] = in2 * wi[6 + 0]
            rawout2[rawout2Pos + 6 + 2] = in3 * wi[6 + 2]
            rawout2[rawout2Pos + 11 - 2] = in3 * wi[11 - 2]

            rawout2[rawout2Pos + 0 + 0] += in0 * wi[0]
            rawout2[rawout2Pos + 5 - 0] += in0 * wi[5 - 0]
            rawout2[rawout2Pos + 0 + 2] += in4 * wi[2]
            rawout2[rawout2Pos + 5 - 2] += in4 * wi[5 - 2]
        }
    }

    /*
	 * III_hybrid
	 */
    private fun III_hybrid(mp: mpstr_tag, fsIn: FloatArray, tsOut: FloatArray,
                           ch: Int, gr_infos: gr_info_s) {
        var tspntPos = 0
        val block = mp.hybrid_block
        val blc = mp.hybrid_blc
        val rawout1: FloatArray
        val rawout2: FloatArray
        var rawout1Pos: Int
        var rawout2Pos: Int
        val bt: Int
        var sb = 0

        run {
            var b = blc[ch]
            rawout1 = block[b][ch]
            rawout1Pos = 0
            b = -b + 1
            rawout2 = block[b][ch]
            rawout2Pos = 0
            blc[ch] = b
        }


        if (gr_infos.mixed_block_flag != 0) {
            sb = 2
            dct36(fsIn, 0 * MPG123.SSLIMIT, rawout1, rawout1Pos, rawout2, rawout2Pos, win[0], tsOut, tspntPos + 0)
            dct36(fsIn, 1 * MPG123.SSLIMIT, rawout1, rawout1Pos + 18, rawout2, rawout2Pos + 18, win1[0], tsOut, tspntPos + 1)
            rawout1Pos += 36
            rawout2Pos += 36
            tspntPos += 2
        }

        bt = gr_infos.block_type
        if (bt == 2) {
            while (sb < gr_infos.maxb) {
                dct12(fsIn, sb * MPG123.SSLIMIT, rawout1, rawout1Pos, rawout2, rawout2Pos, win[2], tsOut, tspntPos + 0)
                dct12(fsIn, (sb + 1) * MPG123.SSLIMIT, rawout1, rawout1Pos + 18, rawout2, rawout2Pos + 18, win1[2], tsOut, tspntPos + 1)
                sb += 2
                tspntPos += 2
                rawout1Pos += 36
                rawout2Pos += 36
            }
        } else {
            while (sb < gr_infos.maxb) {
                dct36(fsIn, sb * MPG123.SSLIMIT, rawout1, rawout1Pos, rawout2, rawout2Pos, win[bt], tsOut, tspntPos + 0)
                dct36(fsIn, (sb + 1) * MPG123.SSLIMIT, rawout1, rawout1Pos + 18, rawout2, rawout2Pos + 18, win1[bt], tsOut, tspntPos + 1)
                sb += 2
                tspntPos += 2
                rawout1Pos += 36
                rawout2Pos += 36
            }
        }

        while (sb < MPG123.SBLIMIT) {
            var i: Int
            i = 0
            while (i < MPG123.SSLIMIT) {
                tsOut[tspntPos + i * MPG123.SBLIMIT] = rawout1[rawout1Pos++]
                rawout2[rawout2Pos++] = 0.0f
                i++
            }
            sb++
            tspntPos++
        }
    }

    fun layer3_audiodata_precedesframes(mp: mpstr_tag): Int {
        val audioDataInFrame: Int
        val framesToBacktrack: Int

        /* specific to Layer 3, since Layer 1 & 2 the audio data starts at the frame that describes it. */
        /* determine how many bytes and therefore bitstream frames the audio data precedes it's matching frame */
        /* fprintf(stderr, "hip: main_data_begin = %d, mp.bsize %d, mp.fsizeold %d, mp.ssize %d\n",
	       sideinfo.main_data_begin, mp.bsize, mp.fsizeold, mp.ssize); */
        /* compute the number of frames to backtrack, 4 for the header, ssize already holds the CRC */
        /* TODO Erroneously assumes current frame is same as previous frame. */
        audioDataInFrame = mp.bsize - 4 - mp.ssize
        framesToBacktrack = (sideinfo.main_data_begin + audioDataInFrame - 1) / audioDataInFrame
        /* fprintf(stderr, "hip: audioDataInFrame %d framesToBacktrack %d\n", audioDataInFrame, framesToBacktrack); */
        return framesToBacktrack
    }

    fun do_layer3_sideinfo(mp: mpstr_tag): Int {
        val fr = mp.fr
        val stereo = fr.stereo
        var single = fr.single
        val ms_stereo: Int
        val sfreq = fr.sampling_frequency
        val granules: Int
        var ch: Int
        var gr: Int
        var databits: Int

        if (stereo == 1) {  /* stream is mono */
            single = 0
        }

        if (fr.mode == MPG123.MPG_MD_JOINT_STEREO) {
            ms_stereo = fr.mode_ext and 0x2
        } else
            ms_stereo = 0


        if (fr.lsf != 0) {
            granules = 1
            III_get_side_info_2(mp, sideinfo, stereo, ms_stereo, sfreq, single)
        } else {
            granules = 2
            III_get_side_info_1(mp, sideinfo, stereo, ms_stereo, sfreq, single)
        }

        databits = 0
        gr = 0
        while (gr < granules) {
            ch = 0
            while (ch < stereo) {
                val gr_infos = sideinfo.ch[ch].gr[gr]
                databits += gr_infos.part2_3_length
                ++ch
            }
            ++gr
        }
        return databits - 8 * sideinfo.main_data_begin
    }

    fun <T> do_layer3(mp: mpstr_tag, pcm_sample: Array<T>, pcm_point: ProcessedBytes,
                      synth: ISynth, tFactory: Factory<T>): Int {
        var gr: Int
        var ch: Int
        var ss: Int
        var clip = 0
        val scalefacs = Array(2) { IntArray(39) } /* max 39 for short[13][3] mode, mixed: 38, long: 22 */
        /*  struct III_sideinfo sideinfo; */
        val fr = mp.fr
        val stereo = fr.stereo
        var single = fr.single
        val ms_stereo: Int
        val i_stereo: Int
        val sfreq = fr.sampling_frequency
        val stereo1: Int
        val granules: Int

        if (common!!.set_pointer(mp, sideinfo.main_data_begin) == MPGLib.MP3_ERR)
            return 0

        if (stereo == 1) {  /* stream is mono */
            stereo1 = 1
            single = 0
        } else if (single >= 0)
        /* stream is stereo, but force to mono */
            stereo1 = 1
        else
            stereo1 = 2

        if (fr.mode == MPG123.MPG_MD_JOINT_STEREO) {
            ms_stereo = fr.mode_ext and 0x2
            i_stereo = fr.mode_ext and 0x1
        } else {
            i_stereo = 0
            ms_stereo = i_stereo
        }


        if (fr.lsf != 0) {
            granules = 1
        } else {
            granules = 2
        }

        gr = 0
        while (gr < granules) {

            run {
                val gr_infos = sideinfo.ch[0].gr[gr]
                val part2bits: Int

                if (fr.lsf != 0)
                    part2bits = III_get_scale_factors_2(mp, scalefacs[0], gr_infos, 0)
                else {
                    part2bits = III_get_scale_factors_1(mp, scalefacs[0], gr_infos)
                }

                if (mp.pinfo != null) {
                    var i: Int
                    mp.pinfo!!.sfbits[gr][0] = part2bits
                    i = 0
                    while (i < 39) {
                        mp.pinfo!!.sfb_s[gr][0][i] = scalefacs[0][i].toDouble()
                        i++
                    }
                }

                /* fprintf(stderr, "calling III dequantize sample 1 gr_infos.part2_3_length %d\n", gr_infos.part2_3_length); */
                if (III_dequantize_sample(mp, hybridIn[0], scalefacs[0], gr_infos, sfreq, part2bits) != 0)
                    return clip
            }
            if (stereo == 2) {
                val gr_infos = sideinfo.ch[1].gr[gr]
                val part2bits: Int
                if (fr.lsf != 0)
                    part2bits = III_get_scale_factors_2(mp, scalefacs[1], gr_infos, i_stereo)
                else {
                    part2bits = III_get_scale_factors_1(mp, scalefacs[1], gr_infos)
                }
                if (mp.pinfo != null) {
                    var i: Int
                    mp.pinfo!!.sfbits[gr][1] = part2bits
                    i = 0
                    while (i < 39) {
                        mp.pinfo!!.sfb_s[gr][1][i] = scalefacs[1][i].toDouble()
                        i++
                    }
                }

                /* fprintf(stderr, "calling III dequantize sample 2  gr_infos.part2_3_length %d\n", gr_infos.part2_3_length); */
                if (III_dequantize_sample(mp, hybridIn[1], scalefacs[1], gr_infos, sfreq, part2bits) != 0)
                    return clip

                if (ms_stereo != 0) {
                    var i: Int
                    i = 0
                    while (i < MPG123.SBLIMIT * MPG123.SSLIMIT) {
                        val tmp0: Float
                        val tmp1: Float
                        tmp0 = hybridIn[0][i]
                        tmp1 = hybridIn[1][i]
                        hybridIn[1][i] = tmp0 - tmp1
                        hybridIn[0][i] = tmp0 + tmp1
                        i++
                    }
                }

                if (i_stereo != 0)
                    III_i_stereo(hybridIn, scalefacs[1], gr_infos, sfreq, ms_stereo, fr.lsf)

                if (ms_stereo != 0 || i_stereo != 0 || single == 3) {
                    if (gr_infos.maxb > sideinfo.ch[0].gr[gr].maxb)
                        sideinfo.ch[0].gr[gr].maxb = gr_infos.maxb
                    else
                        gr_infos.maxb = sideinfo.ch[0].gr[gr].maxb
                }

                when (single) {
                    3 -> {
                        var i: Int
                        val in0 = hybridIn[0]
                        var in0Pos = 0
                        var in1Pos = 0
                        i = 0
                        while (i < MPG123.SSLIMIT * gr_infos.maxb) {
                            in0[in0Pos] = in0[in0Pos] + hybridIn[1][in1Pos++]
                            i++
                            in0Pos++
                        } /* *0.5 done by pow-scale */
                    }
                    1 -> {
                        var i: Int
                        var in0Pos = 0
                        var in1Pos = 0
                        i = 0
                        while (i < MPG123.SSLIMIT * gr_infos.maxb) {
                            hybridIn[0][in0Pos++] = hybridIn[1][in1Pos++]
                            i++
                        }
                    }
                }
            }

            if (mp.pinfo != null) {
                var i: Int
                var sb: Int
                var ifqstep: Float

                mp.pinfo!!.bitrate = Common.tabsel_123[fr.lsf][fr.lay - 1][fr.bitrate_index]
                mp.pinfo!!.sampfreq = Common.freqs[sfreq]
                mp.pinfo!!.emph = fr.emphasis
                mp.pinfo!!.crc = if (fr.error_protection) 1 else 0
                mp.pinfo!!.padding = fr.padding
                mp.pinfo!!.stereo = fr.stereo
                mp.pinfo!!.js = if (fr.mode == MPG123.MPG_MD_JOINT_STEREO) 1 else 0
                mp.pinfo!!.ms_stereo = ms_stereo
                mp.pinfo!!.i_stereo = i_stereo
                mp.pinfo!!.maindata = sideinfo.main_data_begin

                ch = 0
                while (ch < stereo1) {
                    val gr_infos = sideinfo.ch[ch].gr[gr]
                    mp.pinfo!!.big_values[gr][ch] = gr_infos.big_values
                    mp.pinfo!!.scalefac_scale[gr][ch] = gr_infos.scalefac_scale
                    mp.pinfo!!.mixed[gr][ch] = gr_infos.mixed_block_flag
                    mp.pinfo!!.mpg123blocktype[gr][ch] = gr_infos.block_type
                    mp.pinfo!!.mainbits[gr][ch] = gr_infos.part2_3_length
                    mp.pinfo!!.preflag[gr][ch] = gr_infos.preflag
                    if (gr == 1)
                        mp.pinfo!!.scfsi[ch] = gr_infos.scfsi
                    ch++
                }


                ch = 0
                while (ch < stereo1) {
                    val gr_infos = sideinfo.ch[ch].gr[gr]
                    ifqstep = if (mp.pinfo!!.scalefac_scale[gr][ch] == 0) .5f else 1.0f
                    if (2 == gr_infos.block_type) {
                        i = 0
                        while (i < 3) {
                            sb = 0
                            while (sb < 12) {
                                val j = 3 * sb + i
                                /*
	                               is_p = scalefac[sfb*3+lwin-gr_infos.mixed_block_flag];
	                             */
                                /* scalefac was copied into pinfo.sfb_s[] above */
                                mp.pinfo!!.sfb_s[gr][ch][j] = -ifqstep * mp.pinfo!!.sfb_s[gr][ch][j - gr_infos.mixed_block_flag]
                                mp.pinfo!!.sfb_s[gr][ch][j] -= (2 * mp.pinfo!!.sub_gain[gr][ch][i]).toDouble()
                                sb++
                            }
                            mp.pinfo!!.sfb_s[gr][ch][3 * sb + i] = (-2 * mp.pinfo!!.sub_gain[gr][ch][i]).toDouble()
                            i++
                        }
                    } else {
                        sb = 0
                        while (sb < 21) {
                            /* scalefac was copied into pinfo.sfb[] above */
                            mp.pinfo!!.sfb[gr][ch][sb] = mp.pinfo!!.sfb_s[gr][ch][sb]
                            if (gr_infos.preflag != 0)
                                mp.pinfo!!.sfb[gr][ch][sb] += pretab1[sb].toDouble()
                            mp.pinfo!!.sfb[gr][ch][sb] *= (-ifqstep).toDouble()
                            sb++
                        }
                        mp.pinfo!!.sfb[gr][ch][21] = 0.0
                    }
                    ch++
                }



                ch = 0
                while (ch < stereo1) {
                    var j = 0
                    sb = 0
                    while (sb < MPG123.SBLIMIT) {
//                        {
                            ss = 0
                            while (ss < MPG123.SSLIMIT) {
                                mp.pinfo!!.mpg123xr[gr][ch][j] = hybridIn[ch][sb * MPG123.SSLIMIT + ss].toDouble()
                                ss++
                                j++
                            }
//                        }
                        sb++
                    }
                    ch++
                }
            }


            ch = 0
            while (ch < stereo1) {
                val gr_infos = sideinfo.ch[ch].gr[gr]
                III_antialias(hybridIn[ch], gr_infos)
                III_hybrid(mp, hybridIn[ch], hybridOut[ch], ch, gr_infos)
                ch++
            }

            ss = 0
            while (ss < MPG123.SSLIMIT) {
                if (single >= 0) {
                    clip += synth.synth_1to1_mono_ptr(mp, hybridOut[0], ss * MPG123.SBLIMIT, pcm_sample, pcm_point, tFactory)
                } else {
                    val p1 = ProcessedBytes()
                    p1.pb = pcm_point.pb
                    clip += synth.synth_1to1_ptr(mp, hybridOut[0], ss * MPG123.SBLIMIT, 0, pcm_sample, p1, tFactory)
                    clip += synth.synth_1to1_ptr(mp, hybridOut[1], ss * MPG123.SBLIMIT, 1, pcm_sample, pcm_point, tFactory)
                }
                ss++
            }
            gr++
        }

        return clip
    }

    companion object {

        private val bandInfo = arrayOf(

                /* MPEG 1.0 */
                bandInfoStruct(
                        shortArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 52, 62, 74, 90, 110, 134, 162, 196, 238, 288, 342, 418, 576),
                        shortArrayOf(4, 4, 4, 4, 4, 4, 6, 6, 8, 8, 10, 12, 16, 20, 24, 28, 34, 42, 50, 54, 76, 158),
                        shortArrayOf(0, (4 * 3).toShort(), (8 * 3).toShort(), (12 * 3).toShort(), (16 * 3).toShort(), (22 * 3).toShort(), (30 * 3).toShort(), (40 * 3).toShort(), (52 * 3).toShort(), (66 * 3).toShort(), (84 * 3).toShort(), (106 * 3).toShort(), (136 * 3).toShort(), (192 * 3).toShort()),
                        shortArrayOf(4, 4, 4, 4, 6, 8, 10, 12, 14, 18, 22, 30, 56)),

                bandInfoStruct(
                        shortArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 42, 50, 60, 72, 88, 106, 128, 156, 190, 230, 276, 330, 384, 576),
                        shortArrayOf(4, 4, 4, 4, 4, 4, 6, 6, 6, 8, 10, 12, 16, 18, 22, 28, 34, 40, 46, 54, 54, 192),
                        shortArrayOf(0, (4 * 3).toShort(), (8 * 3).toShort(), (12 * 3).toShort(), (16 * 3).toShort(), (22 * 3).toShort(), (28 * 3).toShort(), (38 * 3).toShort(), (50 * 3).toShort(), (64 * 3).toShort(), (80 * 3).toShort(), (100 * 3).toShort(), (126 * 3).toShort(), (192 * 3).toShort()),
                        shortArrayOf(4, 4, 4, 4, 6, 6, 10, 12, 14, 16, 20, 26, 66)),

                bandInfoStruct(
                        shortArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 54, 66, 82, 102, 126, 156, 194, 240, 296, 364, 448, 550, 576),
                        shortArrayOf(4, 4, 4, 4, 4, 4, 6, 6, 8, 10, 12, 16, 20, 24, 30, 38, 46, 56, 68, 84, 102, 26),
                        shortArrayOf(0, (4 * 3).toShort(), (8 * 3).toShort(), (12 * 3).toShort(), (16 * 3).toShort(), (22 * 3).toShort(), (30 * 3).toShort(), (42 * 3).toShort(), (58 * 3).toShort(), (78 * 3).toShort(), (104 * 3).toShort(), (138 * 3).toShort(), (180 * 3).toShort(), (192 * 3).toShort()),
                        shortArrayOf(4, 4, 4, 4, 6, 8, 12, 16, 20, 26, 34, 42, 12)),

                /* MPEG 2.0 */
                bandInfoStruct(
                        shortArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                        shortArrayOf(6, 6, 6, 6, 6, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 38, 46, 52, 60, 68, 58, 54),
                        shortArrayOf(0, (4 * 3).toShort(), (8 * 3).toShort(), (12 * 3).toShort(), (18 * 3).toShort(), (24 * 3).toShort(), (32 * 3).toShort(), (42 * 3).toShort(), (56 * 3).toShort(), (74 * 3).toShort(), (100 * 3).toShort(), (132 * 3).toShort(), (174 * 3).toShort(), (192 * 3).toShort()),
                        shortArrayOf(4, 4, 4, 6, 6, 8, 10, 14, 18, 26, 32, 42, 18)),
                /* docs: 332. mpg123: 330 */
                bandInfoStruct(
                        shortArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 114, 136, 162, 194, 232, 278, 332, 394, 464, 540, 576),
                        shortArrayOf(6, 6, 6, 6, 6, 6, 8, 10, 12, 14, 16, 18, 22, 26, 32, 38, 46, 54, 62, 70, 76, 36),
                        shortArrayOf(0, (4 * 3).toShort(), (8 * 3).toShort(), (12 * 3).toShort(), (18 * 3).toShort(), (26 * 3).toShort(), (36 * 3).toShort(), (48 * 3).toShort(), (62 * 3).toShort(), (80 * 3).toShort(), (104 * 3).toShort(), (136 * 3).toShort(), (180 * 3).toShort(), (192 * 3).toShort()),
                        shortArrayOf(4, 4, 4, 6, 8, 10, 12, 14, 18, 24, 32, 44, 12)),

                bandInfoStruct(
                        shortArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                        shortArrayOf(6, 6, 6, 6, 6, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 38, 46, 52, 60, 68, 58, 54),
                        shortArrayOf(0, (4 * 3).toShort(), (8 * 3).toShort(), (12 * 3).toShort(), (18 * 3).toShort(), (26 * 3).toShort(), (36 * 3).toShort(), (48 * 3).toShort(), (62 * 3).toShort(), (80 * 3).toShort(), (104 * 3).toShort(), (134 * 3).toShort(), (174 * 3).toShort(), (192 * 3).toShort()),
                        shortArrayOf(4, 4, 4, 6, 8, 10, 12, 14, 18, 24, 30, 40, 18)),
                /* MPEG 2.5 */
                bandInfoStruct(
                        shortArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                        shortArrayOf(6, 6, 6, 6, 6, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 38, 46, 52, 60, 68, 58, 54),
                        shortArrayOf(0, 12, 24, 36, 54, 78, 108, 144, 186, 240, 312, 402, 522, 576),
                        shortArrayOf(4, 4, 4, 6, 8, 10, 12, 14, 18, 24, 30, 40, 18)), bandInfoStruct(
                shortArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                shortArrayOf(6, 6, 6, 6, 6, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 38, 46, 52, 60, 68, 58, 54),
                shortArrayOf(0, 12, 24, 36, 54, 78, 108, 144, 186, 240, 312, 402, 522, 576),
                shortArrayOf(4, 4, 4, 6, 8, 10, 12, 14, 18, 24, 30, 40, 18)), bandInfoStruct(
                shortArrayOf(0, 12, 24, 36, 48, 60, 72, 88, 108, 132, 160, 192, 232, 280, 336, 400, 476, 566, 568, 570, 572, 574, 576),
                shortArrayOf(12, 12, 12, 12, 12, 12, 16, 20, 24, 28, 32, 40, 48, 56, 64, 76, 90, 2, 2, 2, 2, 2),
                shortArrayOf(0, 24, 48, 72, 108, 156, 216, 288, 372, 480, 486, 492, 498, 576),
                shortArrayOf(8, 8, 8, 12, 16, 20, 24, 28, 36, 2, 2, 2, 26)))

        private val Ci = doubleArrayOf(-0.6, -0.535, -0.33, -0.185, -0.095, -0.041, -0.0142, -0.0037)
        private val len = intArrayOf(36, 36, 12, 36)

        private val slen = arrayOf(intArrayOf(0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4), intArrayOf(0, 1, 2, 3, 0, 1, 2, 3, 1, 2, 3, 1, 2, 3, 2, 3))

        private val stab = arrayOf(arrayOf(intArrayOf(6, 5, 5, 5), intArrayOf(6, 5, 7, 3), intArrayOf(11, 10, 0, 0), intArrayOf(7, 7, 7, 0), intArrayOf(6, 6, 6, 3), intArrayOf(8, 8, 5, 0)), arrayOf(intArrayOf(9, 9, 9, 9), intArrayOf(9, 9, 12, 6), intArrayOf(18, 18, 0, 0), intArrayOf(12, 12, 12, 0), intArrayOf(12, 9, 9, 6), intArrayOf(15, 12, 9, 0)), arrayOf(intArrayOf(6, 9, 9, 9), intArrayOf(6, 9, 12, 6), intArrayOf(15, 18, 0, 0), intArrayOf(6, 15, 12, 0), intArrayOf(6, 12, 9, 6), intArrayOf(6, 18, 9, 0)))

        private val pretab1 = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 0) /* char enough ? */
        private val pretab2 = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

}
