/*
 *	MP3 quantization
 *
 *	Copyright (c) 1999-2000 Mark Taylor
 *	Copyright (c) 2000-2007 Robert Hegemann
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

/* $Id: VBRQuantize.java,v 1.18 2011/08/27 18:57:12 kenchis Exp $ */

package mp3

import jdk.Arrays
import jdk.Math
import jdk.assert
import jdk.format

class VBRQuantize {

    internal lateinit var qupvt: QuantizePVT
    internal lateinit var tak: Takehiro

    fun setModules(qupvt: QuantizePVT, tk: Takehiro) {
        this.qupvt = qupvt
        this.tak = tk
    }

    class algo_t {
        internal var alloc: alloc_sf_f? = null
        var xr34orig: FloatArray? = null
        var gfc: LameInternalFlags? = null
        var cod_info: GrInfo? = null
        var mingain_l: Int = 0
        var mingain_s = IntArray(3)
    }

    internal interface alloc_sf_f {
        fun alloc(al: algo_t, x: IntArray, y: IntArray, z: Int)
    }

    private fun max_x34(xr34: FloatArray?, x34Pos: Int, bw: Int): Float {
        var x34Pos = x34Pos
        var xfsf = 0f
        var j = bw shr 1
        val remaining = j and 0x01

        j = j shr 1
        while (j > 0) {
            if (xfsf < xr34!![x34Pos + 0]) {
                xfsf = xr34[x34Pos + 0]
            }
            if (xfsf < xr34[x34Pos + 1]) {
                xfsf = xr34[x34Pos + 1]
            }
            if (xfsf < xr34[x34Pos + 2]) {
                xfsf = xr34[x34Pos + 2]
            }
            if (xfsf < xr34[x34Pos + 3]) {
                xfsf = xr34[x34Pos + 3]
            }
            x34Pos += 4
            --j
        }
        if (remaining != 0) {
            if (xfsf < xr34!![x34Pos + 0]) {
                xfsf = xr34[x34Pos + 0]
            }
            if (xfsf < xr34[x34Pos + 1]) {
                xfsf = xr34[x34Pos + 1]
            }
        }
        return xfsf
    }

    private fun findLowestScalefac(xr34: Float): Int {
        var sfOk = 255
        var sf = 128
        var delsf = 64
        for (i in 0..7) {
            val xfsf = qupvt.ipow20[sf] * xr34
            if (xfsf <= QuantizePVT.IXMAX_VAL) {
                sfOk = sf
                sf -= delsf
            } else {
                sf += delsf
            }
            delsf = delsf shr 1
        }
        return sfOk
    }

    private fun belowNoiseFloor(xr: FloatArray, xrPos: Int, l3xmin: Float,
                                bw: Int): Int {
        var sum = 0.0f
        var i = 0
        var j = bw
        while (j > 0) {
            val x = xr[xrPos + i]
            sum += x * x
            ++i
            --j
        }
        return if (l3xmin - sum >= -1E-20) 1 else 0
    }

    private fun k_34_4(x: DoubleArray, l3: IntArray, l3Pos: Int) {
        assert(x[0] <= QuantizePVT.IXMAX_VAL && x[1] <= QuantizePVT.IXMAX_VAL
                && x[2] <= QuantizePVT.IXMAX_VAL && x[3] <= QuantizePVT.IXMAX_VAL)
        l3[l3Pos + 0] = x[0].toInt()
        l3[l3Pos + 1] = x[1].toInt()
        l3[l3Pos + 2] = x[2].toInt()
        l3[l3Pos + 3] = x[3].toInt()
        x[0] += qupvt.adj43[l3[l3Pos + 0]].toDouble()
        x[1] += qupvt.adj43[l3[l3Pos + 1]].toDouble()
        x[2] += qupvt.adj43[l3[l3Pos + 2]].toDouble()
        x[3] += qupvt.adj43[l3[l3Pos + 3]].toDouble()
        l3[l3Pos + 0] = x[0].toInt()
        l3[l3Pos + 1] = x[1].toInt()
        l3[l3Pos + 2] = x[2].toInt()
        l3[l3Pos + 3] = x[3].toInt()
    }

    private fun k_34_2(x: DoubleArray, l3: IntArray, l3Pos: Int) {
        assert(x[0] <= QuantizePVT.IXMAX_VAL && x[1] <= QuantizePVT.IXMAX_VAL)
        l3[l3Pos + 0] = x[0].toInt()
        l3[l3Pos + 1] = x[1].toInt()
        x[0] += qupvt.adj43[l3[l3Pos + 0]].toDouble()
        x[1] += qupvt.adj43[l3[l3Pos + 1]].toDouble()
        l3[l3Pos + 0] = x[0].toInt()
        l3[l3Pos + 1] = x[1].toInt()
    }

    private fun calc_sfb_noise_x34(xr: FloatArray, xr34: FloatArray?,
                                   xrPos: Int, bw: Int, sf: Int): Float {
        var xrPos = xrPos
        val x = DoubleArray(4)
        val l3 = IntArray(4)
        val sfpow = qupvt.pow20[sf + QuantizePVT.Q_MAX2]
        val sfpow34 = qupvt.ipow20[sf]

        var xfsf = 0f
        var j = bw shr 1
        val remaining = j and 0x01

        j = j shr 1
        while (j > 0) {
            x[0] = (sfpow34 * xr34!![xrPos + 0]).toDouble()
            x[1] = (sfpow34 * xr34[xrPos + 1]).toDouble()
            x[2] = (sfpow34 * xr34[xrPos + 2]).toDouble()
            x[3] = (sfpow34 * xr34[xrPos + 3]).toDouble()

            k_34_4(x, l3, 0)

            x[0] = (Math.abs(xr[xrPos + 0]) - sfpow * qupvt.pow43[l3[0]]).toDouble()
            x[1] = (Math.abs(xr[xrPos + 1]) - sfpow * qupvt.pow43[l3[1]]).toDouble()
            x[2] = (Math.abs(xr[xrPos + 2]) - sfpow * qupvt.pow43[l3[2]]).toDouble()
            x[3] = (Math.abs(xr[xrPos + 3]) - sfpow * qupvt.pow43[l3[3]]).toDouble()
            xfsf += (x[0] * x[0] + x[1] * x[1] + (x[2] * x[2] + x[3] * x[3])).toFloat()

            xrPos += 4
            --j
        }
        if (remaining != 0) {
            x[0] = (sfpow34 * xr34!![xrPos + 0]).toDouble()
            x[1] = (sfpow34 * xr34[xrPos + 1]).toDouble()

            k_34_2(x, l3, 0)

            x[0] = (Math.abs(xr[xrPos + 0]) - sfpow * qupvt.pow43[l3[0]]).toDouble()
            x[1] = (Math.abs(xr[xrPos + 1]) - sfpow * qupvt.pow43[l3[1]]).toDouble()
            xfsf += (x[0] * x[0] + x[1] * x[1]).toFloat()
        }
        return xfsf
    }

    protected class CalcNoiseCache {
        internal var valid: Int = 0
        internal var value: Float = 0.toFloat()
    }

    private fun tri_calc_sfb_noise_x34(xr: FloatArray,
                                       xr34: FloatArray?, xrPos: Int, l3_xmin: Float,
                                       bw: Int, sf: Int, did_it: Array<CalcNoiseCache>): Boolean {
        if (did_it[sf].valid == 0) {
            did_it[sf].valid = 1
            did_it[sf].value = calc_sfb_noise_x34(xr, xr34, xrPos, bw, sf)
        }
        if (l3_xmin < did_it[sf].value) {
            return true
        }
        if (sf < 255) {
            val sf_x = sf + 1
            if (did_it[sf_x].valid == 0) {
                did_it[sf_x].valid = 1
                did_it[sf_x].value = calc_sfb_noise_x34(xr, xr34, xrPos, bw,
                        sf_x)
            }
            if (l3_xmin < did_it[sf_x].value) {
                return true
            }
        }
        if (sf > 0) {
            val sf_x = sf - 1
            if (did_it[sf_x].valid == 0) {
                did_it[sf_x].valid = 1
                did_it[sf_x].value = calc_sfb_noise_x34(xr, xr34, xrPos, bw,
                        sf_x)
            }
            if (l3_xmin < did_it[sf_x].value) {
                return true
            }
        }
        return false
    }

    /**
     * the find_scalefac* routines calculate a quantization step size which
     * would introduce as much noise as is allowed. The larger the step size the
     * more quantization noise we'll get. The scalefactors are there to lower
     * the global step size, allowing limited differences in quantization step
     * sizes per band (shaping the noise).
     */
    private fun find_scalefac_x34(xr: FloatArray, xr34: FloatArray?,
                                  xrPos: Int, l3_xmin: Float, bw: Int, sf_min: Int): Int {
        val did_it = Array(256) { CalcNoiseCache() }
        var sf = 128
        var sf_ok = 255
        var delsf = 128
        var seen_good_one = 0
        var i: Int
        i = 0
        while (i < 8) {
            delsf = delsf shr 1
            if (sf <= sf_min) {
                sf += delsf
            } else {
                val bad = tri_calc_sfb_noise_x34(xr, xr34, xrPos,
                        l3_xmin, bw, sf, did_it)
                if (bad) {
                    /* distortion. try a smaller scalefactor */
                    sf -= delsf
                } else {
                    sf_ok = sf
                    sf += delsf
                    seen_good_one = 1
                }
            }
            ++i
        }
        // returning a scalefac without distortion, if possible
        if (seen_good_one > 0) {
            return sf_ok
        }
        return if (sf <= sf_min) {
            sf_min
        } else sf
    }

    /**
     *
     * calc_short_block_vbr_sf(), calc_long_block_vbr_sf()
     *
     * a variation for vbr-mtrh
     *
     * @author Mark Taylor 2000-??-??
     * @author Robert Hegemann 2000-10-25 made functions of it
     */
    private fun block_sf(that: algo_t, l3_xmin: FloatArray,
                         vbrsf: IntArray, vbrsfmin: IntArray): Int {
        var max_xr34: Float
        val xr = that.cod_info!!.xr
        val xr34_orig = that.xr34orig
        val width = that.cod_info!!.width
        val max_nonzero_coeff = that.cod_info!!.max_nonzero_coeff
        var maxsf = 0
        var sfb = 0
        var j = 0
        var i = 0
        val psymax = that.cod_info!!.psymax

        assert(that.cod_info!!.max_nonzero_coeff >= 0)

        that.mingain_l = 0
        that.mingain_s[0] = 0
        that.mingain_s[1] = 0
        that.mingain_s[2] = 0
        while (j <= max_nonzero_coeff) {
            val w = width[sfb]
            val m = max_nonzero_coeff - j + 1
            var l = w
            val m1: Int
            val m2: Int
            if (l > m) {
                l = m
            }
            max_xr34 = max_x34(xr34_orig, j, l)

            m1 = findLowestScalefac(max_xr34)
            vbrsfmin[sfb] = m1
            if (that.mingain_l < m1) {
                that.mingain_l = m1
            }
            if (that.mingain_s[i] < m1) {
                that.mingain_s[i] = m1
            }
            if (++i > 2) {
                i = 0
            }
            if (sfb < psymax) {
                if (belowNoiseFloor(xr, j, l3_xmin[sfb], l) == 0) {
                    m2 = find_scalefac_x34(xr, xr34_orig, j, l3_xmin[sfb], l,
                            m1)
                    if (maxsf < m2) {
                        maxsf = m2
                    }
                } else {
                    m2 = 255
                    maxsf = 255
                }
            } else {
                if (maxsf < m1) {
                    maxsf = m1
                }
                m2 = maxsf
            }
            vbrsf[sfb] = m2
            ++sfb
            j += w
        }
        while (sfb < L3Side.SFBMAX) {
            vbrsf[sfb] = maxsf
            vbrsfmin[sfb] = 0
            ++sfb
        }
        return maxsf
    }

    /**
     * quantize xr34 based on scalefactors
     *
     * block_xr34
     *
     * @author Mark Taylor 2000-??-??
     * @author Robert Hegemann 2000-10-20 made functions of them
     */
    private fun quantize_x34(that: algo_t) {
        val x = DoubleArray(4)
        var xr34_orig = 0
        val cod_info = that.cod_info
        val ifqstep = if (cod_info!!.scalefac_scale == 0) 2 else 4
        var l3 = 0
        var j = 0
        var sfb = 0
        val max_nonzero_coeff = cod_info.max_nonzero_coeff

        assert(cod_info.max_nonzero_coeff >= 0)
        assert(cod_info.max_nonzero_coeff < 576)

        while (j <= max_nonzero_coeff) {
            val s = (cod_info.scalefac[sfb] + if (cod_info.preflag != 0)
                qupvt.pretab[sfb]
            else
                0) * ifqstep + cod_info.subblock_gain[cod_info.window[sfb]] * 8
            val sfac = cod_info.global_gain - s
            val sfpow34 = qupvt.ipow20[sfac]
            val w = cod_info.width[sfb]
            val m = max_nonzero_coeff - j + 1
            var l = w
            val remaining: Int

            assert(cod_info.global_gain - s >= 0)
            assert(cod_info.width[sfb] >= 0)

            if (l > m) {
                l = m
            }
            j += w
            ++sfb
            l = l shr 1
            remaining = l and 1

            l = l shr 1
            while (l > 0) {
                x[0] = (sfpow34 * that.xr34orig!![xr34_orig + 0]).toDouble()
                x[1] = (sfpow34 * that.xr34orig!![xr34_orig + 1]).toDouble()
                x[2] = (sfpow34 * that.xr34orig!![xr34_orig + 2]).toDouble()
                x[3] = (sfpow34 * that.xr34orig!![xr34_orig + 3]).toDouble()

                k_34_4(x, cod_info.l3_enc, l3)

                l3 += 4
                xr34_orig += 4
                --l
            }
            if (remaining != 0) {
                x[0] = (sfpow34 * that.xr34orig!![xr34_orig + 0]).toDouble()
                x[1] = (sfpow34 * that.xr34orig!![xr34_orig + 1]).toDouble()

                k_34_2(x, cod_info.l3_enc, l3)

                l3 += 2
                xr34_orig += 2
            }
        }
    }

    fun set_subblock_gain(cod_info: GrInfo,
                          mingain_s: IntArray, sf: IntArray) {
        val maxrange1 = 15
        val maxrange2 = 7
        val ifqstepShift = if (cod_info.scalefac_scale == 0) 1 else 2
        val sbg = cod_info.subblock_gain
        val psymax = cod_info.psymax
        var psydiv = 18
        val sbg0: Int
        val sbg1: Int
        val sbg2: Int
        var sfb: Int
        var min_sbg = 7

        if (psydiv > psymax) {
            psydiv = psymax
        }
        for (i in 0..2) {
            var maxsf1 = 0
            var maxsf2 = 0
            var minsf = 1000
            /* see if we should use subblock gain */
            sfb = i
            while (sfb < psydiv) {
                /* part 1 */
                val v = -sf[sfb]
                if (maxsf1 < v) {
                    maxsf1 = v
                }
                if (minsf > v) {
                    minsf = v
                }
                sfb += 3
            }
            while (sfb < L3Side.SFBMAX) {
                /* part 2 */
                val v = -sf[sfb]
                if (maxsf2 < v) {
                    maxsf2 = v
                }
                if (minsf > v) {
                    minsf = v
                }
                sfb += 3
            }

            /*
			 * boost subblock gain as little as possible so we can reach maxsf1
			 * with scalefactors 8*sbg >= maxsf1
			 */
            run {
                val m1 = maxsf1 - (maxrange1 shl ifqstepShift)
                val m2 = maxsf2 - (maxrange2 shl ifqstepShift)

                maxsf1 = Math.max(m1, m2)
            }
            if (minsf > 0) {
                sbg[i] = minsf shr 3
            } else {
                sbg[i] = 0
            }
            if (maxsf1 > 0) {
                val m1 = sbg[i]
                val m2 = maxsf1 + 7 shr 3
                sbg[i] = Math.max(m1, m2)
            }
            if (sbg[i] > 0 && mingain_s[i] > cod_info.global_gain - sbg[i] * 8) {
                sbg[i] = cod_info.global_gain - mingain_s[i] shr 3
            }
            if (sbg[i] > 7) {
                sbg[i] = 7
            }
            if (min_sbg > sbg[i]) {
                min_sbg = sbg[i]
            }
        }
        sbg0 = sbg[0] * 8
        sbg1 = sbg[1] * 8
        sbg2 = sbg[2] * 8
        sfb = 0
        while (sfb < L3Side.SFBMAX) {
            sf[sfb + 0] += sbg0
            sf[sfb + 1] += sbg1
            sf[sfb + 2] += sbg2
            sfb += 3
        }
        if (min_sbg > 0) {
            for (i in 0..2) {
                sbg[i] -= min_sbg
            }
            cod_info.global_gain -= min_sbg * 8
        }
    }

    fun set_scalefacs(cod_info: GrInfo,
                      vbrsfmin: IntArray, sf: IntArray, max_range: IntArray) {
        val ifqstep = if (cod_info.scalefac_scale == 0) 2 else 4
        val ifqstepShift = if (cod_info.scalefac_scale == 0) 1 else 2
        val scalefac = cod_info.scalefac
        val sfbmax = cod_info.sfbmax
        val sbg = cod_info.subblock_gain
        val window = cod_info.window
        val preflag = cod_info.preflag

        if (preflag != 0) {
            for (sfb in 11 until sfbmax) {
                sf[sfb] += qupvt.pretab[sfb] * ifqstep
            }
        }
        for (sfb in 0 until sfbmax) {
            val gain = (cod_info.global_gain - sbg[window[sfb]] * 8
                    - (if (preflag != 0) qupvt.pretab[sfb] else 0) * ifqstep)

            if (sf[sfb] < 0) {
                val m = gain - vbrsfmin[sfb]
                /* ifqstep*scalefac >= -sf[sfb], so round UP */
                scalefac[sfb] = ifqstep - 1 - sf[sfb] shr ifqstepShift

                if (scalefac[sfb] > max_range[sfb]) {
                    scalefac[sfb] = max_range[sfb]
                }
                if (scalefac[sfb] > 0 && scalefac[sfb] shl ifqstepShift > m) {
                    scalefac[sfb] = m shr ifqstepShift
                }
            } else {
                scalefac[sfb] = 0
            }
        }
        for (sfb in sfbmax until L3Side.SFBMAX) {
            scalefac[sfb] = 0 /* sfb21 */
        }
    }

    fun checkScalefactor(cod_info: GrInfo,
                         vbrsfmin: IntArray): Boolean {
        val ifqstep = if (cod_info.scalefac_scale == 0) 2 else 4
        for (sfb in 0 until cod_info.psymax) {
            val s = (cod_info.scalefac[sfb] + if (cod_info.preflag != 0)
                qupvt.pretab[sfb]
            else
                0) * ifqstep + cod_info.subblock_gain[cod_info.window[sfb]] * 8

            if (cod_info.global_gain - s < vbrsfmin[sfb]) {
                /**
                 * <CODE>
                 * fprintf( stdout, "sf %d\n", sfb );
                 * fprintf( stdout, "min %d\n", vbrsfmin[sfb] );
                 * fprintf( stdout, "ggain %d\n", cod_info.global_gain );
                 * fprintf( stdout, "scalefac %d\n", cod_info.scalefac[sfb] );
                 * fprintf( stdout, "pretab %d\n", (cod_info.preflag ? pretab[sfb] : 0) );
                 * fprintf( stdout, "scale %d\n", (cod_info.scalefac_scale + 1) );
                 * fprintf( stdout, "subgain %d\n", cod_info.subblock_gain[cod_info.window[sfb]] * 8 );
                 * fflush( stdout );
                 * exit(-1);
                </CODE> *
                 */
                return false
            }
        }
        return true
    }

    private fun bitcount(that: algo_t) {
        val rc: Boolean

        if (that.gfc!!.mode_gr == 2) {
            rc = tak.scale_bitcount(that.cod_info!!)
        } else {
            rc = tak.scale_bitcount_lsf(that.gfc!!, that.cod_info!!)
        }
        if (!rc) {
            return
        }
        /* this should not happen due to the way the scalefactors are selected */
        throw RuntimeException("INTERNAL ERROR IN VBR NEW CODE (986), please send bug report")
    }

    private fun quantizeAndCountBits(that: algo_t): Int {
        quantize_x34(that)
        that.cod_info!!.part2_3_length = tak.noquant_count_bits(that.gfc!!,
                that.cod_info!!, null)
        return that.cod_info!!.part2_3_length
    }

    private fun tryGlobalStepsize(that: algo_t, sfwork: IntArray,
                                  vbrsfmin: IntArray, delta: Int): Int {
        val xrpow_max = that.cod_info!!.xrpow_max
        val sftemp = IntArray(L3Side.SFBMAX)
        val nbits: Int
        var vbrmax = 0
        for (i in 0 until L3Side.SFBMAX) {
            var gain = sfwork[i] + delta
            if (gain < vbrsfmin[i]) {
                gain = vbrsfmin[i]
            }
            if (gain > 255) {
                gain = 255
            }
            if (vbrmax < gain) {
                vbrmax = gain
            }
            sftemp[i] = gain
        }
        that.alloc!!.alloc(that, sftemp, vbrsfmin, vbrmax)
        bitcount(that)
        nbits = quantizeAndCountBits(that)
        that.cod_info!!.xrpow_max = xrpow_max
        return nbits
    }

    private fun searchGlobalStepsizeMax(that: algo_t, sfwork: IntArray,
                                        vbrsfmin: IntArray, target: Int) {
        val cod_info = that.cod_info
        val gain = cod_info!!.global_gain
        var curr = gain
        var gain_ok = 1024
        var nbits = QuantizePVT.LARGE_BITS
        var l = gain
        var r = 512

        assert(gain >= 0)
        while (l <= r) {
            curr = l + r shr 1
            nbits = tryGlobalStepsize(that, sfwork, vbrsfmin, curr - gain)
            if (nbits == 0 || nbits + cod_info.part2_length < target) {
                r = curr - 1
                gain_ok = curr
            } else {
                l = curr + 1
                if (gain_ok == 1024) {
                    gain_ok = curr
                }
            }
        }
        if (gain_ok != curr) {
            curr = gain_ok
            nbits = tryGlobalStepsize(that, sfwork, vbrsfmin, curr - gain)
        }
    }

    private fun sfDepth(sfwork: IntArray): Int {
        var m = 0
        var j = L3Side.SFBMAX
        var i = 0
        while (j > 0) {
            val di = 255 - sfwork[i]
            if (m < di) {
                m = di
            }
            assert(sfwork[i] >= 0)
            assert(sfwork[i] <= 255)
            --j
            ++i
        }
        assert(m >= 0)
        assert(m <= 255)
        return m
    }

    private fun cutDistribution(sfwork: IntArray, sf_out: IntArray,
                                cut: Int) {
        var j = L3Side.SFBMAX
        var i = 0
        while (j > 0) {
            val x = sfwork[i]
            sf_out[i] = if (x < cut) x else cut
            --j
            ++i
        }
    }

    private fun flattenDistribution(sfwork: IntArray, sf_out: IntArray,
                                    dm: Int, k: Int, p: Int): Int {
        var sfmax = 0
        if (dm > 0) {
            var j = L3Side.SFBMAX
            var i = 0
            while (j > 0) {
                val di = p - sfwork[i]
                var x = sfwork[i] + k * di / dm
                if (x < 0) {
                    x = 0
                } else {
                    if (x > 255) {
                        x = 255
                    }
                }
                sf_out[i] = x
                if (sfmax < x) {
                    sfmax = x
                }
                --j
                ++i
            }
        } else {
            var j = L3Side.SFBMAX
            var i = 0
            while (j > 0) {
                val x = sfwork[i]
                sf_out[i] = x
                if (sfmax < x) {
                    sfmax = x
                }
                --j
                ++i
            }
        }
        return sfmax
    }

    private fun tryThatOne(that: algo_t, sftemp: IntArray,
                           vbrsfmin: IntArray, vbrmax: Int): Int {
        val xrpow_max = that.cod_info!!.xrpow_max
        var nbits = QuantizePVT.LARGE_BITS
        that.alloc!!.alloc(that, sftemp, vbrsfmin, vbrmax)
        bitcount(that)
        nbits = quantizeAndCountBits(that)
        nbits += that.cod_info!!.part2_length
        that.cod_info!!.xrpow_max = xrpow_max
        return nbits
    }

    private fun outOfBitsStrategy(that: algo_t, sfwork: IntArray,
                                  vbrsfmin: IntArray, target: Int) {
        val wrk = IntArray(L3Side.SFBMAX)
        val dm = sfDepth(sfwork)
        val p = that.cod_info!!.global_gain

        /* PART 1 */
        run {
            var bi = dm / 2
            var bi_ok = -1
            var bu = 0
            var bo = dm
            while (true) {
                val sfmax = flattenDistribution(sfwork, wrk, dm, bi, p)
                val nbits = tryThatOne(that, wrk, vbrsfmin, sfmax)
                if (nbits <= target) {
                    bi_ok = bi
                    bo = bi - 1
                } else {
                    bu = bi + 1
                }
                if (bu <= bo) {
                    bi = (bu + bo) / 2
                } else {
                    break
                }
            }
            if (bi_ok >= 0) {
                if (bi != bi_ok) {
                    val sfmax = flattenDistribution(sfwork, wrk, dm,
                            bi_ok, p)
                    tryThatOne(that, wrk, vbrsfmin, sfmax)
                }
                return
            }
        }

        /* PART 2: */
        run {
            var bi = (255 + p) / 2
            var bi_ok = -1
            var bu = p
            var bo = 255
            while (true) {
                val sfmax = flattenDistribution(sfwork, wrk, dm, dm, bi)
                val nbits = tryThatOne(that, wrk, vbrsfmin, sfmax)
                if (nbits <= target) {
                    bi_ok = bi
                    bo = bi - 1
                } else {
                    bu = bi + 1
                }
                if (bu <= bo) {
                    bi = (bu + bo) / 2
                } else {
                    break
                }
            }
            if (bi_ok >= 0) {
                if (bi != bi_ok) {
                    val sfmax = flattenDistribution(sfwork, wrk, dm, dm,
                            bi_ok)
                    tryThatOne(that, wrk, vbrsfmin, sfmax)
                }
                return
            }
        }

        /* fall back to old code, likely to be never called */
        searchGlobalStepsizeMax(that, wrk, vbrsfmin, target)
    }

    private fun reduce_bit_usage(gfc: LameInternalFlags, gr: Int,
                                 ch: Int): Int {
        val cod_info = gfc.l3_side.tt[gr][ch]
        // try some better scalefac storage
        tak.best_scalefac_store(gfc, gr, ch, gfc.l3_side)

        // best huffman_divide may save some bits too
        if (gfc.use_best_huffman == 1)
            tak.best_huffman_divide(gfc, cod_info)
        return cod_info.part2_3_length + cod_info.part2_length
    }

    fun VBR_encode_frame(gfc: LameInternalFlags,
                         xr34orig: Array<Array<FloatArray>>, l3_xmin: Array<Array<FloatArray>>,
                         max_bits: Array<IntArray>): Int {
        val sfwork_ = Array(2) { Array(2) { IntArray(L3Side.SFBMAX) } }
        val vbrsfmin_ = Array(2) { Array(2) { IntArray(L3Side.SFBMAX) } }
        val that_ = Array(2) { Array(2) { algo_t() } }
        val ngr = gfc.mode_gr
        val nch = gfc.channels_out
        val max_nbits_ch = Array(2) { IntArray(2) }
        val max_nbits_gr = IntArray(2)
        var max_nbits_fr = 0
        val use_nbits_ch = Array(2) { IntArray(2) }
        val use_nbits_gr = IntArray(2)
        var use_nbits_fr = 0

        /*
		 * set up some encoding parameters
		 */
        for (gr in 0 until ngr) {
            max_nbits_gr[gr] = 0
            for (ch in 0 until nch) {
                max_nbits_ch[gr][ch] = max_bits[gr][ch]
                use_nbits_ch[gr][ch] = 0
                max_nbits_gr[gr] += max_bits[gr][ch]
                max_nbits_fr += max_bits[gr][ch]
                that_[gr][ch] = algo_t()
                that_[gr][ch].gfc = gfc
                that_[gr][ch].cod_info = gfc.l3_side.tt[gr][ch]
                that_[gr][ch].xr34orig = xr34orig[gr][ch]
                if (that_[gr][ch].cod_info!!.block_type == Encoder.SHORT_TYPE) {
                    that_[gr][ch].alloc = ShortBlockConstrain(this)
                } else {
                    that_[gr][ch].alloc = LongBlockConstrain(this)
                }
            } /* for ch */
        }

        /*
		 * searches scalefactors
		 */
        for (gr in 0 until ngr) {
            for (ch in 0 until nch) {
                if (max_bits[gr][ch] > 0) {
                    val that = that_[gr][ch]
                    val sfwork = sfwork_[gr][ch]
                    val vbrsfmin = vbrsfmin_[gr][ch]
                    val vbrmax: Int

                    vbrmax = block_sf(that, l3_xmin[gr][ch], sfwork, vbrsfmin)
                    that.alloc!!.alloc(that, sfwork, vbrsfmin, vbrmax)
                    bitcount(that)
                } else {
                    /*
					 * xr contains no energy l3_enc, our encoding data, will be
					 * quantized to zero continue with next channel
					 */
                }
            } /* for ch */
        }

        /*
		 * encode 'as is'
		 */
        use_nbits_fr = 0
        for (gr in 0 until ngr) {
            use_nbits_gr[gr] = 0
            for (ch in 0 until nch) {
                val that = that_[gr][ch]
                if (max_bits[gr][ch] > 0) {
                    val max_nonzero_coeff = that.cod_info!!.max_nonzero_coeff

                    assert(max_nonzero_coeff < 576)
                    Arrays.fill(that.cod_info!!.l3_enc, max_nonzero_coeff, 576, 0)

                    quantizeAndCountBits(that)
                } else {
                    /*
					 * xr contains no energy l3_enc, our encoding data, will be
					 * quantized to zero continue with next channel
					 */
                }
                use_nbits_ch[gr][ch] = reduce_bit_usage(gfc, gr, ch)
                use_nbits_gr[gr] += use_nbits_ch[gr][ch]
            } /* for ch */
            use_nbits_fr += use_nbits_gr[gr]
        }

        /*
		 * check bit constrains
		 */
        if (use_nbits_fr <= max_nbits_fr) {
            var ok = true
            for (gr in 0 until ngr) {
                if (use_nbits_gr[gr] > LameInternalFlags.MAX_BITS_PER_GRANULE) {
                    /*
					 * violates the rule that every granule has to use no more
					 * bits than MAX_BITS_PER_GRANULE
					 */
                    ok = false
                }
                for (ch in 0 until nch) {
                    if (use_nbits_ch[gr][ch] > LameInternalFlags.MAX_BITS_PER_CHANNEL) {
                        /*
						 * violates the rule that every gr_ch has to use no more
						 * bits than MAX_BITS_PER_CHANNEL
						 *
						 * This isn't explicitly stated in the ISO docs, but the
						 * part2_3_length field has only 12 bits, that makes it
						 * up to a maximum size of 4095 bits!!!
						 */
                        ok = false
                    }
                }
            }
            if (ok) {
                return use_nbits_fr
            }
        }

        /*
		 * OK, we are in trouble and have to define how many bits are to be used
		 * for each granule
		 */
        run {
            var ok = true
            var sum_fr = 0

            for (gr in 0 until ngr) {
                max_nbits_gr[gr] = 0
                for (ch in 0 until nch) {
                    if (use_nbits_ch[gr][ch] > LameInternalFlags.MAX_BITS_PER_CHANNEL) {
                        max_nbits_ch[gr][ch] = LameInternalFlags.MAX_BITS_PER_CHANNEL
                    } else {
                        max_nbits_ch[gr][ch] = use_nbits_ch[gr][ch]
                    }
                    max_nbits_gr[gr] += max_nbits_ch[gr][ch]
                }
                if (max_nbits_gr[gr] > LameInternalFlags.MAX_BITS_PER_GRANULE) {
                    val f = FloatArray(2)
                    var s = 0f
                    for (ch in 0 until nch) {
                        if (max_nbits_ch[gr][ch] > 0) {
                            f[ch] = Math.sqrt(Math
                                    .sqrt(max_nbits_ch[gr][ch].toDouble())).toFloat()
                            s += f[ch]
                        } else {
                            f[ch] = 0f
                        }
                    }
                    for (ch in 0 until nch) {
                        if (s > 0) {
                            max_nbits_ch[gr][ch] = (LameInternalFlags.MAX_BITS_PER_GRANULE * f[ch] / s).toInt()
                        } else {
                            max_nbits_ch[gr][ch] = 0
                        }
                    }
                    if (nch > 1) {
                        if (max_nbits_ch[gr][0] > use_nbits_ch[gr][0] + 32) {
                            max_nbits_ch[gr][1] += max_nbits_ch[gr][0]
                            max_nbits_ch[gr][1] -= use_nbits_ch[gr][0] + 32
                            max_nbits_ch[gr][0] = use_nbits_ch[gr][0] + 32
                        }
                        if (max_nbits_ch[gr][1] > use_nbits_ch[gr][1] + 32) {
                            max_nbits_ch[gr][0] += max_nbits_ch[gr][1]
                            max_nbits_ch[gr][0] -= use_nbits_ch[gr][1] + 32
                            max_nbits_ch[gr][1] = use_nbits_ch[gr][1] + 32
                        }
                        if (max_nbits_ch[gr][0] > LameInternalFlags.MAX_BITS_PER_CHANNEL) {
                            max_nbits_ch[gr][0] = LameInternalFlags.MAX_BITS_PER_CHANNEL
                        }
                        if (max_nbits_ch[gr][1] > LameInternalFlags.MAX_BITS_PER_CHANNEL) {
                            max_nbits_ch[gr][1] = LameInternalFlags.MAX_BITS_PER_CHANNEL
                        }
                    }
                    max_nbits_gr[gr] = 0
                    for (ch in 0 until nch) {
                        max_nbits_gr[gr] += max_nbits_ch[gr][ch]
                    }
                }
                sum_fr += max_nbits_gr[gr]
            }
            if (sum_fr > max_nbits_fr) {
                run {
                    val f = FloatArray(2)
                    var s = 0f
                    for (gr in 0 until ngr) {
                        if (max_nbits_gr[gr] > 0) {
                            f[gr] = Math.sqrt(max_nbits_gr[gr].toDouble()).toFloat()
                            s += f[gr]
                        } else {
                            f[gr] = 0f
                        }
                    }
                    for (gr in 0 until ngr) {
                        if (s > 0) {
                            max_nbits_gr[gr] = (max_nbits_fr * f[gr] / s).toInt()
                        } else {
                            max_nbits_gr[gr] = 0
                        }
                    }
                }
                if (ngr > 1) {
                    if (max_nbits_gr[0] > use_nbits_gr[0] + 125) {
                        max_nbits_gr[1] += max_nbits_gr[0]
                        max_nbits_gr[1] -= use_nbits_gr[0] + 125
                        max_nbits_gr[0] = use_nbits_gr[0] + 125
                    }
                    if (max_nbits_gr[1] > use_nbits_gr[1] + 125) {
                        max_nbits_gr[0] += max_nbits_gr[1]
                        max_nbits_gr[0] -= use_nbits_gr[1] + 125
                        max_nbits_gr[1] = use_nbits_gr[1] + 125
                    }
                    for (gr in 0 until ngr) {
                        if (max_nbits_gr[gr] > LameInternalFlags.MAX_BITS_PER_GRANULE) {
                            max_nbits_gr[gr] = LameInternalFlags.MAX_BITS_PER_GRANULE
                        }
                    }
                }
                for (gr in 0 until ngr) {
                    val f = FloatArray(2)
                    var s = 0f
                    for (ch in 0 until nch) {
                        if (max_nbits_ch[gr][ch] > 0) {
                            f[ch] = Math.sqrt(max_nbits_ch[gr][ch].toDouble()).toFloat()
                            s += f[ch]
                        } else {
                            f[ch] = 0f
                        }
                    }
                    for (ch in 0 until nch) {
                        if (s > 0) {
                            max_nbits_ch[gr][ch] = (max_nbits_gr[gr] * f[ch] / s).toInt()
                        } else {
                            max_nbits_ch[gr][ch] = 0
                        }
                    }
                    if (nch > 1) {
                        if (max_nbits_ch[gr][0] > use_nbits_ch[gr][0] + 32) {
                            max_nbits_ch[gr][1] += max_nbits_ch[gr][0]
                            max_nbits_ch[gr][1] -= use_nbits_ch[gr][0] + 32
                            max_nbits_ch[gr][0] = use_nbits_ch[gr][0] + 32
                        }
                        if (max_nbits_ch[gr][1] > use_nbits_ch[gr][1] + 32) {
                            max_nbits_ch[gr][0] += max_nbits_ch[gr][1]
                            max_nbits_ch[gr][0] -= use_nbits_ch[gr][1] + 32
                            max_nbits_ch[gr][1] = use_nbits_ch[gr][1] + 32
                        }
                        for (ch in 0 until nch) {
                            if (max_nbits_ch[gr][ch] > LameInternalFlags.MAX_BITS_PER_CHANNEL) {
                                max_nbits_ch[gr][ch] = LameInternalFlags.MAX_BITS_PER_CHANNEL
                            }
                        }
                    }
                }
            }
            /* sanity check */
            sum_fr = 0
            for (gr in 0 until ngr) {
                var sum_gr = 0
                for (ch in 0 until nch) {
                    sum_gr += max_nbits_ch[gr][ch]
                    if (max_nbits_ch[gr][ch] > LameInternalFlags.MAX_BITS_PER_CHANNEL) {
                        ok = false
                    }
                }
                sum_fr += sum_gr
                if (sum_gr > LameInternalFlags.MAX_BITS_PER_GRANULE) {
                    ok = false
                }
            }
            if (sum_fr > max_nbits_fr) {
                ok = false
            }
            if (!ok) {
                /*
				 * we must have done something wrong, fallback to 'on_pe' based
				 * constrain
				 */
                for (gr in 0 until ngr) {
                    for (ch in 0 until nch) {
                        max_nbits_ch[gr][ch] = max_bits[gr][ch]
                    }
                }
            }
        }

        /*
		 * we already called the 'best_scalefac_store' function, so we need to
		 * reset some variables before we can do it again.
		 */
        for (ch in 0 until nch) {
            gfc.l3_side.scfsi[ch][0] = 0
            gfc.l3_side.scfsi[ch][1] = 0
            gfc.l3_side.scfsi[ch][2] = 0
            gfc.l3_side.scfsi[ch][3] = 0
        }
        for (gr in 0 until ngr) {
            for (ch in 0 until nch) {
                gfc.l3_side.tt[gr][ch].scalefac_compress = 0
            }
        }

        /*
		 * alter our encoded data, until it fits into the target bitrate
		 */
        use_nbits_fr = 0
        for (gr in 0 until ngr) {
            use_nbits_gr[gr] = 0
            for (ch in 0 until nch) {
                val that = that_[gr][ch]
                use_nbits_ch[gr][ch] = 0
                if (max_bits[gr][ch] > 0) {
                    val sfwork = sfwork_[gr][ch]
                    val vbrsfmin = vbrsfmin_[gr][ch]
                    cutDistribution(sfwork, sfwork, that.cod_info!!.global_gain)
                    outOfBitsStrategy(that, sfwork, vbrsfmin,
                            max_nbits_ch[gr][ch])
                }
                use_nbits_ch[gr][ch] = reduce_bit_usage(gfc, gr, ch)
                assert(use_nbits_ch[gr][ch] <= max_nbits_ch[gr][ch])
                use_nbits_gr[gr] += use_nbits_ch[gr][ch]
            } /* for ch */
            use_nbits_fr += use_nbits_gr[gr]
        }

        /*
		 * check bit constrains, but it should always be ok, if there are no
		 * bugs ;-)
		 */
        if (use_nbits_fr <= max_nbits_fr) {
            return use_nbits_fr
        }

        throw RuntimeException(String.format(
                "INTERNAL ERROR IN VBR NEW CODE (1313), please send bug report\n" + "maxbits=%d usedbits=%d\n", max_nbits_fr,
                use_nbits_fr))
    }

    companion object {

        val max_range_short = intArrayOf(15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 0, 0, 0)

        val max_range_long = intArrayOf(15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 0)

        val max_range_long_lsf_pretab = intArrayOf(7, 7, 7, 7, 7, 7, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

}
