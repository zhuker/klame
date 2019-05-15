/*
 *	MP3 huffman table selecting and bit counting
 *
 *	Copyright (c) 1999-2005 Takehiro TOMINAGA
 *	Copyright (c) 2002-2005 Gabriel Bouvigne
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: Takehiro.java,v 1.26 2011/05/24 20:48:06 kenchis Exp $ */

package mp3

import jdk.Math
import jdk.System
import jdk.assert
import jdk.Arrays


class Takehiro {

    internal lateinit var qupvt: QuantizePVT

    val subdv_table = arrayOf(intArrayOf(0, 0), /* 0 bands */
            intArrayOf(0, 0), /* 1 bands */
            intArrayOf(0, 0), /* 2 bands */
            intArrayOf(0, 0), /* 3 bands */
            intArrayOf(0, 0), /* 4 bands */
            intArrayOf(0, 1), /* 5 bands */
            intArrayOf(1, 1), /* 6 bands */
            intArrayOf(1, 1), /* 7 bands */
            intArrayOf(1, 2), /* 8 bands */
            intArrayOf(2, 2), /* 9 bands */
            intArrayOf(2, 3), /* 10 bands */
            intArrayOf(2, 3), /* 11 bands */
            intArrayOf(3, 4), /* 12 bands */
            intArrayOf(3, 4), /* 13 bands */
            intArrayOf(3, 4), /* 14 bands */
            intArrayOf(4, 5), /* 15 bands */
            intArrayOf(4, 5), /* 16 bands */
            intArrayOf(4, 6), /* 17 bands */
            intArrayOf(5, 6), /* 18 bands */
            intArrayOf(5, 6), /* 19 bands */
            intArrayOf(5, 7), /* 20 bands */
            intArrayOf(6, 7), /* 21 bands */
            intArrayOf(6, 7))/* 22 bands */

    fun setModules(qupvt: QuantizePVT) {
        this.qupvt = qupvt
    }

    internal class Bits(var bits: Int)

    /**
     * nonlinear quantization of xr More accurate formula than the ISO formula.
     * Takes into account the fact that we are quantizing xr . ix, but we want
     * ix^4/3 to be as close as possible to x^4/3. (taking the nearest int would
     * mean ix is as close as possible to xr, which is different.)
     *
     * From Segher Boessenkool <segher></segher>@eastsite.nl> 11/1999
     *
     * 09/2000: ASM code removed in favor of IEEE754 hack by Takehiro Tominaga.
     * If you need the ASM code, check CVS circa Aug 2000.
     *
     * 01/2004: Optimizations by Gabriel Bouvigne
     */
    private fun quantize_lines_xrpow_01(l: Int, istep: Float, xr: FloatArray,
                                         xrPos: Int, ix: IntArray, ixPos: Int) {
        var l = l
        var xrPos = xrPos
        var ixPos = ixPos
        val compareval0 = (1.0f - 0.4054f) / istep

        assert(l > 0)
        l = l shr 1
        while (l-- != 0) {
            ix[ixPos++] = if (compareval0 > xr[xrPos++]) 0 else 1
            ix[ixPos++] = if (compareval0 > xr[xrPos++]) 0 else 1
        }
    }

    /**
     * XRPOW_FTOI is a macro to convert floats to ints.<BR></BR>
     * if XRPOW_FTOI(x) = nearest_int(x), then QUANTFAC(x)=adj43asm[x]<BR></BR>
     * ROUNDFAC= -0.0946<BR></BR>
     *
     * if XRPOW_FTOI(x) = floor(x), then QUANTFAC(x)=asj43[x]<BR></BR>
     * ROUNDFAC=0.4054<BR></BR>
     *
     * Note: using floor() or (int) is extremely slow. On machines where the
     * TAKEHIRO_IEEE754_HACK code above does not work, it is worthwile to write
     * some ASM for XRPOW_FTOI().
     */
    private fun quantize_lines_xrpow(l: Int, istep: Float, xr: FloatArray,
                                      xrPos: Int, ix: IntArray, ixPos: Int) {
        var l = l
        var xrPos = xrPos
        var ixPos = ixPos
        assert(l > 0)

        l = l shr 1
        val remaining = l % 2
        l = l shr 1
        while (l-- != 0) {
            var x0: Float
            var x1: Float
            var x2: Float
            var x3: Float
            val rx0: Int
            val rx1: Int
            val rx2: Int
            val rx3: Int

            x0 = xr[xrPos++] * istep
            x1 = xr[xrPos++] * istep
            rx0 = x0.toInt()
            x2 = xr[xrPos++] * istep
            rx1 = x1.toInt()
            x3 = xr[xrPos++] * istep
            rx2 = x2.toInt()
            x0 += qupvt.adj43[rx0]
            rx3 = x3.toInt()
            x1 += qupvt.adj43[rx1]
            ix[ixPos++] = x0.toInt()
            x2 += qupvt.adj43[rx2]
            ix[ixPos++] = x1.toInt()
            x3 += qupvt.adj43[rx3]
            ix[ixPos++] = x2.toInt()
            ix[ixPos++] = x3.toInt()
        }
        if (remaining != 0) {
            var x0: Float
            var x1: Float
            val rx0: Int
            val rx1: Int

            x0 = xr[xrPos++] * istep
            x1 = xr[xrPos++] * istep
            rx0 = x0.toInt()
            rx1 = x1.toInt()
            x0 += qupvt.adj43[rx0]
            x1 += qupvt.adj43[rx1]
            ix[ixPos++] = x0.toInt()
            ix[ixPos++] = x1.toInt()
        }
    }

    /**
     * Quantization function This function will select which lines to quantize
     * and call the proper quantization function
     */
    private fun quantize_xrpow(xp: FloatArray, pi: IntArray, istep: Float,
                               codInfo: GrInfo, prevNoise: CalcNoiseData?) {
        /* quantize on xr^(3/4) instead of xr */
        var sfb: Int
        val sfbmax: Int
        var j = 0
        val prev_data_use: Boolean
        var accumulate = 0
        var accumulate01 = 0

        var xpPos = 0

        var iDataPos = 0
        var acc_iData = pi
        var acc_iDataPos = 0
        var acc_xp = xp
        var acc_xpPos = 0

        /*
		 * Reusing previously computed data does not seems to work if global
		 * gain is changed. Finding why it behaves this way would allow to use a
		 * cache of previously computed values (let's 10 cached values per sfb)
		 * that would probably provide a noticeable speedup
		 */
        prev_data_use = prevNoise != null && codInfo.global_gain == prevNoise.global_gain

        if (codInfo.block_type == Encoder.SHORT_TYPE)
            sfbmax = 38
        else
            sfbmax = 21

        sfb = 0
        while (sfb <= sfbmax) {
            var step = -1

            if (prev_data_use || codInfo.block_type == Encoder.NORM_TYPE) {
                val i = if (codInfo.preflag != 0) qupvt.pretab[sfb] else 0
                step = (codInfo.global_gain
                        - ((codInfo.scalefac[sfb] + i) shl codInfo.scalefac_scale + 1)
                        - codInfo.subblock_gain[codInfo.window[sfb]] * 8)
            }
            assert(codInfo.width[sfb] >= 0)
            if (prev_data_use && prevNoise!!.step[sfb] == step) {
                /*
				 * do not recompute this part, but compute accumulated lines
				 */
                if (accumulate != 0) {
                    quantize_lines_xrpow(accumulate, istep, acc_xp, acc_xpPos,
                            acc_iData, acc_iDataPos)
                    accumulate = 0
                }
                if (accumulate01 != 0) {
                    quantize_lines_xrpow_01(accumulate01, istep, acc_xp,
                            acc_xpPos, acc_iData, acc_iDataPos)
                    accumulate01 = 0
                }
            } else { /* should compute this part */
                var l = codInfo.width[sfb]

                if (j + codInfo.width[sfb] > codInfo.max_nonzero_coeff) {
                    /* do not compute upper zero part */
                    val usefullsize: Int
                    usefullsize = codInfo.max_nonzero_coeff - j + 1
                    Arrays.fill(pi, codInfo.max_nonzero_coeff, 576, 0)
                    l = usefullsize

                    if (l < 0) {
                        l = 0
                    }

                    /* no need to compute higher sfb values */
                    sfb = sfbmax + 1
                }

                /* accumulate lines to quantize */
                if (0 == accumulate && 0 == accumulate01) {
                    acc_iData = pi
                    acc_iDataPos = iDataPos
                    acc_xp = xp
                    acc_xpPos = xpPos
                }
                if (prevNoise != null && prevNoise.sfb_count1 > 0
                        && sfb >= prevNoise.sfb_count1
                        && prevNoise.step[sfb] > 0
                        && step >= prevNoise.step[sfb]) {

                    if (accumulate != 0) {
                        quantize_lines_xrpow(accumulate, istep, acc_xp,
                                acc_xpPos, acc_iData, acc_iDataPos)
                        accumulate = 0
                        acc_iData = pi
                        acc_iDataPos = iDataPos
                        acc_xp = xp
                        acc_xpPos = xpPos
                    }
                    accumulate01 += l
                } else {
                    if (accumulate01 != 0) {
                        quantize_lines_xrpow_01(accumulate01, istep, acc_xp,
                                acc_xpPos, acc_iData, acc_iDataPos)
                        accumulate01 = 0
                        acc_iData = pi
                        acc_iDataPos = iDataPos
                        acc_xp = xp
                        acc_xpPos = xpPos
                    }
                    accumulate += l
                }

                if (l <= 0) {
                    /*
					 * rh: 20040215 may happen due to "prev_data_use"
					 * optimization
					 */
                    if (accumulate01 != 0) {
                        quantize_lines_xrpow_01(accumulate01, istep, acc_xp,
                                acc_xpPos, acc_iData, acc_iDataPos)
                        accumulate01 = 0
                    }
                    if (accumulate != 0) {
                        quantize_lines_xrpow(accumulate, istep, acc_xp,
                                acc_xpPos, acc_iData, acc_iDataPos)
                        accumulate = 0
                    }

                    break /* ends for-loop */
                }
            }
            if (sfb <= sfbmax) {
                iDataPos += codInfo.width[sfb]
                xpPos += codInfo.width[sfb]
                j += codInfo.width[sfb]
            }
            sfb++
        }
        if (accumulate != 0) { /* last data part */
            quantize_lines_xrpow(accumulate, istep, acc_xp, acc_xpPos,
                    acc_iData, acc_iDataPos)
            accumulate = 0
        }
        if (accumulate01 != 0) { /* last data part */
            quantize_lines_xrpow_01(accumulate01, istep, acc_xp, acc_xpPos,
                    acc_iData, acc_iDataPos)
            accumulate01 = 0
        }

    }

    /**
     * ix_max
     */
    private fun ix_max(ix: IntArray, ixPos: Int, endPos: Int): Int {
        var ixPos = ixPos
        var max1 = 0
        var max2 = 0

        do {
            val x1 = ix[ixPos++]
            val x2 = ix[ixPos++]
            if (max1 < x1)
                max1 = x1

            if (max2 < x2)
                max2 = x2
        } while (ixPos < endPos)
        if (max1 < max2)
            max1 = max2
        return max1
    }

    private fun count_bit_ESC(ix: IntArray, ixPos: Int, end: Int, t1: Int,
                              t2: Int, s: Bits): Int {
        var ixPos = ixPos
        var t1 = t1
        /* ESC-table is used */
        val linbits = Tables.ht[t1].xlen * 65536 + Tables.ht[t2].xlen
        var sum = 0
        val sum2: Int

        do {
            var x = ix[ixPos++]
            var y = ix[ixPos++]

            if (x != 0) {
                if (x > 14) {
                    x = 15
                    sum += linbits
                }
                x *= 16
            }

            if (y != 0) {
                if (y > 14) {
                    y = 15
                    sum += linbits
                }
                x += y
            }

            sum += Tables.largetbl[x]
        } while (ixPos < end)

        sum2 = sum and 0xffff
        sum = sum shr 16

        if (sum > sum2) {
            sum = sum2
            t1 = t2
        }

        s.bits += sum
        return t1
    }

    private fun count_bit_noESC(ix: IntArray, ixPos: Int, end: Int, s: Bits): Int {
        var ixPos = ixPos
        /* No ESC-words */
        var sum1 = 0
        val hlen1 = Tables.ht[1].hlen

        do {
            val x = ix[ixPos + 0] * 2 + ix[ixPos + 1]
            ixPos += 2
            sum1 += hlen1[x]
        } while (ixPos < end)

        s.bits += sum1
        return 1
    }

    private fun count_bit_noESC_from2(ix: IntArray, ixPos: Int, end: Int,
                                      t1: Int, s: Bits): Int {
        var ixPos = ixPos
        var t1 = t1
        /* No ESC-words */
        var sum = 0
        val sum2: Int
        val xlen = Tables.ht[t1].xlen
        val hlen: IntArray
        if (t1 == 2)
            hlen = Tables.table23
        else
            hlen = Tables.table56

        do {
            val x = ix[ixPos + 0] * xlen + ix[ixPos + 1]
            ixPos += 2
            sum += hlen[x]
        } while (ixPos < end)

        sum2 = sum and 0xffff
        sum = sum shr 16

        if (sum > sum2) {
            sum = sum2
            t1++
        }

        s.bits += sum
        return t1
    }

    private fun count_bit_noESC_from3(ix: IntArray, ixPos: Int, end: Int,
                                      t1: Int, s: Bits): Int {
        var ixPos = ixPos
        /* No ESC-words */
        var sum1 = 0
        var sum2 = 0
        var sum3 = 0
        val xlen = Tables.ht[t1].xlen
        val hlen1 = Tables.ht[t1].hlen
        val hlen2 = Tables.ht[t1 + 1].hlen
        val hlen3 = Tables.ht[t1 + 2].hlen

        do {
            val x = ix[ixPos + 0] * xlen + ix[ixPos + 1]
            ixPos += 2
            sum1 += hlen1[x]
            sum2 += hlen2[x]
            sum3 += hlen3[x]
        } while (ixPos < end)

        var t = t1
        if (sum1 > sum2) {
            sum1 = sum2
            t++
        }
        if (sum1 > sum3) {
            sum1 = sum3
            t = t1 + 2
        }
        s.bits += sum1

        return t
    }

    /**
     * Choose the Huffman table that will encode ix[begin end] with the fewest
     * bits.
     *
     * Note: This code contains knowledge about the sizes and characteristics of
     * the Huffman tables as defined in the IS (Table B.7), and will not work
     * with any arbitrary tables.
     */
    private fun choose_table(ix: IntArray, ixPos: Int, endPos: Int,
                             s: Bits): Int {
        var max = ix_max(ix, ixPos, endPos)

        when (max) {
            0 -> return max

            1 -> return count_bit_noESC(ix, ixPos, endPos, s)

            2, 3 -> return count_bit_noESC_from2(ix, ixPos, endPos,
                    huf_tbl_noESC[max - 1], s)

            4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 -> return count_bit_noESC_from3(ix, ixPos, endPos,
                    huf_tbl_noESC[max - 1], s)

            else -> {
                /* try tables with linbits */
                if (max > QuantizePVT.IXMAX_VAL) {
                    s.bits = QuantizePVT.LARGE_BITS
                    return -1
                }
                max -= 15
                var choice2: Int
                choice2 = 24
                while (choice2 < 32) {
                    if (Tables.ht[choice2].linmax >= max) {
                        break
                    }
                    choice2++
                }
                var choice: Int
                choice = choice2 - 8
                while (choice < 24) {
                    if (Tables.ht[choice].linmax >= max) {
                        break
                    }
                    choice++
                }
                return count_bit_ESC(ix, ixPos, endPos, choice, choice2, s)
            }
        }
    }

    /**
     * count_bit
     */
    fun noquant_count_bits(gfc: LameInternalFlags,
                           gi: GrInfo, prev_noise: CalcNoiseData?): Int {
        val ix = gi.l3_enc

        var i = Math.min(576, gi.max_nonzero_coeff + 2 shr 1 shl 1)

        if (prev_noise != null)
            prev_noise.sfb_count1 = 0

        /* Determine count1 region */
        while (i > 1) {
            if (ix[i - 1] or ix[i - 2] != 0)
                break
            i -= 2
        }
        gi.count1 = i

        /* Determines the number of bits to encode the quadruples. */
        var a1 = 0
        var a2 = 0
        while (i > 3) {
            val p: Int
            /* hack to check if all values <= 1 */
            if (ix[i - 1].toLong() or ix[i - 2].toLong() or ix[i - 3].toLong() or ix[i - 4].toLong() and 0xffffffffL > 1L)
                break

            p = ((ix[i - 4] * 2 + ix[i - 3]) * 2 + ix[i - 2]) * 2 + ix[i - 1]
            a1 += Tables.t32l[p]
            a2 += Tables.t33l[p]
            i -= 4
        }

        var bits = a1
        gi.count1table_select = 0
        if (a1 > a2) {
            bits = a2
            gi.count1table_select = 1
        }

        gi.count1bits = bits
        gi.big_values = i
        if (i == 0)
            return bits

        if (gi.block_type == Encoder.SHORT_TYPE) {
            a1 = 3 * gfc.scalefac_band.s[3]
            if (a1 > gi.big_values)
                a1 = gi.big_values
            a2 = gi.big_values

        } else if (gi.block_type == Encoder.NORM_TYPE) {
            assert(i <= 576) /* bv_scf has 576 entries (0..575) */
            gi.region0_count = gfc.bv_scf[i - 2]
            a1 = gi.region0_count
            gi.region1_count = gfc.bv_scf[i - 1]
            a2 = gi.region1_count

            assert(a1 + a2 + 2 < Encoder.SBPSY_l)
            a2 = gfc.scalefac_band.l[a1 + a2 + 2]
            a1 = gfc.scalefac_band.l[a1 + 1]
            if (a2 < i) {
                val bi = Bits(bits)
                gi.table_select[2] = choose_table(ix, a2, i, bi)
                bits = bi.bits
            }
        } else {
            gi.region0_count = 7
            /* gi.region1_count = SBPSY_l - 7 - 1; */
            gi.region1_count = Encoder.SBMAX_l - 1 - 7 - 1
            a1 = gfc.scalefac_band.l[7 + 1]
            a2 = i
            if (a1 > a2) {
                a1 = a2
            }
        }

        /* have to allow for the case when bigvalues < region0 < region1 */
        /* (and region0, region1 are ignored) */
        a1 = Math.min(a1, i)
        a2 = Math.min(a2, i)

        assert(a1 >= 0)
        assert(a2 >= 0)

        /* Count the number of bits necessary to code the bigvalues region. */
        if (0 < a1) {
            val bi = Bits(bits)
            gi.table_select[0] = choose_table(ix, 0, a1, bi)
            bits = bi.bits
        }
        if (a1 < a2) {
            val bi = Bits(bits)
            gi.table_select[1] = choose_table(ix, a1, a2, bi)
            bits = bi.bits
        }
        if (gfc.use_best_huffman == 2) {
            gi.part2_3_length = bits
            best_huffman_divide(gfc, gi)
            bits = gi.part2_3_length
        }

        if (prev_noise != null) {
            if (gi.block_type == Encoder.NORM_TYPE) {
                var sfb = 0
                while (gfc.scalefac_band.l[sfb] < gi.big_values) {
                    sfb++
                }
                prev_noise.sfb_count1 = sfb
            }
        }

        return bits
    }

    fun count_bits(gfc: LameInternalFlags, xr: FloatArray,
                   gi: GrInfo, prev_noise: CalcNoiseData?): Int {
        val ix = gi.l3_enc

        /* since quantize_xrpow uses table lookup, we need to check this first: */
        val w = QuantizePVT.IXMAX_VAL / qupvt.IPOW20(gi.global_gain)

        if (gi.xrpow_max > w)
            return QuantizePVT.LARGE_BITS

        quantize_xrpow(xr, ix, qupvt.IPOW20(gi.global_gain), gi, prev_noise)

        if (gfc.substep_shaping and 2 != 0) {
            var j = 0
            /* 0.634521682242439 = 0.5946*2**(.5*0.1875) */
            val gain = gi.global_gain + gi.scalefac_scale
            val roundfac = 0.634521682242439f / qupvt.IPOW20(gain)
            for (sfb in 0 until gi.sfbmax) {
                val width = gi.width[sfb]
                assert(width >= 0)
                if (0 == gfc.pseudohalf[sfb]) {
                    j += width
                } else {
                    var k: Int
                    k = j
                    j += width
                    while (k < j) {
                        ix[k] = if (xr[k] >= roundfac) ix[k] else 0
                        ++k
                    }
                }
            }
        }
        return noquant_count_bits(gfc, gi, prev_noise)
    }

    /**
     * re-calculate the best scalefac_compress using scfsi the saved bits are
     * kept in the bit reservoir.
     */
    private fun recalc_divide_init(gfc: LameInternalFlags,
                                   cod_info: GrInfo, ix: IntArray, r01_bits: IntArray,
                                   r01_div: IntArray, r0_tbl: IntArray, r1_tbl: IntArray) {
        val bigv = cod_info.big_values

        for (r0 in 0..7 + 15) {
            r01_bits[r0] = QuantizePVT.LARGE_BITS
        }

        for (r0 in 0..15) {
            val a1 = gfc.scalefac_band.l[r0 + 1]
            if (a1 >= bigv)
                break
            var r0bits = 0
            var bi = Bits(r0bits)
            val r0t = choose_table(ix, 0, a1, bi)
            r0bits = bi.bits

            for (r1 in 0..7) {
                val a2 = gfc.scalefac_band.l[r0 + r1 + 2]
                if (a2 >= bigv)
                    break

                var bits = r0bits
                bi = Bits(bits)
                val r1t = choose_table(ix, a1, a2, bi)
                bits = bi.bits
                if (r01_bits[r0 + r1] > bits) {
                    r01_bits[r0 + r1] = bits
                    r01_div[r0 + r1] = r0
                    r0_tbl[r0 + r1] = r0t
                    r1_tbl[r0 + r1] = r1t
                }
            }
        }
    }

    private fun recalc_divide_sub(gfc: LameInternalFlags,
                                  cod_info2: GrInfo, gi: GrInfo, ix: IntArray,
                                  r01_bits: IntArray, r01_div: IntArray, r0_tbl: IntArray,
                                  r1_tbl: IntArray) {
        val bigv = cod_info2.big_values

        for (r2 in 2 until Encoder.SBMAX_l + 1) {
            val a2 = gfc.scalefac_band.l[r2]
            if (a2 >= bigv)
                break

            var bits = r01_bits[r2 - 2] + cod_info2.count1bits
            if (gi.part2_3_length <= bits)
                break

            val bi = Bits(bits)
            val r2t = choose_table(ix, a2, bigv, bi)
            bits = bi.bits
            if (gi.part2_3_length <= bits)
                continue

            gi.assign(cod_info2)
            gi.part2_3_length = bits
            gi.region0_count = r01_div[r2 - 2]
            gi.region1_count = r2 - 2 - r01_div[r2 - 2]
            gi.table_select[0] = r0_tbl[r2 - 2]
            gi.table_select[1] = r1_tbl[r2 - 2]
            gi.table_select[2] = r2t
        }
    }

    fun best_huffman_divide(gfc: LameInternalFlags,
                            gi: GrInfo) {
        val cod_info2 = GrInfo()
        val ix = gi.l3_enc

        val r01_bits = IntArray(7 + 15 + 1)
        val r01_div = IntArray(7 + 15 + 1)
        val r0_tbl = IntArray(7 + 15 + 1)
        val r1_tbl = IntArray(7 + 15 + 1)

        /* SHORT BLOCK stuff fails for MPEG2 */
        if (gi.block_type == Encoder.SHORT_TYPE && gfc.mode_gr == 1)
            return

        cod_info2.assign(gi)
        if (gi.block_type == Encoder.NORM_TYPE) {
            recalc_divide_init(gfc, gi, ix, r01_bits, r01_div, r0_tbl, r1_tbl)
            recalc_divide_sub(gfc, cod_info2, gi, ix, r01_bits, r01_div,
                    r0_tbl, r1_tbl)
        }

        var i = cod_info2.big_values
        if (i == 0 || ix[i - 2] or ix[i - 1] > 1)
            return

        i = gi.count1 + 2
        if (i > 576)
            return

        /* Determines the number of bits to encode the quadruples. */
        cod_info2.assign(gi)
        cod_info2.count1 = i
        var a1 = 0
        var a2 = 0

        assert(i <= 576)

        while (i > cod_info2.big_values) {
            val p = ((ix[i - 4] * 2 + ix[i - 3]) * 2 + ix[i - 2]) * 2 + ix[i - 1]
            a1 += Tables.t32l[p]
            a2 += Tables.t33l[p]
            i -= 4
        }
        cod_info2.big_values = i

        cod_info2.count1table_select = 0
        if (a1 > a2) {
            a1 = a2
            cod_info2.count1table_select = 1
        }

        cod_info2.count1bits = a1

        if (cod_info2.block_type == Encoder.NORM_TYPE)
            recalc_divide_sub(gfc, cod_info2, gi, ix, r01_bits, r01_div,
                    r0_tbl, r1_tbl)
        else {
            /* Count the number of bits necessary to code the bigvalues region. */
            cod_info2.part2_3_length = a1
            a1 = gfc.scalefac_band.l[7 + 1]
            if (a1 > i) {
                a1 = i
            }
            if (a1 > 0) {
                val bi = Bits(cod_info2.part2_3_length)
                cod_info2.table_select[0] = choose_table(ix, 0, a1, bi)
                cod_info2.part2_3_length = bi.bits
            }
            if (i > a1) {
                val bi = Bits(cod_info2.part2_3_length)
                cod_info2.table_select[1] = choose_table(ix, a1, i, bi)
                cod_info2.part2_3_length = bi.bits
            }
            if (gi.part2_3_length > cod_info2.part2_3_length)
                gi.assign(cod_info2)
        }
    }

    private fun scfsi_calc(ch: Int, l3_side: IIISideInfo) {
        var sfb: Int
        val gi = l3_side.tt[1][ch]
        val g0 = l3_side.tt[0][ch]

        for (i in 0 until Tables.scfsi_band.size - 1) {
            sfb = Tables.scfsi_band[i]
            while (sfb < Tables.scfsi_band[i + 1]) {
                if (g0.scalefac[sfb] != gi.scalefac[sfb] && gi.scalefac[sfb] >= 0)
                    break
                sfb++
            }
            if (sfb == Tables.scfsi_band[i + 1]) {
                sfb = Tables.scfsi_band[i]
                while (sfb < Tables.scfsi_band[i + 1]) {
                    gi.scalefac[sfb] = -1
                    sfb++
                }
                l3_side.scfsi[ch][i] = 1
            }
        }

        var s1 = 0
        var c1 = 0
        sfb = 0
        while (sfb < 11) {
            if (gi.scalefac[sfb] == -1) {
                sfb++
                continue
            }
            c1++
            if (s1 < gi.scalefac[sfb])
                s1 = gi.scalefac[sfb]
            sfb++
        }

        var s2 = 0
        var c2 = 0
        while (sfb < Encoder.SBPSY_l) {
            if (gi.scalefac[sfb] == -1) {
                sfb++
                continue
            }
            c2++
            if (s2 < gi.scalefac[sfb])
                s2 = gi.scalefac[sfb]
            sfb++
        }

        for (i in 0..15) {
            if (s1 < slen1_n[i] && s2 < slen2_n[i]) {
                val c = slen1_tab[i] * c1 + slen2_tab[i] * c2
                if (gi.part2_length > c) {
                    gi.part2_length = c
                    gi.scalefac_compress = i
                }
            }
        }
    }

    /**
     * Find the optimal way to store the scalefactors. Only call this routine
     * after final scalefactors have been chosen and the channel/granule will
     * not be re-encoded.
     */
    fun best_scalefac_store(gfc: LameInternalFlags, gr: Int,
                            ch: Int, l3_side: IIISideInfo) {
        /* use scalefac_scale if we can */
        val gi = l3_side.tt[gr][ch]
        var sfb: Int
        var i: Int
        var j: Int
        var l: Int
        var recalc = 0

        /*
		 * remove scalefacs from bands with ix=0. This idea comes from the AAC
		 * ISO docs. added mt 3/00
		 */
        /* check if l3_enc=0 */
        j = 0
        sfb = 0
        while (sfb < gi.sfbmax) {
            val width = gi.width[sfb]
            assert(width >= 0)
            j += width
            l = -width
            while (l < 0) {
                if (gi.l3_enc[l + j] != 0)
                    break
                l++
            }
            if (l == 0) {
                recalc = -2
                gi.scalefac[sfb] = recalc
            } /* anything goes. */
            sfb++
            /*
			 * only best_scalefac_store and calc_scfsi know--and only they
			 * should know--about the magic number -2.
			 */
        }

        if (0 == gi.scalefac_scale && 0 == gi.preflag) {
            var s = 0
            sfb = 0
            while (sfb < gi.sfbmax) {
                if (gi.scalefac[sfb] > 0)
                    s = s or gi.scalefac[sfb]
                sfb++
            }

            if (0 == s and 1 && s != 0) {
                sfb = 0
                while (sfb < gi.sfbmax) {
                    if (gi.scalefac[sfb] > 0)
                        gi.scalefac[sfb] = gi.scalefac[sfb] shr 1
                    sfb++
                }

                recalc = 1
                gi.scalefac_scale = recalc
            }
        }

        if (0 == gi.preflag && gi.block_type != Encoder.SHORT_TYPE
                && gfc.mode_gr == 2) {
            sfb = 11
            while (sfb < Encoder.SBPSY_l) {
                if (gi.scalefac[sfb] < qupvt.pretab[sfb] && gi.scalefac[sfb] != -2)
                    break
                sfb++
            }
            if (sfb == Encoder.SBPSY_l) {
                sfb = 11
                while (sfb < Encoder.SBPSY_l) {
                    if (gi.scalefac[sfb] > 0)
                        gi.scalefac[sfb] -= qupvt.pretab[sfb]
                    sfb++
                }

                recalc = 1
                gi.preflag = recalc
            }
        }

        i = 0
        while (i < 4) {
            l3_side.scfsi[ch][i] = 0
            i++
        }

        if (gfc.mode_gr == 2 && gr == 1
                && l3_side.tt[0][ch].block_type != Encoder.SHORT_TYPE
                && l3_side.tt[1][ch].block_type != Encoder.SHORT_TYPE) {
            scfsi_calc(ch, l3_side)
            recalc = 0
        }
        sfb = 0
        while (sfb < gi.sfbmax) {
            if (gi.scalefac[sfb] == -2) {
                gi.scalefac[sfb] = 0 /* if anything goes, then 0 is a good choice */
            }
            sfb++
        }
        if (recalc != 0) {
            if (gfc.mode_gr == 2) {
                scale_bitcount(gi)
            } else {
                scale_bitcount_lsf(gfc, gi)
            }
        }
    }

    private fun all_scalefactors_not_negative(scalefac: IntArray, n: Int): Boolean {
        for (i in 0 until n) {
            if (scalefac[i] < 0)
                return false
        }
        return true
    }

    /**
     * Also calculates the number of bits necessary to code the scalefactors.
     */
    fun scale_bitcount(cod_info: GrInfo): Boolean {
        var k: Int
        var sfb: Int
        var max_slen1 = 0
        var max_slen2 = 0

        /* maximum values */
        var tab: IntArray
        val scalefac = cod_info.scalefac

        assert(all_scalefactors_not_negative(scalefac, cod_info.sfbmax))

        if (cod_info.block_type == Encoder.SHORT_TYPE) {
            tab = scale_short
            if (cod_info.mixed_block_flag != 0)
                tab = scale_mixed
        } else { /* block_type == 1,2,or 3 */
            tab = scale_long
            if (0 == cod_info.preflag) {
                sfb = 11
                while (sfb < Encoder.SBPSY_l) {
                    if (scalefac[sfb] < qupvt.pretab[sfb])
                        break
                    sfb++
                }

                if (sfb == Encoder.SBPSY_l) {
                    cod_info.preflag = 1
                    sfb = 11
                    while (sfb < Encoder.SBPSY_l) {
                        scalefac[sfb] -= qupvt.pretab[sfb]
                        sfb++
                    }
                }
            }
        }

        sfb = 0
        while (sfb < cod_info.sfbdivide) {
            if (max_slen1 < scalefac[sfb])
                max_slen1 = scalefac[sfb]
            sfb++
        }

        while (sfb < cod_info.sfbmax) {
            if (max_slen2 < scalefac[sfb])
                max_slen2 = scalefac[sfb]
            sfb++
        }

        /*
		 * from Takehiro TOMINAGA <tominaga@isoternet.org> 10/99 loop over *all*
		 * posible values of scalefac_compress to find the one which uses the
		 * smallest number of bits. ISO would stop at first valid index
		 */
        cod_info.part2_length = QuantizePVT.LARGE_BITS
        k = 0
        while (k < 16) {
            if (max_slen1 < slen1_n[k] && max_slen2 < slen2_n[k]
                    && cod_info.part2_length > tab[k]) {
                cod_info.part2_length = tab[k]
                cod_info.scalefac_compress = k
            }
            k++
        }
        return cod_info.part2_length == QuantizePVT.LARGE_BITS
    }

    /**
     * Also counts the number of bits to encode the scalefacs but for MPEG 2
     * Lower sampling frequencies (24, 22.05 and 16 kHz.)
     *
     * This is reverse-engineered from section 2.4.3.2 of the MPEG2 IS,
     * "Audio Decoding Layer III"
     */
    fun scale_bitcount_lsf(gfc: LameInternalFlags,
                           cod_info: GrInfo): Boolean {
        val table_number: Int
        val row_in_table: Int
        var partition: Int
        var nr_sfb: Int
        var window: Int
        var over: Boolean
        var i: Int
        var sfb: Int
        val max_sfac = IntArray(4)
        val partition_table: IntArray
        val scalefac = cod_info.scalefac

        /*
		 * Set partition table. Note that should try to use table one, but do
		 * not yet...
		 */
        if (cod_info.preflag != 0)
            table_number = 2
        else
            table_number = 0

        i = 0
        while (i < 4) {
            max_sfac[i] = 0
            i++
        }

        if (cod_info.block_type == Encoder.SHORT_TYPE) {
            row_in_table = 1
            partition_table = qupvt.nr_of_sfb_block[table_number][row_in_table]
            sfb = 0
            partition = 0
            while (partition < 4) {
                nr_sfb = partition_table[partition] / 3
                i = 0
                while (i < nr_sfb) {
//                    {
                    window = 0
                    while (window < 3) {
                        if (scalefac[sfb * 3 + window] > max_sfac[partition])
                            max_sfac[partition] = scalefac[sfb * 3 + window]
                        window++
                    }
//                    }
                    i++
                    sfb++
                }
                partition++
            }
        } else {
            row_in_table = 0
            partition_table = qupvt.nr_of_sfb_block[table_number][row_in_table]
            sfb = 0
            partition = 0
            while (partition < 4) {
                nr_sfb = partition_table[partition]
                i = 0
                while (i < nr_sfb) {
                    if (scalefac[sfb] > max_sfac[partition])
                        max_sfac[partition] = scalefac[sfb]
                    i++
                    sfb++
                }
                partition++
            }
        }

        over = false
        partition = 0
        while (partition < 4) {
            if (max_sfac[partition] > max_range_sfac_tab[table_number][partition])
                over = true
            partition++
        }
        if (!over) {

            val slen1: Int
            val slen2: Int
            val slen3: Int
            val slen4: Int

            cod_info.sfb_partition_table = qupvt.nr_of_sfb_block[table_number][row_in_table]
            partition = 0
            while (partition < 4) {
                cod_info.slen[partition] = log2tab[max_sfac[partition]]
                partition++
            }

            /* set scalefac_compress */
            slen1 = cod_info.slen[0]
            slen2 = cod_info.slen[1]
            slen3 = cod_info.slen[2]
            slen4 = cod_info.slen[3]

            when (table_number) {
                0 -> cod_info.scalefac_compress = ((slen1 * 5 + slen2 shl 4)
                        + (slen3 shl 2) + slen4)

                1 -> cod_info.scalefac_compress = (400 + (slen1 * 5 + slen2 shl 2)
                        + slen3)

                2 -> cod_info.scalefac_compress = 500 + slen1 * 3 + slen2

                else -> System.err.printf("intensity stereo not implemented yet\n")
            }
        }
        if (!over) {
            val sfb_partition_table = cod_info.sfb_partition_table!!
            assert(sfb_partition_table != null)
            cod_info.part2_length = 0
            partition = 0
            while (partition < 4) {
                cod_info.part2_length += cod_info.slen[partition] * sfb_partition_table[partition]
                partition++
            }
        }
        return over
    }

    fun huffman_init(gfc: LameInternalFlags) {
        var i = 2
        while (i <= 576) {
            var scfb_anz = 0
            var bv_index: Int
            while (gfc.scalefac_band.l[++scfb_anz] < i)
            ;

            bv_index = subdv_table[scfb_anz][0] // .region0_count
            while (gfc.scalefac_band.l[bv_index + 1] > i)
                bv_index--

            if (bv_index < 0) {
                /*
				 * this is an indication that everything is going to be encoded
				 * as region0: bigvalues < region0 < region1 so lets set
				 * region0, region1 to some value larger than bigvalues
				 */
                bv_index = subdv_table[scfb_anz][0] // .region0_count
            }

            gfc.bv_scf[i - 2] = bv_index

            bv_index = subdv_table[scfb_anz][1] // .region1_count
            while (gfc.scalefac_band.l[bv_index + gfc.bv_scf[i - 2] + 2] > i)
                bv_index--

            if (bv_index < 0) {
                bv_index = subdv_table[scfb_anz][1] // .region1_count
            }

            gfc.bv_scf[i - 1] = bv_index
            i += 2
        }
    }

    companion object {

        /** */
        /* choose table */
        /** */

        private val huf_tbl_noESC = intArrayOf(1, 2, 5, 7, 7, 10, 10, 13, 13, 13, 13, 13, 13, 13, 13)

        private val slen1_n = intArrayOf(1, 1, 1, 1, 8, 2, 2, 2, 4, 4, 4, 8, 8, 8, 16, 16)
        private val slen2_n = intArrayOf(1, 2, 4, 8, 1, 2, 4, 8, 2, 4, 8, 2, 4, 8, 4, 8)
        val slen1_tab = intArrayOf(0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4)
        val slen2_tab = intArrayOf(0, 1, 2, 3, 0, 1, 2, 3, 1, 2, 3, 1, 2, 3, 2, 3)

        /**
         * number of bits used to encode scalefacs.
         *
         * 18*slen1_tab[i] + 18*slen2_tab[i]
         */
        private val scale_short = intArrayOf(0, 18, 36, 54, 54, 36, 54, 72, 54, 72, 90, 72, 90, 108, 108, 126)

        /**
         * number of bits used to encode scalefacs.
         *
         * 17*slen1_tab[i] + 18*slen2_tab[i]
         */
        private val scale_mixed = intArrayOf(0, 18, 36, 54, 51, 35, 53, 71, 52, 70, 88, 69, 87, 105, 104, 122)

        /**
         * number of bits used to encode scalefacs.
         *
         * 11*slen1_tab[i] + 10*slen2_tab[i]
         */
        private val scale_long = intArrayOf(0, 10, 20, 30, 33, 21, 31, 41, 32, 42, 52, 43, 53, 63, 64, 74)

        /**
         * table of largest scalefactor values for MPEG2
         */
        private val max_range_sfac_tab = arrayOf(intArrayOf(15, 15, 7, 7), intArrayOf(15, 15, 7, 0), intArrayOf(7, 3, 0, 0), intArrayOf(15, 31, 31, 0), intArrayOf(7, 7, 7, 0), intArrayOf(3, 3, 0, 0))

        /*
	 * Since no bands have been over-amplified, we can set scalefac_compress and
	 * slen[] for the formatter
	 */
        private val log2tab = intArrayOf(0, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4)
    }
}
