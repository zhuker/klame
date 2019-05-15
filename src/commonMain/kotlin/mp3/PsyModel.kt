/*
 *      psymodel.c
 *
 *      Copyright (c) 1999-2000 Mark Taylor
 *      Copyright (c) 2001-2002 Naoki Shibata
 *      Copyright (c) 2000-2003 Takehiro Tominaga
 *      Copyright (c) 2000-2008 Robert Hegemann
 *      Copyright (c) 2000-2005 Gabriel Bouvigne
 *      Copyright (c) 2000-2005 Alexander Leidinger
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

/* $Id: PsyModel.java,v 1.27 2011/05/24 20:48:06 kenchis Exp $ */


/*
PSYCHO ACOUSTICS


This routine computes the psycho acoustics, delayed by one granule.

Input: buffer of PCM data (1024 samples).

This window should be centered over the 576 sample granule window.
The routine will compute the psycho acoustics for
this granule, but return the psycho acoustics computed
for the *previous* granule.  This is because the block
type of the previous granule can only be determined
after we have computed the psycho acoustics for the following
granule.

Output:  maskings and energies for each scalefactor band.
block type, PE, and some correlation measures.
The PE is used by CBR modes to determine if extra bits
from the bit reservoir should be used.  The correlation
measures are used to determine mid/side or regular stereo.
*/
/*
Notation:

barks:  a non-linear frequency scale.  Mapping from frequency to
        barks is given by freq2bark()

scalefactor bands: The spectrum (frequencies) are broken into
                   SBMAX "scalefactor bands".  Thes bands
                   are determined by the MPEG ISO spec.  In
                   the noise shaping/quantization code, we allocate
                   bits among the partition bands to achieve the
                   best possible quality

partition bands:   The spectrum is also broken into about
                   64 "partition bands".  Each partition
                   band is about .34 barks wide.  There are about 2-5
                   partition bands for each scalefactor band.

LAME computes all psycho acoustic information for each partition
band.  Then at the end of the computations, this information
is mapped to scalefactor bands.  The energy in each scalefactor
band is taken as the sum of the energy in all partition bands
which overlap the scalefactor band.  The maskings can be computed
in the same way (and thus represent the average masking in that band)
or by taking the minmum value multiplied by the number of
partition bands used (which represents a minimum masking in that band).
*/
/*
The general outline is as follows:

1. compute the energy in each partition band
2. compute the tonality in each partition band
3. compute the strength of each partion band "masker"
4. compute the masking (via the spreading function applied to each masker)
5. Modifications for mid/side masking.

Each partition band is considiered a "masker".  The strength
of the i'th masker in band j is given by:

    s3(bark(i)-bark(j))*strength(i)

The strength of the masker is a function of the energy and tonality.
The more tonal, the less masking.  LAME uses a simple linear formula
(controlled by NMT and TMN) which says the strength is given by the
energy divided by a linear function of the tonality.
*/
/*
s3() is the "spreading function".  It is given by a formula
determined via listening tests.

The total masking in the j'th partition band is the sum over
all maskings i.  It is thus given by the convolution of
the strength with s3(), the "spreading function."

masking(j) = sum_over_i  s3(i-j)*strength(i)  = s3 o strength

where "o" = convolution operator.  s3 is given by a formula determined
via listening tests.  It is normalized so that s3 o 1 = 1.

Note: instead of a simple convolution, LAME also has the
option of using "additive masking"

The most critical part is step 2, computing the tonality of each
partition band.  LAME has two tonality estimators.  The first
is based on the ISO spec, and measures how predictiable the
signal is over time.  The more predictable, the more tonal.
The second measure is based on looking at the spectrum of
a single granule.  The more peaky the spectrum, the more
tonal.  By most indications, the latter approach is better.

Finally, in step 5, the maskings for the mid and side
channel are possibly increased.  Under certain circumstances,
noise in the mid & side channels is assumed to also
be masked by strong maskers in the L or R channels.


Other data computed by the psy-model:

ms_ratio        side-channel / mid-channel masking ratio (for previous granule)
ms_ratio_next   side-channel / mid-channel masking ratio for this granule

percep_entropy[2]     L and R values (prev granule) of PE - A measure of how
                      much pre-echo is in the previous granule
percep_entropy_MS[2]  mid and side channel values (prev granule) of percep_entropy
energy[4]             L,R,M,S energy in each channel, prev granule
blocktype_d[2]        block type to use for previous granule
*/
package mp3

import jdk.Arrays
import jdk.Math
import jdk.assert

class PsyModel {

    private val fft = FFT()

    private var ma_max_i1: Float = 0.toFloat()
    private var ma_max_i2: Float = 0.toFloat()
    private var ma_max_m: Float = 0.toFloat()

    /**
     * <PRE>
     * L3psycho_anal.  Compute psycho acoustics.
     *
     * Data returned to the calling program must be delayed by one
     * granule.
     *
     * This is done in two places.
     * If we do not need to know the blocktype, the copying
     * can be done here at the top of the program: we copy the data for
     * the last granule (computed during the last call) before it is
     * overwritten with the new data.  It looks like this:
     *
     * 0. static psymodel_data
     * 1. calling_program_data = psymodel_data
     * 2. compute psymodel_data
     *
     * For data which needs to know the blocktype, the copying must be
     * done at the end of this loop, and the old values must be saved:
     *
     * 0. static psymodel_data_old
     * 1. compute psymodel_data
     * 2. compute possible block type of this granule
     * 3. compute final block type of previous granule based on #2.
     * 4. calling_program_data = psymodel_data_old
     * 5. psymodel_data_old = psymodel_data
     * psycho_loudness_approx
     * jd - 2001 mar 12
     * in:  energy   - BLKSIZE/2 elements of frequency magnitudes ^ 2
     * gfp      - uses out_samplerate, ATHtype (also needed for ATHformula)
     * returns: loudness^2 approximation, a positive value roughly tuned for a value
     * of 1.0 for signals near clipping.
     * notes:   When calibrated, feeding this function binary white noise at sample
     * values +32767 or -32768 should return values that approach 3.
     * ATHformula is used to approximate an equal loudness curve.
     * future:  Data indicates that the shape of the equal loudness curve varies
     * with intensity.  This function might be improved by using an equal
     * loudness curve shaped for typical playback levels (instead of the
     * ATH, that is shaped for the threshold).  A flexible realization might
     * simply bend the existing ATH curve to achieve the desired shape.
     * However, the potential gain may not be enough to justify an effort.
    </PRE> *
     */
    private fun psycho_loudness_approx(energy: FloatArray,
                                       gfc: LameInternalFlags): Float {
        var loudness_power = 0.0f
        /* apply weights to power in freq. bands */
        for (i in 0 until Encoder.BLKSIZE / 2)
            loudness_power += energy[i] * gfc.ATH!!.eql_w[i]
        loudness_power *= VO_SCALE

        return loudness_power
    }

    private fun compute_ffts(gfp: LameGlobalFlags,
                             fftenergy: FloatArray, fftenergy_s: Array<FloatArray>,
                             wsamp_l: Array<FloatArray>, wsamp_lPos: Int,
                             wsamp_s: Array<Array<FloatArray>>, wsamp_sPos: Int, gr_out: Int,
                             chn: Int, buffer: Array<FloatArray>, bufPos: Int) {
        val gfc = gfp.internal_flags!!
        if (chn < 2) {
            fft.fft_long(gfc, wsamp_l[wsamp_lPos], chn, buffer, bufPos)
            fft.fft_short(gfc, wsamp_s[wsamp_sPos], chn, buffer, bufPos)
        } else if (chn == 2) {
            for (j in Encoder.BLKSIZE - 1 downTo 0) {
                val l = wsamp_l[wsamp_lPos + 0][j]
                val r = wsamp_l[wsamp_lPos + 1][j]
                wsamp_l[wsamp_lPos + 0][j] = (l + r) * Util.SQRT2 * 0.5f
                wsamp_l[wsamp_lPos + 1][j] = (l - r) * Util.SQRT2 * 0.5f
            }
            for (b in 2 downTo 0) {
                for (j in Encoder.BLKSIZE_s - 1 downTo 0) {
                    val l = wsamp_s[wsamp_sPos + 0][b][j]
                    val r = wsamp_s[wsamp_sPos + 1][b][j]
                    wsamp_s[wsamp_sPos + 0][b][j] = (l + r) * Util.SQRT2 * 0.5f
                    wsamp_s[wsamp_sPos + 1][b][j] = (l - r) * Util.SQRT2 * 0.5f
                }
            }
        }/* FFT data for mid and side channel is derived from L & R */

        /*********************************************************************
         * compute energies
         */
        fftenergy[0] = NON_LINEAR_SCALE_ENERGY(wsamp_l[wsamp_lPos + 0][0])
        fftenergy[0] *= fftenergy[0]

        for (j in Encoder.BLKSIZE / 2 - 1 downTo 0) {
            val re = wsamp_l[wsamp_lPos + 0][Encoder.BLKSIZE / 2 - j]
            val im = wsamp_l[wsamp_lPos + 0][Encoder.BLKSIZE / 2 + j]
            fftenergy[Encoder.BLKSIZE / 2 - j] = NON_LINEAR_SCALE_ENERGY((re * re + im * im) * 0.5f)
        }
        for (b in 2 downTo 0) {
            fftenergy_s[b][0] = wsamp_s[wsamp_sPos + 0][b][0]
            fftenergy_s[b][0] *= fftenergy_s[b][0]
            for (j in Encoder.BLKSIZE_s / 2 - 1 downTo 0) {
                val re = wsamp_s[wsamp_sPos + 0][b][Encoder.BLKSIZE_s / 2 - j]
                val im = wsamp_s[wsamp_sPos + 0][b][Encoder.BLKSIZE_s / 2 + j]
                fftenergy_s[b][Encoder.BLKSIZE_s / 2 - j] = NON_LINEAR_SCALE_ENERGY((re * re + im * im) * 0.5f)
            }
        }
        /* total energy */
        run {
            var totalenergy = 0.0f
            for (j in 11 until Encoder.HBLKSIZE)
                totalenergy += fftenergy[j]

            gfc.tot_ener[chn] = totalenergy
        }

        if (gfp.analysis) {
            val pinfo = gfc.pinfo!!
            for (j in 0 until Encoder.HBLKSIZE) {
                pinfo.energy[gr_out][chn][j] = pinfo.energy_save[chn][j]
                pinfo.energy_save[chn][j] = fftenergy[j].toDouble()
            }
            pinfo.pe[gr_out][chn] = gfc.pe[chn].toDouble()
        }

        /*********************************************************************
         * compute loudness approximation (used for ATH auto-level adjustment)
         */
        if (gfp.athaa_loudapprox == 2 && chn < 2) {
            // no loudness for mid/side ch
            gfc.loudness_sq[gr_out][chn] = gfc.loudness_sq_save[chn]
            gfc.loudness_sq_save[chn] = psycho_loudness_approx(fftenergy, gfc)
        }
    }

    private fun init_mask_add_max_values() {
        ma_max_i1 = Math.pow(10.0, (I1LIMIT + 1) / 16.0).toFloat()
        ma_max_i2 = Math.pow(10.0, (I2LIMIT + 1) / 16.0).toFloat()
        ma_max_m = Math.pow(10.0, MLIMIT / 10.0).toFloat()
    }

    /**
     * addition of simultaneous masking Naoki Shibata 2000/7
     */
    private fun mask_add(m1: Float, m2: Float, kk: Int, b: Int,
                         gfc: LameInternalFlags, shortblock: Int): Float {
        var m1 = m1
        var m2 = m2
        val ratio: Float

        if (m2 > m1) {
            if (m2 < m1 * ma_max_i2)
                ratio = m2 / m1
            else
                return m1 + m2
        } else {
            if (m1 >= m2 * ma_max_i2)
                return m1 + m2
            ratio = m1 / m2
        }

        /* Should always be true, just checking */
        assert(m1 >= 0)
        assert(m2 >= 0)

        m1 += m2
        if ((b + 3).toLong() and 0xffffffffL <= 3 + 3) {
            /* approximately, 1 bark = 3 partitions */
            /* 65% of the cases */
            /* originally 'if(i > 8)' */
            if (ratio >= ma_max_i1) {
                /* 43% of the total */
                return m1
            }

            /* 22% of the total */
            val i = Util.FAST_LOG10_X(ratio, 16.0f).toInt()
            return m1 * table2[i]
        }

        /**
         * <PRE>
         * m<15 equ log10((m1+m2)/gfc.ATH.cb[k])<1.5
         * equ (m1+m2)/gfc.ATH.cb[k]<10^1.5
         * equ (m1+m2)<10^1.5 * gfc.ATH.cb[k]
        </PRE> *
         */

        val i = Util.FAST_LOG10_X(ratio, 16.0f).toInt()
        val ath = gfc.ATH!!
        if (shortblock != 0) {
            m2 = ath.cb_s[kk] * ath.adjust
        } else {
            m2 = ath.cb_l[kk] * ath.adjust
        }
        assert(m2 >= 0)
        if (m1 < ma_max_m * m2) {
            /* 3% of the total */
            /* Originally if (m > 0) { */
            if (m1 > m2) {
                var f: Float
                val r: Float

                f = 1.0f
                if (i <= 13)
                    f = table3[i]

                r = Util.FAST_LOG10_X(m1 / m2, 10.0f / 15.0f)
                return m1 * ((table1[i] - f) * r + f)
            }

            return if (i > 13) m1 else m1 * table3[i]

        }

        /* 10% of total */
        return m1 * table1[i]
    }

    /**
     * addition of simultaneous masking Naoki Shibata 2000/7
     */
    private fun vbrpsy_mask_add(m1: Float, m2: Float, b: Int): Float {
        var m1 = m1
        var m2 = m2
        val ratio: Float

        if (m1 < 0) {
            m1 = 0f
        }
        if (m2 < 0) {
            m2 = 0f
        }
        if (m1 <= 0) {
            return m2
        }
        if (m2 <= 0) {
            return m1
        }
        if (m2 > m1) {
            ratio = m2 / m1
        } else {
            ratio = m1 / m2
        }
        if (-2 <= b && b <= 2) {
            /* approximately, 1 bark = 3 partitions */
            /* originally 'if(i > 8)' */
            if (ratio >= ma_max_i1) {
                return m1 + m2
            } else {
                val i = Util.FAST_LOG10_X(ratio, 16.0f).toInt()
                return (m1 + m2) * table2_[i]
            }
        }
        if (ratio < ma_max_i2) {
            return m1 + m2
        }
        if (m1 < m2) {
            m1 = m2
        }
        return m1
    }

    /**
     * compute interchannel masking effects
     */
    private fun calc_interchannel_masking(gfp: LameGlobalFlags,
                                          ratio: Float) {
        val gfc = gfp.internal_flags
        if (gfc!!.channels_out > 1) {
            for (sb in 0 until Encoder.SBMAX_l) {
                val l = gfc.thm[0].l[sb]
                val r = gfc.thm[1].l[sb]
                gfc.thm[0].l[sb] += r * ratio
                gfc.thm[1].l[sb] += l * ratio
            }
            for (sb in 0 until Encoder.SBMAX_s) {
                for (sblock in 0..2) {
                    val l = gfc.thm[0].s[sb][sblock]
                    val r = gfc.thm[1].s[sb][sblock]
                    gfc.thm[0].s[sb][sblock] += r * ratio
                    gfc.thm[1].s[sb][sblock] += l * ratio
                }
            }
        }
    }

    /**
     * compute M/S thresholds from Johnston & Ferreira 1992 ICASSP paper
     */
    private fun msfix1(gfc: LameInternalFlags) {
        for (sb in 0 until Encoder.SBMAX_l) {
            /* use this fix if L & R masking differs by 2db or less */
            /* if db = 10*log10(x2/x1) < 2 */
            /* if (x2 < 1.58*x1) { */
            if (gfc.thm[0].l[sb] > 1.58 * gfc.thm[1].l[sb] || gfc.thm[1].l[sb] > 1.58 * gfc.thm[0].l[sb])
                continue

            var mld = gfc.mld_l[sb] * gfc.en[3].l[sb]
            val rmid = Math.max(gfc.thm[2].l[sb],
                    Math.min(gfc.thm[3].l[sb], mld))

            mld = gfc.mld_l[sb] * gfc.en[2].l[sb]
            val rside = Math.max(gfc.thm[3].l[sb],
                    Math.min(gfc.thm[2].l[sb], mld))
            gfc.thm[2].l[sb] = rmid
            gfc.thm[3].l[sb] = rside
        }

        for (sb in 0 until Encoder.SBMAX_s) {
            for (sblock in 0..2) {
                if (gfc.thm[0].s[sb][sblock] > 1.58 * gfc.thm[1].s[sb][sblock] || gfc.thm[1].s[sb][sblock] > 1.58 * gfc.thm[0].s[sb][sblock])
                    continue

                var mld = gfc.mld_s[sb] * gfc.en[3].s[sb][sblock]
                val rmid = Math.max(gfc.thm[2].s[sb][sblock],
                        Math.min(gfc.thm[3].s[sb][sblock], mld))

                mld = gfc.mld_s[sb] * gfc.en[2].s[sb][sblock]
                val rside = Math.max(gfc.thm[3].s[sb][sblock],
                        Math.min(gfc.thm[2].s[sb][sblock], mld))

                gfc.thm[2].s[sb][sblock] = rmid
                gfc.thm[3].s[sb][sblock] = rside
            }
        }
    }

    /**
     * Adjust M/S maskings if user set "msfix"
     *
     * Naoki Shibata 2000
     */
    private fun ns_msfix(gfc: LameInternalFlags, msfix: Float,
                         athadjust: Float) {
        var msfix = msfix
        var msfix2 = msfix
        var athlower = Math.pow(10.0, athadjust.toDouble()).toFloat()

        msfix *= 2.0f
        msfix2 *= 2.0f
        val ath1 = gfc.ATH!!
        for (sb in 0 until Encoder.SBMAX_l) {
            val thmLR: Float
            var thmM: Float
            var thmS: Float
            val ath: Float
            ath = ath1.cb_l[gfc.bm_l[sb]] * athlower
            thmLR = Math.min(Math.max(gfc.thm[0].l[sb], ath),
                    Math.max(gfc.thm[1].l[sb], ath))
            thmM = Math.max(gfc.thm[2].l[sb], ath)
            thmS = Math.max(gfc.thm[3].l[sb], ath)
            if (thmLR * msfix < thmM + thmS) {
                val f = thmLR * msfix2 / (thmM + thmS)
                thmM *= f
                thmS *= f
                assert(thmM + thmS > 0)
            }
            gfc.thm[2].l[sb] = Math.min(thmM, gfc.thm[2].l[sb])
            gfc.thm[3].l[sb] = Math.min(thmS, gfc.thm[3].l[sb])
        }

        athlower *= Encoder.BLKSIZE_s.toFloat() / Encoder.BLKSIZE
        for (sb in 0 until Encoder.SBMAX_s) {
            for (sblock in 0..2) {
                val thmLR: Float
                var thmM: Float
                var thmS: Float
                val ath: Float
                ath = ath1.cb_s[gfc.bm_s[sb]] * athlower
                thmLR = Math.min(Math.max(gfc.thm[0].s[sb][sblock], ath),
                        Math.max(gfc.thm[1].s[sb][sblock], ath))
                thmM = Math.max(gfc.thm[2].s[sb][sblock], ath)
                thmS = Math.max(gfc.thm[3].s[sb][sblock], ath)

                if (thmLR * msfix < thmM + thmS) {
                    val f = thmLR * msfix / (thmM + thmS)
                    thmM *= f
                    thmS *= f
                    assert(thmM + thmS > 0)
                }
                gfc.thm[2].s[sb][sblock] = Math.min(gfc.thm[2].s[sb][sblock],
                        thmM)
                gfc.thm[3].s[sb][sblock] = Math.min(gfc.thm[3].s[sb][sblock],
                        thmS)
            }
        }
    }

    /**
     * short block threshold calculation (part 2)
     *
     * partition band bo_s[sfb] is at the transition from scalefactor band sfb
     * to the next one sfb+1; enn and thmm have to be split between them
     */
    private fun convert_partition2scalefac_s(gfc: LameInternalFlags,
                                             eb: FloatArray, thr: FloatArray, chn: Int, sblock: Int) {
        var sb: Int
        var b: Int
        var enn = 0.0f
        var thmm = 0.0f
        b = 0
        sb = b
        while (sb < Encoder.SBMAX_s) {
            val bo_s_sb = gfc.bo_s[sb]
            val npart_s = gfc.npart_s
            val b_lim = if (bo_s_sb < npart_s) bo_s_sb else npart_s
            while (b < b_lim) {
                assert(eb[b] >= 0)
                // iff failed, it may indicate some index error elsewhere
                assert(thr[b] >= 0)
                enn += eb[b]
                thmm += thr[b]
                b++
            }
            gfc.en[chn].s[sb][sblock] = enn
            gfc.thm[chn].s[sb][sblock] = thmm

            if (b >= npart_s) {
                ++sb
                break
            }
            assert(eb[b] >= 0)
            // iff failed, it may indicate some index error elsewhere
            assert(thr[b] >= 0)
            run {
                /* at transition sfb . sfb+1 */
                val w_curr = gfc.PSY!!.bo_s_weight[sb]
                val w_next = 1.0f - w_curr
                enn = w_curr * eb[b]
                thmm = w_curr * thr[b]
                gfc.en[chn].s[sb][sblock] += enn
                gfc.thm[chn].s[sb][sblock] += thmm
                enn = w_next * eb[b]
                thmm = w_next * thr[b]
            }
            ++b
            ++sb
        }
        /* zero initialize the rest */
        while (sb < Encoder.SBMAX_s) {
            gfc.en[chn].s[sb][sblock] = 0f
            gfc.thm[chn].s[sb][sblock] = 0f
            ++sb
        }
    }

    /**
     * longblock threshold calculation (part 2)
     */
    private fun convert_partition2scalefac_l(gfc: LameInternalFlags,
                                             eb: FloatArray, thr: FloatArray, chn: Int) {
        var sb: Int
        var b: Int
        var enn = 0.0f
        var thmm = 0.0f
        b = 0
        sb = b
        while (sb < Encoder.SBMAX_l) {
            val bo_l_sb = gfc.bo_l[sb]
            val npart_l = gfc.npart_l
            val b_lim = if (bo_l_sb < npart_l) bo_l_sb else npart_l
            while (b < b_lim) {
                assert(eb[b] >= 0)
                // iff failed, it may indicate some index error elsewhere
                assert(thr[b] >= 0)
                enn += eb[b]
                thmm += thr[b]
                b++
            }
            gfc.en[chn].l[sb] = enn
            gfc.thm[chn].l[sb] = thmm

            if (b >= npart_l) {
                ++sb
                break
            }
            assert(eb[b] >= 0)
            assert(thr[b] >= 0)
            run {
                /* at transition sfb . sfb+1 */
                val w_curr = gfc.PSY!!.bo_l_weight[sb]
                val w_next = 1.0f - w_curr
                enn = w_curr * eb[b]
                thmm = w_curr * thr[b]
                gfc.en[chn].l[sb] += enn
                gfc.thm[chn].l[sb] += thmm
                enn = w_next * eb[b]
                thmm = w_next * thr[b]
            }
            ++b
            ++sb
        }
        /* zero initialize the rest */
        while (sb < Encoder.SBMAX_l) {
            gfc.en[chn].l[sb] = 0f
            gfc.thm[chn].l[sb] = 0f
            ++sb
        }
    }

    private fun compute_masking_s(gfp: LameGlobalFlags,
                                  fftenergy_s: Array<FloatArray>, eb: FloatArray, thr: FloatArray,
                                  chn: Int, sblock: Int) {
        val gfc = gfp.internal_flags
        var j: Int
        var b: Int

        j = 0
        b = j
        while (b < gfc!!.npart_s) {
            var ebb = 0f
            var m = 0f
            val n = gfc.numlines_s[b]
            var i = 0
            while (i < n) {
                val el = fftenergy_s[sblock][j]
                ebb += el
                if (m < el)
                    m = el
                ++i
                ++j
            }
            eb[b] = ebb
            ++b
        }
        assert(b == gfc.npart_s)
        assert(j == 129)
        b = 0
        j = b
        val s3_ss = gfc.s3_ss!!
        while (b < gfc.npart_s) {
            var kk = gfc.s3ind_s[b][0]
            var ecb = s3_ss[j++] * eb[kk]
            ++kk
            while (kk <= gfc.s3ind_s[b][1]) {
                ecb += s3_ss[j] * eb[kk]
                ++j
                ++kk
            }

            run {
                /* limit calculated threshold by previous granule */
                val x = rpelev_s * gfc.nb_s1[chn][b]
                thr[b] = Math.min(ecb, x)
            }
            if (gfc.blocktype_old[chn and 1] == Encoder.SHORT_TYPE) {
                /* limit calculated threshold by even older granule */
                val x = rpelev2_s * gfc.nb_s2[chn][b]
                val y = thr[b]
                thr[b] = Math.min(x, y)
            }

            gfc.nb_s2[chn][b] = gfc.nb_s1[chn][b]
            gfc.nb_s1[chn][b] = ecb
            assert(thr[b] >= 0)
            b++
        }
        while (b <= Encoder.CBANDS) {
            eb[b] = 0f
            thr[b] = 0f
            ++b
        }
    }

    private fun block_type_set(gfp: LameGlobalFlags,
                               uselongblock: IntArray, blocktype_d: IntArray,
                               blocktype: IntArray) {
        val gfc = gfp.internal_flags

        if (gfp.short_blocks == ShortBlock.short_block_coupled
                /* force both channels to use the same block type */
                /* this is necessary if the frame is to be encoded in ms_stereo. */
                /* But even without ms_stereo, FhG does this */
                && !(uselongblock[0] != 0 && uselongblock[1] != 0)) {
            uselongblock[1] = 0
            uselongblock[0] = uselongblock[1]
        }

        /*
		 * update the blocktype of the previous granule, since it depends on
		 * what happend in this granule
		 */
        for (chn in 0 until gfc!!.channels_out) {
            blocktype[chn] = Encoder.NORM_TYPE
            /* disable short blocks */
            if (gfp.short_blocks == ShortBlock.short_block_dispensed)
                uselongblock[chn] = 1
            if (gfp.short_blocks == ShortBlock.short_block_forced)
                uselongblock[chn] = 0

            if (uselongblock[chn] != 0) {
                /* no attack : use long blocks */
                assert(gfc.blocktype_old[chn] != Encoder.START_TYPE)
                if (gfc.blocktype_old[chn] == Encoder.SHORT_TYPE)
                    blocktype[chn] = Encoder.STOP_TYPE
            } else {
                /* attack : use short blocks */
                blocktype[chn] = Encoder.SHORT_TYPE
                if (gfc.blocktype_old[chn] == Encoder.NORM_TYPE) {
                    gfc.blocktype_old[chn] = Encoder.START_TYPE
                }
                if (gfc.blocktype_old[chn] == Encoder.STOP_TYPE)
                    gfc.blocktype_old[chn] = Encoder.SHORT_TYPE
            }

            blocktype_d[chn] = gfc.blocktype_old[chn]
            // value returned to calling program
            gfc.blocktype_old[chn] = blocktype[chn]
            // save for next call to l3psy_anal
        }
    }

    private fun NS_INTERP(x: Float, y: Float, r: Float): Float {
        /* was pow((x),(r))*pow((y),1-(r)) */
        if (r >= 1.0) {
            /* 99.7% of the time */
            return x
        }
        if (r <= 0.0)
            return y
        return if (y > 0.0) {
            /* rest of the time */
            (Math.pow((x / y).toDouble(), r.toDouble()) * y).toFloat()
        } else 0.0f
        /* never happens */
    }

    private fun pecalc_s(mr: III_psy_ratio,
                         masking_lower: Float): Float {
        var pe_s = 1236.28f / 4
        for (sb in 0 until Encoder.SBMAX_s - 1) {
            for (sblock in 0..2) {
                val thm = mr.thm.s[sb][sblock]
                assert(sb < regcoef_s.size)
                if (thm > 0.0) {
                    val x = thm * masking_lower
                    val en = mr.en.s[sb][sblock]
                    if (en > x) {
                        if (en > x * 1e10) {
                            pe_s += regcoef_s[sb] * (10.0f * LOG10)
                        } else {
                            assert(x > 0)
                            pe_s += regcoef_s[sb] * Util.FAST_LOG10(en / x)
                        }
                    }
                }
            }
        }

        return pe_s
    }

    private fun pecalc_l(mr: III_psy_ratio,
                         masking_lower: Float): Float {
        var pe_l = 1124.23f / 4
        for (sb in 0 until Encoder.SBMAX_l - 1) {
            val thm = mr.thm.l[sb]
            assert(sb < regcoef_l.size)
            if (thm > 0.0) {
                val x = thm * masking_lower
                val en = mr.en.l[sb]
                if (en > x) {
                    if (en > x * 1e10) {
                        pe_l += regcoef_l[sb] * (10.0f * LOG10)
                    } else {
                        assert(x > 0)
                        pe_l += regcoef_l[sb] * Util.FAST_LOG10(en / x)
                    }
                }
            }
        }
        return pe_l
    }

    private fun calc_energy(gfc: LameInternalFlags,
                            fftenergy: FloatArray, eb: FloatArray, max: FloatArray,
                            avg: FloatArray) {
        var b: Int
        var j: Int

        j = 0
        b = j
        while (b < gfc.npart_l) {
            var ebb = 0f
            var m = 0f
            var i: Int
            i = 0
            while (i < gfc.numlines_l[b]) {
                val el = fftenergy[j]
                assert(el >= 0)
                ebb += el
                if (m < el)
                    m = el
                ++i
                ++j
            }
            eb[b] = ebb
            max[b] = m
            avg[b] = ebb * gfc.rnumlines_l[b]
            assert(gfc.rnumlines_l[b] >= 0)
            assert(ebb >= 0)
            assert(eb[b] >= 0)
            assert(max[b] >= 0)
            assert(avg[b] >= 0)
            ++b
        }
    }

    private fun calc_mask_index_l(gfc: LameInternalFlags,
                                  max: FloatArray, avg: FloatArray, mask_idx: IntArray) {
        val last_tab_entry = tab.size - 1
        var b = 0
        var a = avg[b] + avg[b + 1]
        assert(a >= 0)
        if (a > 0.0) {
            var m = max[b]
            if (m < max[b + 1])
                m = max[b + 1]
            assert(gfc.numlines_l[b] + gfc.numlines_l[b + 1] - 1 > 0)
            a = 20.0f * (m * 2.0f - a) / (a * (gfc.numlines_l[b] + gfc.numlines_l[b + 1] - 1))
            var k = a.toInt()
            if (k > last_tab_entry)
                k = last_tab_entry
            mask_idx[b] = k
        } else {
            mask_idx[b] = 0
        }

        b = 1
        while (b < gfc.npart_l - 1) {
            a = avg[b - 1] + avg[b] + avg[b + 1]
            assert(a >= 0)
            if (a > 0.0) {
                var m = max[b - 1]
                if (m < max[b])
                    m = max[b]
                if (m < max[b + 1])
                    m = max[b + 1]
                assert((gfc.numlines_l[b - 1] + gfc.numlines_l[b]
                        + gfc.numlines_l[b + 1]) - 1 > 0)
                a = 20.0f * (m * 3.0f - a) / (a * ((gfc.numlines_l[b - 1] + gfc.numlines_l[b]
                        + gfc.numlines_l[b + 1]) - 1))
                var k = a.toInt()
                if (k > last_tab_entry)
                    k = last_tab_entry
                mask_idx[b] = k
            } else {
                mask_idx[b] = 0
            }
            b++
        }
        assert(b > 0)
        assert(b == gfc.npart_l - 1)

        a = avg[b - 1] + avg[b]
        assert(a >= 0)
        if (a > 0.0) {
            var m = max[b - 1]
            if (m < max[b])
                m = max[b]
            assert(gfc.numlines_l[b - 1] + gfc.numlines_l[b] - 1 > 0)
            a = 20.0f * (m * 2.0f - a) / (a * (gfc.numlines_l[b - 1] + gfc.numlines_l[b] - 1))
            var k = a.toInt()
            if (k > last_tab_entry)
                k = last_tab_entry
            mask_idx[b] = k
        } else {
            mask_idx[b] = 0
        }
        assert(b == gfc.npart_l - 1)
    }

    fun L3psycho_anal_ns(gfp: LameGlobalFlags,
                         buffer: Array<FloatArray>, bufPos: Int, gr_out: Int,
                         masking_ratio: Array<Array<III_psy_ratio>>,
                         masking_MS_ratio: Array<Array<III_psy_ratio>>,
                         percep_entropy: FloatArray, percep_MS_entropy: FloatArray,
                         energy: FloatArray, blocktype_d: IntArray): Int {
        /*
		 * to get a good cache performance, one has to think about the sequence,
		 * in which the variables are used.
		 */
        val gfc = gfp.internal_flags

        /* fft and energy calculation */
        val wsamp_L = Array(2) { FloatArray(Encoder.BLKSIZE) }
        val wsamp_S = Array(2) { Array(3) { FloatArray(Encoder.BLKSIZE_s) } }

        /* convolution */
        val eb_l = FloatArray(Encoder.CBANDS + 1)
        val eb_s = FloatArray(Encoder.CBANDS + 1)
        val thr = FloatArray(Encoder.CBANDS + 2)

        /* block type */
        val blocktype = IntArray(2)
        val uselongblock = IntArray(2)

        /* usual variables like loop indices, etc.. */
        var numchn: Int
        var chn: Int
        var b: Int
        var i: Int
        var j: Int
        var k: Int
        var sb: Int
        var sblock: Int

        /* variables used for --nspsytune */
        val ns_hpfsmpl = Array(2) { FloatArray(576) }
        val pcfact: Float

        val mask_idx_l = IntArray(Encoder.CBANDS + 2)
        val mask_idx_s = IntArray(Encoder.CBANDS + 2)

        Arrays.fill(mask_idx_s, 0)

        numchn = gfc!!.channels_out
        /* chn=2 and 3 = Mid and Side channels */
        if (gfp.mode == MPEGMode.JOINT_STEREO)
            numchn = 4

        if (gfp.VBR == VbrMode.vbr_off)
            pcfact = if (gfc.ResvMax == 0)
                0f
            else
                gfc.ResvSize.toFloat() / gfc.ResvMax * 0.5f
        else if (gfp.VBR == VbrMode.vbr_rh || gfp.VBR == VbrMode.vbr_mtrh
                || gfp.VBR == VbrMode.vbr_mt) {
            pcfact = 0.6f
        } else
            pcfact = 1.0f

        /**********************************************************************
         * Apply HPF of fs/4 to the input signal. This is used for attack
         * detection / handling.
         */
        /* Don't copy the input buffer into a temporary buffer */
        /* unroll the loop 2 times */
        chn = 0
        while (chn < gfc.channels_out) {
            /* apply high pass filter of fs/4 */
            val firbuf = buffer[chn]
            val firbufPos = bufPos + 576 - 350 - NSFIRLEN + 192
            assert(fircoef.size == (NSFIRLEN - 1) / 2)
            i = 0
            while (i < 576) {
                var sum1: Float
                var sum2: Float
                sum1 = firbuf[firbufPos + i + 10]
                sum2 = 0.0f
                j = 0
                while (j < (NSFIRLEN - 1) / 2 - 1) {
                    sum1 += fircoef[j] * (firbuf[firbufPos + i + j] + firbuf[(firbufPos + i
                            + NSFIRLEN) - j])
                    sum2 += fircoef[j + 1] * (firbuf[firbufPos + i + j + 1] + firbuf[(firbufPos
                            + i + NSFIRLEN) - j - 1])
                    j += 2
                }
                ns_hpfsmpl[chn][i] = sum1 + sum2
                i++
            }
            masking_ratio[gr_out][chn].en.assign(gfc.en[chn])
            masking_ratio[gr_out][chn].thm.assign(gfc.thm[chn])
            if (numchn > 2) {
                /* MS maskings */
                /* percep_MS_entropy [chn-2] = gfc . pe [chn]; */
                masking_MS_ratio[gr_out][chn].en.assign(gfc.en[chn + 2])
                masking_MS_ratio[gr_out][chn].thm.assign(gfc.thm[chn + 2])
            }
            chn++
        }

        chn = 0
        while (chn < numchn) {
            val wsamp_l: Array<FloatArray>
            val wsamp_s: Array<Array<FloatArray>>
            val en_subshort = FloatArray(12)
            val en_short = floatArrayOf(0f, 0f, 0f, 0f)
            val attack_intensity = FloatArray(12)
            var ns_uselongblock = 1
            val attackThreshold: Float
            val max = FloatArray(Encoder.CBANDS)
            val avg = FloatArray(Encoder.CBANDS)
            val ns_attacks = intArrayOf(0, 0, 0, 0)
            val fftenergy = FloatArray(Encoder.HBLKSIZE)
            val fftenergy_s = Array(3) { FloatArray(Encoder.HBLKSIZE_s) }

            /*
			 * rh 20040301: the following loops do access one off the limits so
			 * I increase the array dimensions by one and initialize the
			 * accessed values to zero
			 */
            assert(gfc.npart_s <= Encoder.CBANDS)
            assert(gfc.npart_l <= Encoder.CBANDS)

            /***************************************************************
             * determine the block type (window type)
             */
            /* calculate energies of each sub-shortblocks */
            i = 0
            while (i < 3) {
                en_subshort[i] = gfc.nsPsy.last_en_subshort[chn][i + 6]
                assert(gfc.nsPsy.last_en_subshort[chn][i + 4] > 0)
                attack_intensity[i] = en_subshort[i] / gfc.nsPsy.last_en_subshort[chn][i + 4]
                en_short[0] += en_subshort[i]
                i++
            }

            if (chn == 2) {
                i = 0
                while (i < 576) {
                    val l: Float
                    val r: Float
                    l = ns_hpfsmpl[0][i]
                    r = ns_hpfsmpl[1][i]
                    ns_hpfsmpl[0][i] = l + r
                    ns_hpfsmpl[1][i] = l - r
                    i++
                }
            }
            run {
                val pf = ns_hpfsmpl[chn and 1]
                var pfPos = 0
                i = 0
                while (i < 9) {
                    val pfe = pfPos + 576 / 9
                    var p = 1f
                    while (pfPos < pfe) {
                        if (p < Math.abs(pf[pfPos]))
                            p = Math.abs(pf[pfPos])
                        pfPos++
                    }

                    en_subshort[i + 3] = p
                    gfc.nsPsy.last_en_subshort[chn][i] = en_subshort[i + 3]
                    en_short[1 + i / 3] += p
                    if (p > en_subshort[i + 3 - 2]) {
                        assert(en_subshort[i + 3 - 2] > 0)
                        p = p / en_subshort[i + 3 - 2]
                    } else if (en_subshort[i + 3 - 2] > p * 10.0f) {
                        assert(p > 0)
                        p = en_subshort[i + 3 - 2] / (p * 10.0f)
                    } else
                        p = 0.0f
                    attack_intensity[i + 3] = p
                    i++
                }
            }

            if (gfp.analysis) {
                var x = attack_intensity[0]
                i = 1
                while (i < 12) {
                    if (x < attack_intensity[i])
                        x = attack_intensity[i]
                    i++
                }
                gfc.pinfo!!.ers[gr_out][chn] = gfc.pinfo!!.ers_save[chn]
                gfc.pinfo!!.ers_save[chn] = x.toDouble()
            }

            /* compare energies between sub-shortblocks */
            attackThreshold = if (chn == 3)
                gfc.nsPsy.attackthre_s
            else
                gfc.nsPsy.attackthre
            i = 0
            while (i < 12) {
                if (0 == ns_attacks[i / 3] && attack_intensity[i] > attackThreshold)
                    ns_attacks[i / 3] = i % 3 + 1
                i++
            }

            /*
			 * should have energy change between short blocks, in order to avoid
			 * periodic signals
			 */
            i = 1
            while (i < 4) {
                val ratio: Float
                if (en_short[i - 1] > en_short[i]) {
                    assert(en_short[i] > 0)
                    ratio = en_short[i - 1] / en_short[i]
                } else {
                    assert(en_short[i - 1] > 0)
                    ratio = en_short[i] / en_short[i - 1]
                }
                if (ratio < 1.7) {
                    ns_attacks[i] = 0
                    if (i == 1)
                        ns_attacks[0] = 0
                }
                i++
            }

            if (ns_attacks[0] != 0 && gfc.nsPsy.lastAttacks[chn] != 0)
                ns_attacks[0] = 0

            if (gfc.nsPsy.lastAttacks[chn] == 3 || ns_attacks[0] + ns_attacks[1] + ns_attacks[2] + ns_attacks[3] != 0) {
                ns_uselongblock = 0

                if (ns_attacks[1] != 0 && ns_attacks[0] != 0)
                    ns_attacks[1] = 0
                if (ns_attacks[2] != 0 && ns_attacks[1] != 0)
                    ns_attacks[2] = 0
                if (ns_attacks[3] != 0 && ns_attacks[2] != 0)
                    ns_attacks[3] = 0
            }

            if (chn < 2) {
                uselongblock[chn] = ns_uselongblock
            } else {
                if (ns_uselongblock == 0) {
                    uselongblock[1] = 0
                    uselongblock[0] = uselongblock[1]
                }
            }

            /*
			 * there is a one granule delay. Copy maskings computed last call
			 * into masking_ratio to return to calling program.
			 */
            energy[chn] = gfc.tot_ener[chn]

            /*********************************************************************
             * compute FFTs
             */
            wsamp_s = wsamp_S
            wsamp_l = wsamp_L
            compute_ffts(gfp, fftenergy, fftenergy_s, wsamp_l, chn and 1,
                    wsamp_s, chn and 1, gr_out, chn, buffer, bufPos)

            /*********************************************************************
             * Calculate the energy and the tonality of each partition.
             */
            calc_energy(gfc, fftenergy, eb_l, max, avg)
            calc_mask_index_l(gfc, max, avg, mask_idx_l)
            /* compute masking thresholds for short blocks */
            sblock = 0
            while (sblock < 3) {
                var enn: Float
                var thmm: Float
                compute_masking_s(gfp, fftenergy_s, eb_s, thr, chn, sblock)
                convert_partition2scalefac_s(gfc, eb_s, thr, chn, sblock)
                /**** short block pre-echo control  */
                sb = 0
                while (sb < Encoder.SBMAX_s) {
                    thmm = gfc.thm[chn].s[sb][sblock]

                    thmm *= NS_PREECHO_ATT0
                    if (ns_attacks[sblock] >= 2 || ns_attacks[sblock + 1] == 1) {
                        val idx = if (sblock != 0) sblock - 1 else 2
                        val p = NS_INTERP(gfc.thm[chn].s[sb][idx], thmm,
                                NS_PREECHO_ATT1 * pcfact).toDouble()
                        thmm = Math.min(thmm.toDouble(), p).toFloat()
                    }

                    if (ns_attacks[sblock] == 1) {
                        val idx = if (sblock != 0) sblock - 1 else 2
                        val p = NS_INTERP(gfc.thm[chn].s[sb][idx], thmm,
                                NS_PREECHO_ATT2 * pcfact).toDouble()
                        thmm = Math.min(thmm.toDouble(), p).toFloat()
                    } else if (sblock != 0 && ns_attacks[sblock - 1] == 3 || sblock == 0 && gfc.nsPsy.lastAttacks[chn] == 3) {
                        val idx = if (sblock != 2) sblock + 1 else 0
                        val p = NS_INTERP(gfc.thm[chn].s[sb][idx], thmm,
                                NS_PREECHO_ATT2 * pcfact).toDouble()
                        thmm = Math.min(thmm.toDouble(), p).toFloat()
                    }

                    /* pulse like signal detection for fatboy.wav and so on */
                    enn = (en_subshort[sblock * 3 + 3]
                            + en_subshort[sblock * 3 + 4]
                            + en_subshort[sblock * 3 + 5])
                    if (en_subshort[sblock * 3 + 5] * 6 < enn) {
                        thmm *= 0.5f
                        if (en_subshort[sblock * 3 + 4] * 6 < enn)
                            thmm *= 0.5f
                    }

                    gfc.thm[chn].s[sb][sblock] = thmm
                    sb++
                }
                sblock++
            }
            gfc.nsPsy.lastAttacks[chn] = ns_attacks[2]

            /*********************************************************************
             * convolve the partitioned energy and unpredictability with the
             * spreading function, s3_l[b][k]
             */
            k = 0
            run {
                b = 0
                val s3_ll = gfc.s3_ll!!
                while (b < gfc.npart_l) {
                    /*
					 * convolve the partitioned energy with the spreading
					 * function
					 */
                    var kk = gfc.s3ind[b][0]
                    var eb2 = eb_l[kk] * tab[mask_idx_l[kk]]
                    var ecb = s3_ll[k++] * eb2
                    while (++kk <= gfc.s3ind[b][1]) {
                        eb2 = eb_l[kk] * tab[mask_idx_l[kk]]
                        ecb = mask_add(ecb, s3_ll[k++] * eb2, kk, kk - b,
                                gfc, 0)
                    }
                    ecb *= 0.158489319246111f /* pow(10,-0.8) */

                    /**** long block pre-echo control  */
                    /**
                     * <PRE>
                     * dont use long block pre-echo control if previous granule was
                     * a short block.  This is to avoid the situation:
                     * frame0:  quiet (very low masking)
                     * frame1:  surge  (triggers short blocks)
                     * frame2:  regular frame.  looks like pre-echo when compared to
                     * frame0, but all pre-echo was in frame1.
                    </PRE> *
                     */
                    /*
					 * chn=0,1 L and R channels
					 *
					 * chn=2,3 S and M channels.
					 */

                    if (gfc.blocktype_old[chn and 1] == Encoder.SHORT_TYPE)
                        thr[b] = ecb
                    else
                        thr[b] = NS_INTERP(
                                Math.min(ecb, Math.min(rpelev * gfc.nb_1[chn][b], rpelev2 * gfc.nb_2[chn][b])), ecb, pcfact)

                    gfc.nb_2[chn][b] = gfc.nb_1[chn][b]
                    gfc.nb_1[chn][b] = ecb
                    b++
                }
            }
            while (b <= Encoder.CBANDS) {
                eb_l[b] = 0f
                thr[b] = 0f
                ++b
            }
            /* compute masking thresholds for long blocks */
            convert_partition2scalefac_l(gfc, eb_l, thr, chn)
            chn++
        } /* end loop over chn */

        if (gfp.mode == MPEGMode.STEREO || gfp.mode == MPEGMode.JOINT_STEREO) {
            if (gfp.interChRatio > 0.0) {
                calc_interchannel_masking(gfp, gfp.interChRatio)
            }
        }

        if (gfp.mode == MPEGMode.JOINT_STEREO) {
            val msfix: Float
            msfix1(gfc)
            msfix = gfp.msfix
            if (Math.abs(msfix) > 0.0)
                ns_msfix(gfc, msfix, gfp.ATHlower * gfc.ATH!!.adjust)
        }

        /***************************************************************
         * determine final block type
         */
        block_type_set(gfp, uselongblock, blocktype_d, blocktype)

        /*********************************************************************
         * compute the value of PE to return ... no delay and advance
         */
        chn = 0
        while (chn < numchn) {
            val ppe: FloatArray
            var ppePos = 0
            var type: Int
            val mr: III_psy_ratio

            if (chn > 1) {
                ppe = percep_MS_entropy
                ppePos = -2
                type = Encoder.NORM_TYPE
                if (blocktype_d[0] == Encoder.SHORT_TYPE || blocktype_d[1] == Encoder.SHORT_TYPE)
                    type = Encoder.SHORT_TYPE
                mr = masking_MS_ratio[gr_out][chn - 2]
            } else {
                ppe = percep_entropy
                ppePos = 0
                type = blocktype_d[chn]
                mr = masking_ratio[gr_out][chn]
            }

            if (type == Encoder.SHORT_TYPE)
                ppe[ppePos + chn] = pecalc_s(mr, gfc.masking_lower)
            else
                ppe[ppePos + chn] = pecalc_l(mr, gfc.masking_lower)

            if (gfp.analysis)
                gfc.pinfo!!.pe[gr_out][chn] = ppe[ppePos + chn].toDouble()
            chn++

        }
        return 0
    }

    private fun vbrpsy_compute_fft_l(gfp: LameGlobalFlags,
                                     buffer: Array<FloatArray>, bufPos: Int, chn: Int,
                                     gr_out: Int, fftenergy: FloatArray, wsamp_l: Array<FloatArray>,
                                     wsamp_lPos: Int) {
        val gfc = gfp.internal_flags!!
        if (chn < 2) {
            fft.fft_long(gfc, wsamp_l[wsamp_lPos], chn, buffer, bufPos)
        } else if (chn == 2) {
            /* FFT data for mid and side channel is derived from L & R */
            for (j in Encoder.BLKSIZE - 1 downTo 0) {
                val l = wsamp_l[wsamp_lPos + 0][j]
                val r = wsamp_l[wsamp_lPos + 1][j]
                wsamp_l[wsamp_lPos + 0][j] = (l + r) * Util.SQRT2 * 0.5f
                wsamp_l[wsamp_lPos + 1][j] = (l - r) * Util.SQRT2 * 0.5f
            }
        }

        /*********************************************************************
         * compute energies
         */
        fftenergy[0] = NON_LINEAR_SCALE_ENERGY(wsamp_l[wsamp_lPos + 0][0])
        fftenergy[0] *= fftenergy[0]

        for (j in Encoder.BLKSIZE / 2 - 1 downTo 0) {
            val re = wsamp_l[wsamp_lPos + 0][Encoder.BLKSIZE / 2 - j]
            val im = wsamp_l[wsamp_lPos + 0][Encoder.BLKSIZE / 2 + j]
            fftenergy[Encoder.BLKSIZE / 2 - j] = NON_LINEAR_SCALE_ENERGY((re * re + im * im) * 0.5f)
        }
        /* total energy */
        run {
            var totalenergy = 0.0f
            for (j in 11 until Encoder.HBLKSIZE)
                totalenergy += fftenergy[j]

            gfc.tot_ener[chn] = totalenergy
        }

        if (gfp.analysis) {
            val pinfo = gfc.pinfo!!
            for (j in 0 until Encoder.HBLKSIZE) {
                pinfo.energy[gr_out][chn][j] = pinfo.energy_save[chn][j]
                pinfo.energy_save[chn][j] = fftenergy[j].toDouble()
            }
            pinfo.pe[gr_out][chn] = gfc.pe[chn].toDouble()
        }
    }

    private fun vbrpsy_compute_fft_s(gfp: LameGlobalFlags,
                                     buffer: Array<FloatArray>, bufPos: Int, chn: Int,
                                     sblock: Int, fftenergy_s: Array<FloatArray>,
                                     wsamp_s: Array<Array<FloatArray>>, wsamp_sPos: Int) {
        val gfc = gfp.internal_flags!!

        if (sblock == 0 && chn < 2) {
            fft.fft_short(gfc, wsamp_s[wsamp_sPos], chn, buffer, bufPos)
        }
        if (chn == 2) {
            /* FFT data for mid and side channel is derived from L & R */
            for (j in Encoder.BLKSIZE_s - 1 downTo 0) {
                val l = wsamp_s[wsamp_sPos + 0][sblock][j]
                val r = wsamp_s[wsamp_sPos + 1][sblock][j]
                wsamp_s[wsamp_sPos + 0][sblock][j] = (l + r) * Util.SQRT2 * 0.5f
                wsamp_s[wsamp_sPos + 1][sblock][j] = (l - r) * Util.SQRT2 * 0.5f
            }
        }

        /*********************************************************************
         * compute energies
         */
        fftenergy_s[sblock][0] = wsamp_s[wsamp_sPos + 0][sblock][0]
        fftenergy_s[sblock][0] *= fftenergy_s[sblock][0]
        for (j in Encoder.BLKSIZE_s / 2 - 1 downTo 0) {
            val re = wsamp_s[wsamp_sPos + 0][sblock][Encoder.BLKSIZE_s / 2 - j]
            val im = wsamp_s[wsamp_sPos + 0][sblock][Encoder.BLKSIZE_s / 2 + j]
            fftenergy_s[sblock][Encoder.BLKSIZE_s / 2 - j] = NON_LINEAR_SCALE_ENERGY((re * re + im * im) * 0.5f)
        }
    }

    /**
     * compute loudness approximation (used for ATH auto-level adjustment)
     */
    private fun vbrpsy_compute_loudness_approximation_l(gfp: LameGlobalFlags,
                                                        gr_out: Int, chn: Int, fftenergy: FloatArray) {
        val gfc = gfp.internal_flags
        if (gfp.athaa_loudapprox == 2 && chn < 2) {
            // no loudness for mid/side ch
            gfc!!.loudness_sq[gr_out][chn] = gfc.loudness_sq_save[chn]
            gfc.loudness_sq_save[chn] = psycho_loudness_approx(fftenergy, gfc)
        }
    }

    /**
     * Apply HPF of fs/4 to the input signal. This is used for attack detection
     * / handling.
     */
    private fun vbrpsy_attack_detection(gfp: LameGlobalFlags,
                                        buffer: Array<FloatArray>, bufPos: Int, gr_out: Int,
                                        masking_ratio: Array<Array<III_psy_ratio>>,
                                        masking_MS_ratio: Array<Array<III_psy_ratio>>,
                                        energy: FloatArray, sub_short_factor: Array<FloatArray>,
                                        ns_attacks: Array<IntArray>, uselongblock: IntArray) {
        val ns_hpfsmpl = Array(2) { FloatArray(576) }
        val gfc = gfp.internal_flags
        val n_chn_out = gfc!!.channels_out
        /* chn=2 and 3 = Mid and Side channels */
        val n_chn_psy = if (gfp.mode == MPEGMode.JOINT_STEREO) 4 else n_chn_out
        /* Don't copy the input buffer into a temporary buffer */
        /* unroll the loop 2 times */
        for (chn in 0 until n_chn_out) {
            /* apply high pass filter of fs/4 */
            val firbuf = buffer[chn]
            val firbufPos = bufPos + 576 - 350 - NSFIRLEN + 192
            assert(fircoef_.size == (NSFIRLEN - 1) / 2)
            for (i in 0..575) {
                var sum1: Float
                var sum2: Float
                sum1 = firbuf[firbufPos + i + 10]
                sum2 = 0.0f
                var j = 0
                while (j < (NSFIRLEN - 1) / 2 - 1) {
                    sum1 += fircoef_[j] * (firbuf[firbufPos + i + j] + firbuf[(firbufPos + i
                            + NSFIRLEN) - j])
                    sum2 += fircoef_[j + 1] * (firbuf[firbufPos + i + j + 1] + firbuf[(firbufPos
                            + i + NSFIRLEN) - j - 1])
                    j += 2
                }
                ns_hpfsmpl[chn][i] = sum1 + sum2
            }
            masking_ratio[gr_out][chn].en.assign(gfc.en[chn])
            masking_ratio[gr_out][chn].thm.assign(gfc.thm[chn])
            if (n_chn_psy > 2) {
                /* MS maskings */
                /* percep_MS_entropy [chn-2] = gfc . pe [chn]; */
                masking_MS_ratio[gr_out][chn].en.assign(gfc.en[chn + 2])
                masking_MS_ratio[gr_out][chn].thm.assign(gfc.thm[chn + 2])
            }
        }
        for (chn in 0 until n_chn_psy) {
            val attack_intensity = FloatArray(12)
            val en_subshort = FloatArray(12)
            val en_short = floatArrayOf(0f, 0f, 0f, 0f)
            val pf = ns_hpfsmpl[chn and 1]
            var pfPos = 0
            val attackThreshold = if (chn == 3)
                gfc.nsPsy.attackthre_s
            else
                gfc.nsPsy.attackthre
            var ns_uselongblock = 1

            if (chn == 2) {
                var i = 0
                var j = 576
                while (j > 0) {
                    val l = ns_hpfsmpl[0][i]
                    val r = ns_hpfsmpl[1][i]
                    ns_hpfsmpl[0][i] = l + r
                    ns_hpfsmpl[1][i] = l - r
                    ++i
                    --j
                }
            }
            /***************************************************************
             * determine the block type (window type)
             */
            /* calculate energies of each sub-shortblocks */
            for (i in 0..2) {
                en_subshort[i] = gfc.nsPsy.last_en_subshort[chn][i + 6]
                assert(gfc.nsPsy.last_en_subshort[chn][i + 4] > 0)
                attack_intensity[i] = en_subshort[i] / gfc.nsPsy.last_en_subshort[chn][i + 4]
                en_short[0] += en_subshort[i]
            }

            for (i in 0..8) {
                val pfe = pfPos + 576 / 9
                var p = 1f
                while (pfPos < pfe) {
                    if (p < Math.abs(pf[pfPos]))
                        p = Math.abs(pf[pfPos])
                    pfPos++
                }

                en_subshort[i + 3] = p
                gfc.nsPsy.last_en_subshort[chn][i] = en_subshort[i + 3]
                en_short[1 + i / 3] += p
                if (p > en_subshort[i + 3 - 2]) {
                    assert(en_subshort[i + 3 - 2] > 0)
                    p = p / en_subshort[i + 3 - 2]
                } else if (en_subshort[i + 3 - 2] > p * 10.0) {
                    assert(p > 0)
                    p = en_subshort[i + 3 - 2] / (p * 10.0f)
                } else {
                    p = 0.0f
                }
                attack_intensity[i + 3] = p
            }
            /* pulse like signal detection for fatboy.wav and so on */
            for (i in 0..2) {
                val enn = (en_subshort[i * 3 + 3]
                        + en_subshort[i * 3 + 4] + en_subshort[i * 3 + 5])
                var factor = 1f
                if (en_subshort[i * 3 + 5] * 6 < enn) {
                    factor *= 0.5f
                    if (en_subshort[i * 3 + 4] * 6 < enn) {
                        factor *= 0.5f
                    }
                }
                sub_short_factor[chn][i] = factor
            }

            if (gfp.analysis) {
                var x = attack_intensity[0]
                for (i in 1..11) {
                    if (x < attack_intensity[i]) {
                        x = attack_intensity[i]
                    }
                }
                val pinfo = gfc.pinfo!!
                pinfo.ers[gr_out][chn] = pinfo.ers_save[chn]
                pinfo.ers_save[chn] = x.toDouble()
            }

            /* compare energies between sub-shortblocks */
            for (i in 0..11) {
                if (0 == ns_attacks[chn][i / 3] && attack_intensity[i] > attackThreshold) {
                    ns_attacks[chn][i / 3] = i % 3 + 1
                }
            }

            /*
			 * should have energy change between short blocks, in order to avoid
			 * periodic signals
			 */
            /* Good samples to show the effect are Trumpet test songs */
            /*
			 * GB: tuned (1) to avoid too many short blocks for test sample
			 * TRUMPET
			 */
            /*
			 * RH: tuned (2) to let enough short blocks through for test sample
			 * FSOL and SNAPS
			 */
            for (i in 1..3) {
                val u = en_short[i - 1]
                val v = en_short[i]
                val m = Math.max(u, v)
                if (m < 40000) { /* (2) */
                    if (u < 1.7 * v && v < 1.7 * u) { /* (1) */
                        if (i == 1 && ns_attacks[chn][0] <= ns_attacks[chn][i]) {
                            ns_attacks[chn][0] = 0
                        }
                        ns_attacks[chn][i] = 0
                    }
                }
            }

            if (ns_attacks[chn][0] <= gfc.nsPsy.lastAttacks[chn]) {
                ns_attacks[chn][0] = 0
            }

            if (gfc.nsPsy.lastAttacks[chn] == 3 || (ns_attacks[chn][0] + ns_attacks[chn][1]
                            + ns_attacks[chn][2] + ns_attacks[chn][3]) != 0) {
                ns_uselongblock = 0

                if (ns_attacks[chn][1] != 0 && ns_attacks[chn][0] != 0) {
                    ns_attacks[chn][1] = 0
                }
                if (ns_attacks[chn][2] != 0 && ns_attacks[chn][1] != 0) {
                    ns_attacks[chn][2] = 0
                }
                if (ns_attacks[chn][3] != 0 && ns_attacks[chn][2] != 0) {
                    ns_attacks[chn][3] = 0
                }
            }
            if (chn < 2) {
                uselongblock[chn] = ns_uselongblock
            } else {
                if (ns_uselongblock == 0) {
                    uselongblock[1] = 0
                    uselongblock[0] = uselongblock[1]
                }
            }

            /*
			 * there is a one granule delay. Copy maskings computed last call
			 * into masking_ratio to return to calling program.
			 */
            energy[chn] = gfc.tot_ener[chn]
        }
    }

    private fun vbrpsy_skip_masking_s(gfc: LameInternalFlags,
                                      chn: Int, sblock: Int) {
        if (sblock == 0) {
            for (b in 0 until gfc.npart_s) {
                gfc.nb_s2[chn][b] = gfc.nb_s1[chn][b]
                gfc.nb_s1[chn][b] = 0f
            }
        }
    }

    private fun vbrpsy_skip_masking_l(gfc: LameInternalFlags,
                                      chn: Int) {
        for (b in 0 until gfc.npart_l) {
            gfc.nb_2[chn][b] = gfc.nb_1[chn][b]
            gfc.nb_1[chn][b] = 0f
        }
    }

    private fun psyvbr_calc_mask_index_s(gfc: LameInternalFlags,
                                         max: FloatArray, avg: FloatArray, mask_idx: IntArray) {
        val last_tab_entry = tab.size - 1
        var b = 0
        var a = avg[b] + avg[b + 1]
        assert(a >= 0)
        if (a > 0.0) {
            var m = max[b]
            if (m < max[b + 1])
                m = max[b + 1]
            assert(gfc.numlines_s[b] + gfc.numlines_s[b + 1] - 1 > 0)
            a = 20.0f * (m * 2.0f - a) / (a * (gfc.numlines_s[b] + gfc.numlines_s[b + 1] - 1))
            var k = a.toInt()
            if (k > last_tab_entry)
                k = last_tab_entry
            mask_idx[b] = k
        } else {
            mask_idx[b] = 0
        }

        b = 1
        while (b < gfc.npart_s - 1) {
            a = avg[b - 1] + avg[b] + avg[b + 1]
            assert(b + 1 < gfc.npart_s)
            assert(a >= 0)
            if (a > 0.0) {
                var m = max[b - 1]
                if (m < max[b])
                    m = max[b]
                if (m < max[b + 1])
                    m = max[b + 1]
                assert((gfc.numlines_s[b - 1] + gfc.numlines_s[b]
                        + gfc.numlines_s[b + 1]) - 1 > 0)
                a = 20.0f * (m * 3.0f - a) / (a * ((gfc.numlines_s[b - 1] + gfc.numlines_s[b]
                        + gfc.numlines_s[b + 1]) - 1))
                var k = a.toInt()
                if (k > last_tab_entry)
                    k = last_tab_entry
                mask_idx[b] = k
            } else {
                mask_idx[b] = 0
            }
            b++
        }
        assert(b > 0)
        assert(b == gfc.npart_s - 1)

        a = avg[b - 1] + avg[b]
        assert(a >= 0)
        if (a > 0.0) {
            var m = max[b - 1]
            if (m < max[b])
                m = max[b]
            assert(gfc.numlines_s[b - 1] + gfc.numlines_s[b] - 1 > 0)
            a = 20.0f * (m * 2.0f - a) / (a * (gfc.numlines_s[b - 1] + gfc.numlines_s[b] - 1))
            var k = a.toInt()
            if (k > last_tab_entry)
                k = last_tab_entry
            mask_idx[b] = k
        } else {
            mask_idx[b] = 0
        }
        assert(b == gfc.npart_s - 1)
    }

    private fun vbrpsy_compute_masking_s(gfp: LameGlobalFlags,
                                         fftenergy_s: Array<FloatArray>, eb: FloatArray, thr: FloatArray,
                                         chn: Int, sblock: Int) {
        val gfc = gfp.internal_flags
        val max = FloatArray(Encoder.CBANDS)
        val avg = FloatArray(Encoder.CBANDS)
        var i: Int
        var j: Int
        var b: Int
        val mask_idx_s = IntArray(Encoder.CBANDS)

        j = 0
        b = j
        while (b < gfc!!.npart_s) {
            var ebb = 0f
            var m = 0f
            val n = gfc.numlines_s[b]
            i = 0
            while (i < n) {
                val el = fftenergy_s[sblock][j]
                ebb += el
                if (m < el)
                    m = el
                ++i
                ++j
            }
            eb[b] = ebb
            assert(ebb >= 0)
            max[b] = m
            assert(n > 0)
            avg[b] = ebb / n
            assert(avg[b] >= 0)
            ++b
        }
        assert(b == gfc.npart_s)
        assert(j == 129)
        while (b < Encoder.CBANDS) {
            max[b] = 0f
            avg[b] = 0f
            ++b
        }
        psyvbr_calc_mask_index_s(gfc, max, avg, mask_idx_s)
        b = 0
        j = b
        val s3_ss = gfc.s3_ss!!
        while (b < gfc.npart_s) {
            var kk = gfc.s3ind_s[b][0]
            val last = gfc.s3ind_s[b][1]
            var dd: Int
            var dd_n: Int
            var x: Float
            var ecb: Float
            val avg_mask: Float
            dd = mask_idx_s[kk]
            dd_n = 1
            ecb = s3_ss[j] * eb[kk] * tab[mask_idx_s[kk]]
            ++j
            ++kk
            while (kk <= last) {
                dd += mask_idx_s[kk]
                dd_n += 1
                x = s3_ss[j] * eb[kk] * tab[mask_idx_s[kk]]
                ecb = vbrpsy_mask_add(ecb, x, kk - b)
                ++j
                ++kk
            }
            dd = (1 + 2 * dd) / (2 * dd_n)
            avg_mask = tab[dd] * 0.5f
            ecb *= avg_mask
            thr[b] = ecb
            gfc.nb_s2[chn][b] = gfc.nb_s1[chn][b]
            gfc.nb_s1[chn][b] = ecb
            run {
                /*
				 * if THR exceeds EB, the quantization routines will take the
				 * difference from other bands. in case of strong tonal samples
				 * (tonaltest.wav) this leads to heavy distortions. that's why
				 * we limit THR here.
				 */
                x = max[b]
                x *= gfc.minval_s[b]
                x *= avg_mask
                if (thr[b] > x) {
                    thr[b] = x
                }
            }
            if (gfc.masking_lower > 1) {
                thr[b] *= gfc.masking_lower
            }
            if (thr[b] > eb[b]) {
                thr[b] = eb[b]
            }
            if (gfc.masking_lower < 1) {
                thr[b] *= gfc.masking_lower
            }

            assert(thr[b] >= 0)
            b++
        }
        while (b < Encoder.CBANDS) {
            eb[b] = 0f
            thr[b] = 0f
            ++b
        }
    }

    private fun vbrpsy_compute_masking_l(gfc: LameInternalFlags,
                                         fftenergy: FloatArray, eb_l: FloatArray, thr: FloatArray,
                                         chn: Int) {
        val max = FloatArray(Encoder.CBANDS)
        val avg = FloatArray(Encoder.CBANDS)
        val mask_idx_l = IntArray(Encoder.CBANDS + 2)
        var b: Int

        /*********************************************************************
         * Calculate the energy and the tonality of each partition.
         */
        calc_energy(gfc, fftenergy, eb_l, max, avg)
        calc_mask_index_l(gfc, max, avg, mask_idx_l)

        /*********************************************************************
         * convolve the partitioned energy and unpredictability with the
         * spreading function, s3_l[b][k]
         */
        var k = 0
        b = 0
        val s3_ll = gfc.s3_ll!!
        while (b < gfc.npart_l) {
            var x: Float
            var ecb: Float
            val avg_mask: Float
            var t: Float
            /* convolve the partitioned energy with the spreading function */
            var kk = gfc.s3ind[b][0]
            val last = gfc.s3ind[b][1]
            var dd = 0
            var dd_n = 0
            dd = mask_idx_l[kk]
            dd_n += 1
            ecb = s3_ll[k] * eb_l[kk] * tab[mask_idx_l[kk]]
            ++k
            ++kk
            while (kk <= last) {
                dd += mask_idx_l[kk]
                dd_n += 1
                x = s3_ll[k] * eb_l[kk] * tab[mask_idx_l[kk]]
                t = vbrpsy_mask_add(ecb, x, kk - b)
                ecb = t
                ++k
                ++kk
            }
            dd = (1 + 2 * dd) / (2 * dd_n)
            avg_mask = tab[dd] * 0.5f
            ecb *= avg_mask

            /**** long block pre-echo control  */
            /**
             * <PRE>
             * dont use long block pre-echo control if previous granule was
             * a short block.  This is to avoid the situation:
             * frame0:  quiet (very low masking)
             * frame1:  surge  (triggers short blocks)
             * frame2:  regular frame.  looks like pre-echo when compared to
             * frame0, but all pre-echo was in frame1.
            </PRE> *
             */
            /*
			 * chn=0,1 L and R channels chn=2,3 S and M channels.
			 */
            if (gfc.blocktype_old[chn and 0x01] == Encoder.SHORT_TYPE) {
                val ecb_limit = rpelev * gfc.nb_1[chn][b]
                if (ecb_limit > 0) {
                    thr[b] = Math.min(ecb, ecb_limit)
                } else {
                    /**
                     * <PRE>
                     * Robert 071209:
                     * Because we don't calculate long block psy when we know a granule
                     * should be of short blocks, we don't have any clue how the granule
                     * before would have looked like as a long block. So we have to guess
                     * a little bit for this END_TYPE block.
                     * Most of the time we get away with this sloppyness. (fingers crossed :)
                     * The speed increase is worth it.
                    </PRE> *
                     */
                    thr[b] = Math.min(ecb, eb_l[b] * NS_PREECHO_ATT2)
                }
            } else {
                var ecb_limit_2 = rpelev2 * gfc.nb_2[chn][b]
                var ecb_limit_1 = rpelev * gfc.nb_1[chn][b]
                val ecb_limit: Float
                if (ecb_limit_2 <= 0) {
                    ecb_limit_2 = ecb
                }
                if (ecb_limit_1 <= 0) {
                    ecb_limit_1 = ecb
                }
                if (gfc.blocktype_old[chn and 0x01] == Encoder.NORM_TYPE) {
                    ecb_limit = Math.min(ecb_limit_1, ecb_limit_2)
                } else {
                    ecb_limit = ecb_limit_1
                }
                thr[b] = Math.min(ecb, ecb_limit)
            }
            gfc.nb_2[chn][b] = gfc.nb_1[chn][b]
            gfc.nb_1[chn][b] = ecb
            run {
                /*
				 * if THR exceeds EB, the quantization routines will take the
				 * difference from other bands. in case of strong tonal samples
				 * (tonaltest.wav) this leads to heavy distortions. that's why
				 * we limit THR here.
				 */
                x = max[b]
                x *= gfc.minval_l[b]
                x *= avg_mask
                if (thr[b] > x) {
                    thr[b] = x
                }
            }
            if (gfc.masking_lower > 1) {
                thr[b] *= gfc.masking_lower
            }
            if (thr[b] > eb_l[b]) {
                thr[b] = eb_l[b]
            }
            if (gfc.masking_lower < 1) {
                thr[b] *= gfc.masking_lower
            }
            assert(thr[b] >= 0)
            b++
        }
        while (b < Encoder.CBANDS) {
            eb_l[b] = 0f
            thr[b] = 0f
            ++b
        }
    }

    private fun vbrpsy_compute_block_type(gfp: LameGlobalFlags,
                                          uselongblock: IntArray) {
        val gfc = gfp.internal_flags

        if (gfp.short_blocks == ShortBlock.short_block_coupled
                /* force both channels to use the same block type */
                /* this is necessary if the frame is to be encoded in ms_stereo. */
                /* But even without ms_stereo, FhG does this */
                && !(uselongblock[0] != 0 && uselongblock[1] != 0)) {
            uselongblock[1] = 0
            uselongblock[0] = uselongblock[1]
        }

        for (chn in 0 until gfc!!.channels_out) {
            /* disable short blocks */
            if (gfp.short_blocks == ShortBlock.short_block_dispensed) {
                uselongblock[chn] = 1
            }
            if (gfp.short_blocks == ShortBlock.short_block_forced) {
                uselongblock[chn] = 0
            }
        }
    }

    private fun vbrpsy_apply_block_type(gfp: LameGlobalFlags,
                                        uselongblock: IntArray, blocktype_d: IntArray) {
        val gfc = gfp.internal_flags

        /*
		 * update the blocktype of the previous granule, since it depends on
		 * what happend in this granule
		 */
        for (chn in 0 until gfc!!.channels_out) {
            var blocktype = Encoder.NORM_TYPE
            /* disable short blocks */

            if (uselongblock[chn] != 0) {
                /* no attack : use long blocks */
                assert(gfc.blocktype_old[chn] != Encoder.START_TYPE)
                if (gfc.blocktype_old[chn] == Encoder.SHORT_TYPE)
                    blocktype = Encoder.STOP_TYPE
            } else {
                /* attack : use short blocks */
                blocktype = Encoder.SHORT_TYPE
                if (gfc.blocktype_old[chn] == Encoder.NORM_TYPE) {
                    gfc.blocktype_old[chn] = Encoder.START_TYPE
                }
                if (gfc.blocktype_old[chn] == Encoder.STOP_TYPE)
                    gfc.blocktype_old[chn] = Encoder.SHORT_TYPE
            }

            blocktype_d[chn] = gfc.blocktype_old[chn]
            // value returned to calling program
            gfc.blocktype_old[chn] = blocktype
            // save for next call to l3psy_anal
        }
    }

    /**
     * compute M/S thresholds from Johnston & Ferreira 1992 ICASSP paper
     */
    private fun vbrpsy_compute_MS_thresholds(eb: Array<FloatArray>,
                                             thr: Array<FloatArray>, cb_mld: FloatArray, ath_cb: FloatArray,
                                             athadjust: Float, msfix: Float, n: Int) {
        val msfix2 = msfix * 2
        val athlower = if (msfix > 0) Math.pow(10.0, athadjust.toDouble()).toFloat() else 1f
        var rside: Float
        var rmid: Float
        for (b in 0 until n) {
            val ebM = eb[2][b]
            val ebS = eb[3][b]
            val thmL = thr[0][b]
            val thmR = thr[1][b]
            var thmM = thr[2][b]
            var thmS = thr[3][b]

            /* use this fix if L & R masking differs by 2db or less */
            if (thmL <= 1.58 * thmR && thmR <= 1.58 * thmL) {
                val mld_m = cb_mld[b] * ebS
                val mld_s = cb_mld[b] * ebM
                rmid = Math.max(thmM, Math.min(thmS, mld_m))
                rside = Math.max(thmS, Math.min(thmM, mld_s))
            } else {
                rmid = thmM
                rside = thmS
            }
            if (msfix > 0) {
                /** */
                /* Adjust M/S maskings if user set "msfix" */
                /** */
                /* Naoki Shibata 2000 */
                val thmLR: Float
                val thmMS: Float
                val ath = ath_cb[b] * athlower
                thmLR = Math.min(Math.max(thmL, ath), Math.max(thmR, ath))
                thmM = Math.max(rmid, ath)
                thmS = Math.max(rside, ath)
                thmMS = thmM + thmS
                if (thmMS > 0 && thmLR * msfix2 < thmMS) {
                    val f = thmLR * msfix2 / thmMS
                    thmM *= f
                    thmS *= f
                    assert(thmMS > 0)
                }
                rmid = Math.min(thmM, rmid)
                rside = Math.min(thmS, rside)
            }
            if (rmid > ebM) {
                rmid = ebM
            }
            if (rside > ebS) {
                rside = ebS
            }
            thr[2][b] = rmid
            thr[3][b] = rside
        }
    }

    fun L3psycho_anal_vbr(gfp: LameGlobalFlags,
                          buffer: Array<FloatArray>, bufPos: Int, gr_out: Int,
                          masking_ratio: Array<Array<III_psy_ratio>>,
                          masking_MS_ratio: Array<Array<III_psy_ratio>>,
                          percep_entropy: FloatArray, percep_MS_entropy: FloatArray,
                          energy: FloatArray, blocktype_d: IntArray): Int {
        val gfc = gfp.internal_flags!!

        /* fft and energy calculation */
        var wsamp_l: Array<FloatArray>
        var wsamp_s: Array<Array<FloatArray>>
        val fftenergy = FloatArray(Encoder.HBLKSIZE)
        val fftenergy_s = Array(3) { FloatArray(Encoder.HBLKSIZE_s) }
        val wsamp_L = Array(2) { FloatArray(Encoder.BLKSIZE) }
        val wsamp_S = Array(2) { Array(3) { FloatArray(Encoder.BLKSIZE_s) } }
        val eb = Array(4) { FloatArray(Encoder.CBANDS) }
        val thr = Array(4) { FloatArray(Encoder.CBANDS) }

        val sub_short_factor = Array(4) { FloatArray(3) }
        val pcfact = 0.6f

        /* block type */
        val ns_attacks = arrayOf(intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0))
        val uselongblock = IntArray(2)

        /* usual variables like loop indices, etc.. */

        /* chn=2 and 3 = Mid and Side channels */
        val n_chn_psy = if (gfp.mode == MPEGMode.JOINT_STEREO)
            4
        else
            gfc!!.channels_out

        vbrpsy_attack_detection(gfp, buffer, bufPos, gr_out, masking_ratio,
                masking_MS_ratio, energy, sub_short_factor, ns_attacks,
                uselongblock)

        vbrpsy_compute_block_type(gfp, uselongblock)

        /* LONG BLOCK CASE */
        val ath = gfc.ATH!!
        run {
            for (chn in 0 until n_chn_psy) {
                val ch01 = chn and 0x01
                wsamp_l = wsamp_L
                vbrpsy_compute_fft_l(gfp, buffer, bufPos, chn, gr_out,
                        fftenergy, wsamp_l, ch01)

                vbrpsy_compute_loudness_approximation_l(gfp, gr_out, chn,
                        fftenergy)

                if (uselongblock[ch01] != 0) {
                    vbrpsy_compute_masking_l(gfc, fftenergy, eb[chn], thr[chn],
                            chn)
                } else {
                    vbrpsy_skip_masking_l(gfc!!, chn)
                }
            }
            if (uselongblock[0] + uselongblock[1] == 2) {
                /* M/S channel */
                if (gfp.mode == MPEGMode.JOINT_STEREO) {
                    vbrpsy_compute_MS_thresholds(eb, thr, gfc!!.mld_cb_l,
                            ath.cb_l, gfp.ATHlower * ath.adjust,
                            gfp.msfix, gfc.npart_l)
                }
            }
            /* TODO: apply adaptive ATH masking here ?? */
            for (chn in 0 until n_chn_psy) {
                val ch01 = chn and 0x01
                if (uselongblock[ch01] != 0) {
                    convert_partition2scalefac_l(gfc, eb[chn], thr[chn], chn)
                }
            }
        }

        /* SHORT BLOCKS CASE */
        run {
            for (sblock in 0..2) {
                for (chn in 0 until n_chn_psy) {
                    val ch01 = chn and 0x01

                    if (uselongblock[ch01] != 0) {
                        vbrpsy_skip_masking_s(gfc, chn, sblock)
                    } else {
                        /* compute masking thresholds for short blocks */
                        wsamp_s = wsamp_S
                        vbrpsy_compute_fft_s(gfp, buffer, bufPos, chn, sblock,
                                fftenergy_s, wsamp_s, ch01)
                        vbrpsy_compute_masking_s(gfp, fftenergy_s, eb[chn],
                                thr[chn], chn, sblock)
                    }
                }
                if (uselongblock[0] + uselongblock[1] == 0) {
                    /* M/S channel */
                    if (gfp.mode == MPEGMode.JOINT_STEREO) {
                        vbrpsy_compute_MS_thresholds(eb, thr, gfc.mld_cb_s,
                                ath.cb_s, gfp.ATHlower * ath.adjust,
                                gfp.msfix, gfc.npart_s)
                    }
                    /* L/R channel */
                }
                /* TODO: apply adaptive ATH masking here ?? */
                for (chn in 0 until n_chn_psy) {
                    val ch01 = chn and 0x01
                    if (0 == uselongblock[ch01]) {
                        convert_partition2scalefac_s(gfc, eb[chn], thr[chn],
                                chn, sblock)
                    }
                }
            }

            /**** short block pre-echo control  */
            for (chn in 0 until n_chn_psy) {
                val ch01 = chn and 0x01

                if (uselongblock[ch01] != 0) {
                    continue
                }
                for (sb in 0 until Encoder.SBMAX_s) {
                    val new_thmm = FloatArray(3)
                    for (sblock in 0..2) {
                        var thmm = gfc!!.thm[chn].s[sb][sblock]
                        thmm *= NS_PREECHO_ATT0

                        if (ns_attacks[chn][sblock] >= 2 || ns_attacks[chn][sblock + 1] == 1) {
                            val idx = if (sblock != 0) sblock - 1 else 2
                            val p = NS_INTERP(gfc.thm[chn].s[sb][idx], thmm,
                                    NS_PREECHO_ATT1 * pcfact).toDouble()
                            thmm = Math.min(thmm.toDouble(), p).toFloat()
                        } else if (ns_attacks[chn][sblock] == 1) {
                            val idx = if (sblock != 0) sblock - 1 else 2
                            val p = NS_INTERP(gfc.thm[chn].s[sb][idx], thmm,
                                    NS_PREECHO_ATT2 * pcfact).toDouble()
                            thmm = Math.min(thmm.toDouble(), p).toFloat()
                        } else if (sblock != 0 && ns_attacks[chn][sblock - 1] == 3 || sblock == 0 && gfc.nsPsy.lastAttacks[chn] == 3) {
                            val idx = if (sblock != 2) sblock + 1 else 0
                            val p = NS_INTERP(gfc.thm[chn].s[sb][idx], thmm,
                                    NS_PREECHO_ATT2 * pcfact).toDouble()
                            thmm = Math.min(thmm.toDouble(), p).toFloat()
                        }

                        /* pulse like signal detection for fatboy.wav and so on */
                        thmm *= sub_short_factor[chn][sblock]

                        new_thmm[sblock] = thmm
                    }
                    for (sblock in 0..2) {
                        gfc!!.thm[chn].s[sb][sblock] = new_thmm[sblock]
                    }
                }
            }
        }
        for (chn in 0 until n_chn_psy) {
            gfc!!.nsPsy.lastAttacks[chn] = ns_attacks[chn][2]
        }

        /***************************************************************
         * determine final block type
         */
        vbrpsy_apply_block_type(gfp, uselongblock, blocktype_d)

        /*********************************************************************
         * compute the value of PE to return ... no delay and advance
         */
        for (chn in 0 until n_chn_psy) {
            val ppe: FloatArray
            val ppePos: Int
            var type: Int
            val mr: III_psy_ratio

            if (chn > 1) {
                ppe = percep_MS_entropy
                ppePos = -2
                type = Encoder.NORM_TYPE
                if (blocktype_d[0] == Encoder.SHORT_TYPE || blocktype_d[1] == Encoder.SHORT_TYPE)
                    type = Encoder.SHORT_TYPE
                mr = masking_MS_ratio[gr_out][chn - 2]
            } else {
                ppe = percep_entropy
                ppePos = 0
                type = blocktype_d[chn]
                mr = masking_ratio[gr_out][chn]
            }

            if (type == Encoder.SHORT_TYPE) {
                ppe[ppePos + chn] = pecalc_s(mr, gfc!!.masking_lower)
            } else {
                ppe[ppePos + chn] = pecalc_l(mr, gfc!!.masking_lower)
            }

            if (gfp.analysis) {
                gfc.pinfo!!.pe[gr_out][chn] = ppe[ppePos + chn].toDouble()
            }
        }
        return 0
    }

    private fun s3_func_x(bark: Float, hf_slope: Float): Float {
        val tempy: Float

        if (bark >= 0) {
            tempy = -bark * 27
        } else {
            tempy = bark * hf_slope
        }
        return if (tempy <= -72.0) {
            0f
        } else Math.exp((tempy * LN_TO_LOG10).toDouble()).toFloat()
    }

    private fun norm_s3_func_x(hf_slope: Float): Float {
        var lim_a = 0.0
        var lim_b = 0.0
        run {
            var x = 0.0
            var l: Double
            var h: Double
            x = 0.0
            while (s3_func_x(x.toFloat(), hf_slope) > 1e-20) {
                x -= 1.0
            }
            l = x
            h = 0.0
            while (Math.abs(h - l) > 1e-12) {
                x = (h + l) / 2
                if (s3_func_x(x.toFloat(), hf_slope) > 0) {
                    h = x
                } else {
                    l = x
                }
            }
            lim_a = l
        }
        run {
            var x = 0.0
            var l: Double
            var h: Double
            x = 0.0
            while (s3_func_x(x.toFloat(), hf_slope) > 1e-20) {
                x += 1.0
            }
            l = 0.0
            h = x
            while (Math.abs(h - l) > 1e-12) {
                x = (h + l) / 2
                if (s3_func_x(x.toFloat(), hf_slope) > 0) {
                    l = x
                } else {
                    h = x
                }
            }
            lim_b = h
        }
        run {
            var sum = 0.0
            val m = 1000
            var i: Int
            i = 0
            while (i <= m) {
                val x = lim_a + i * (lim_b - lim_a) / m
                val y = s3_func_x(x.toFloat(), hf_slope).toDouble()
                sum += y
                ++i
            }
            run {
                val norm = (m + 1) / (sum * (lim_b - lim_a))
                /* printf( "norm = %lf\n",norm); */
                return norm.toFloat()
            }
        }
    }

    /**
     * The spreading function.  Values returned in units of energy
     */
    private fun s3_func(bark: Float): Float {
        var tempx: Float
        val x: Float
        val tempy: Float
        val temp: Float
        tempx = bark
        if (tempx >= 0)
            tempx *= 3f
        else
            tempx *= 1.5f

        if (tempx >= 0.5 && tempx <= 2.5) {
            temp = tempx - 0.5f
            x = 8.0f * (temp * temp - 2.0f * temp)
        } else
            x = 0.0f
        tempx += 0.474f
        tempy = 15.811389f + 7.5f * tempx - 17.5f * Math.sqrt(1.0 + tempx * tempx).toFloat()

        if (tempy <= -60.0)
            return 0.0f

        tempx = Math.exp(((x + tempy) * LN_TO_LOG10).toDouble()).toFloat()

        /**
         * <PRE>
         * Normalization.  The spreading function should be normalized so that:
         * +inf
         * /
         * |  s3 [ bark ]  d(bark)   =  1
         * /
         * -inf
        </PRE> *
         */
        tempx /= .6609193f
        return tempx
    }

    /**
     * see for example "Zwicker: Psychoakustik, 1982; ISBN 3-540-11401-7
     */
    private fun freq2bark(freq: Float): Float {
        var freq = freq
        /* input: freq in hz output: barks */
        if (freq < 0)
            freq = 0f
        freq = freq * 0.001f
        return 13.0f * Math.atan(.76 * freq).toFloat() + 3.5f * Math.atan(freq * freq / (7.5 * 7.5)).toFloat()
    }

    private fun init_numline(numlines: IntArray, bo: IntArray,
                             bm: IntArray, bval: FloatArray, bval_width: FloatArray,
                             mld: FloatArray, bo_w: FloatArray, sfreq: Float,
                             blksize: Int, scalepos: IntArray, deltafreq: Float,
                             sbmax: Int): Int {
        var sfreq = sfreq
        val b_frq = FloatArray(Encoder.CBANDS + 1)
        val sample_freq_frac = sfreq / if (sbmax > 15) 2 * 576 else 2 * 192
        val partition = IntArray(Encoder.HBLKSIZE)
        var i: Int
        sfreq /= blksize.toFloat()
        var j = 0
        var ni = 0
        /* compute numlines, the number of spectral lines in each partition band */
        /* each partition band should be about DELBARK wide. */
        i = 0
        while (i < Encoder.CBANDS) {
            val bark1: Float
            var j2: Int
            bark1 = freq2bark(sfreq * j)

            b_frq[i] = sfreq * j

            j2 = j
            while (freq2bark(sfreq * j2) - bark1 < DELBARK && j2 <= blksize / 2) {
                j2++
            }

            numlines[i] = j2 - j
            ni = i + 1

            while (j < j2) {
                assert(j < Encoder.HBLKSIZE)
                partition[j++] = i
            }
            if (j > blksize / 2) {
                j = blksize / 2
                ++i
                break
            }
            i++
        }
        assert(i < Encoder.CBANDS)
        b_frq[i] = sfreq * j

        for (sfb in 0 until sbmax) {
            var i1: Int
            var i2: Int
            val start: Int
            val end: Int
            var arg: Float
            start = scalepos[sfb]
            end = scalepos[sfb + 1]

            i1 = Math.floor(.5 + deltafreq * (start - .5)).toInt()
            if (i1 < 0)
                i1 = 0
            i2 = Math.floor(.5 + deltafreq * (end - .5)).toInt()

            if (i2 > blksize / 2)
                i2 = blksize / 2

            bm[sfb] = (partition[i1] + partition[i2]) / 2
            bo[sfb] = partition[i2]

            val f_tmp = sample_freq_frac * end
            /*
			 * calculate how much of this band belongs to current scalefactor
			 * band
			 */
            bo_w[sfb] = (f_tmp - b_frq[bo[sfb]]) / (b_frq[bo[sfb] + 1] - b_frq[bo[sfb]])
            if (bo_w[sfb] < 0) {
                bo_w[sfb] = 0f
            } else {
                if (bo_w[sfb] > 1) {
                    bo_w[sfb] = 1f
                }
            }
            /* setup stereo demasking thresholds */
            /* formula reverse enginerred from plot in paper */
            arg = freq2bark(sfreq * scalepos[sfb].toFloat() * deltafreq)
            arg = Math.min(arg.toDouble(), 15.5).toFloat() / 15.5f

            mld[sfb] = Math.pow(10.0,
                    1.25 * (1 - Math.cos(Math.PI * arg)) - 2.5).toFloat()
        }

        /* compute bark values of each critical band */
        j = 0
        for (k in 0 until ni) {
            val w = numlines[k]
            var bark1: Float
            var bark2: Float

            bark1 = freq2bark(sfreq * j)
            bark2 = freq2bark(sfreq * (j + w - 1))
            bval[k] = .5f * (bark1 + bark2)

            bark1 = freq2bark(sfreq * (j - .5f))
            bark2 = freq2bark(sfreq * (j + w - .5f))
            bval_width[k] = bark2 - bark1
            j += w
        }

        return ni
    }

    private fun init_s3_values(s3ind: Array<IntArray>, npart: Int,
                               bval: FloatArray, bval_width: FloatArray, norm: FloatArray,
                               use_old_s3: Boolean): FloatArray {
        val s3 = Array(Encoder.CBANDS) { FloatArray(Encoder.CBANDS) }
        /*
		 * The s3 array is not linear in the bark scale.
		 *
		 * bval[x] should be used to get the bark value.
		 */
        var j: Int
        var numberOfNoneZero = 0

        /**
         * <PRE>
         * s[i][j], the value of the spreading function,
         * centered at band j (masker), for band i (maskee)
         *
         * i.e.: sum over j to spread into signal barkval=i
         * NOTE: i and j are used opposite as in the ISO docs
        </PRE> *
         */
        if (use_old_s3) {
            for (i in 0 until npart) {
                j = 0
                while (j < npart) {
                    val v = s3_func(bval[i] - bval[j]) * bval_width[j]
                    s3[i][j] = v * norm[i]
                    j++
                }
            }
        } else {
            j = 0
            while (j < npart) {
                val hf_slope = 15 + Math.min(21 / bval[j], 12f)
                val s3_x_norm = norm_s3_func_x(hf_slope)
                for (i in 0 until npart) {
                    val v = (s3_x_norm
                            * s3_func_x(bval[i] - bval[j], hf_slope)
                            * bval_width[j])
                    s3[i][j] = v * norm[i]
                }
                j++
            }
        }
        for (i in 0 until npart) {
            j = 0
            while (j < npart) {
                if (s3[i][j] > 0.0f)
                    break
                j++
            }
            s3ind[i][0] = j

            j = npart - 1
            while (j > 0) {
                if (s3[i][j] > 0.0f)
                    break
                j--
            }
            s3ind[i][1] = j
            numberOfNoneZero += s3ind[i][1] - s3ind[i][0] + 1
        }

        val p = FloatArray(numberOfNoneZero)

        var k = 0
        for (i in 0 until npart) {
            j = s3ind[i][0]
            while (j <= s3ind[i][1]) {
                p[k++] = s3[i][j]
                j++
            }
        }

        return p
    }

    private fun stereo_demask(f: Double): Float {
        /* setup stereo demasking thresholds */
        /* formula reverse enginerred from plot in paper */
        var arg = freq2bark(f.toFloat()).toDouble()
        arg = Math.min(arg, 15.5) / 15.5

        return Math.pow(10.0,
                1.25 * (1 - Math.cos(Math.PI * arg)) - 2.5).toFloat()
    }

    /**
     * NOTE: the bitrate reduction from the inter-channel masking effect is low
     * compared to the chance of getting annyoing artefacts. L3psycho_anal_vbr
     * does not use this feature. (Robert 071216)
     */
    fun psymodel_init(gfp: LameGlobalFlags): Int {
        val gfc = gfp.internal_flags
        var i: Int
        var useOldS3 = true
        var bvl_a = 13f
        val bvl_b = 24f
        var snr_l_a = 0f
        var snr_l_b = 0f
        var snr_s_a = -8.25f
        var snr_s_b = -4.5f

        val bval = FloatArray(Encoder.CBANDS)
        val bval_width = FloatArray(Encoder.CBANDS)
        val norm = FloatArray(Encoder.CBANDS)
        val sfreq = gfp.out_samplerate.toFloat()

        when (gfp.experimentalZ) {
            0 -> useOldS3 = true
            1 -> useOldS3 = if (gfp.VBR == VbrMode.vbr_mtrh || gfp.VBR == VbrMode.vbr_mt)
                false
            else
                true
            2 -> useOldS3 = false
            3 -> {
                bvl_a = 8f
                snr_l_a = -1.75f
                snr_l_b = -0.0125f
                snr_s_a = -8.25f
                snr_s_b = -2.25f
            }
            else -> useOldS3 = true
        }
        gfc!!.ms_ener_ratio_old = .25f
        gfc.blocktype_old[1] = Encoder.NORM_TYPE
        gfc.blocktype_old[0] = gfc.blocktype_old[1]
        // the vbr header is long blocks

        i = 0
        while (i < 4) {
            for (j in 0 until Encoder.CBANDS) {
                gfc.nb_1[i][j] = 1e20f
                gfc.nb_2[i][j] = 1e20f
                gfc.nb_s2[i][j] = 1.0f
                gfc.nb_s1[i][j] = gfc.nb_s2[i][j]
            }
            for (sb in 0 until Encoder.SBMAX_l) {
                gfc.en[i].l[sb] = 1e20f
                gfc.thm[i].l[sb] = 1e20f
            }
            for (j in 0..2) {
                for (sb in 0 until Encoder.SBMAX_s) {
                    gfc.en[i].s[sb][j] = 1e20f
                    gfc.thm[i].s[sb][j] = 1e20f
                }
                gfc.nsPsy.lastAttacks[i] = 0
            }
            for (j in 0..8)
                gfc.nsPsy.last_en_subshort[i][j] = 10f
            ++i
        }

        /* init. for loudness approx. -jd 2001 mar 27 */
        gfc.loudness_sq_save[1] = 0.0f
        gfc.loudness_sq_save[0] = gfc.loudness_sq_save[1]

        /*************************************************************************
         * now compute the psychoacoustic model specific constants
         */
        /* compute numlines, bo, bm, bval, bval_width, mld */

        val psy = gfc.PSY!!
        gfc.npart_l = init_numline(gfc.numlines_l, gfc.bo_l, gfc.bm_l, bval,
                bval_width, gfc.mld_l, psy!!.bo_l_weight, sfreq,
                Encoder.BLKSIZE, gfc.scalefac_band.l, Encoder.BLKSIZE / (2.0f * 576f), Encoder.SBMAX_l)
        assert(gfc.npart_l < Encoder.CBANDS)
        /* compute the spreading function */
        i = 0
        while (i < gfc.npart_l) {
            var snr = snr_l_a.toDouble()
            if (bval[i] >= bvl_a) {
                snr = (snr_l_b * (bval[i] - bvl_a) / (bvl_b - bvl_a) + snr_l_a * (bvl_b - bval[i]) / (bvl_b - bvl_a)).toDouble()
            }
            norm[i] = Math.pow(10.0, snr / 10.0).toFloat()
            if (gfc.numlines_l[i] > 0) {
                gfc.rnumlines_l[i] = 1.0f / gfc.numlines_l[i]
            } else {
                gfc.rnumlines_l[i] = 0f
            }
            i++
        }
        gfc.s3_ll = init_s3_values(gfc.s3ind, gfc.npart_l, bval, bval_width,
                norm, useOldS3)

        /* compute long block specific values, ATH and MINVAL */
        var j = 0
        i = 0
        val ath = gfc.ATH!!
        while (i < gfc.npart_l) {
            var x: Double

            /* ATH */
            x = Float.MAX_VALUE.toDouble()
            var k = 0
            while (k < gfc.numlines_l[i]) {
                val freq = sfreq * j / (1000.0f * Encoder.BLKSIZE)
                var level: Float
                /*
				 * ATH below 100 Hz constant, not further climbing
				 */
                level = ATHformula(freq * 1000, gfp) - 20
                // scale to FFT units; returned value is in dB
                level = Math.pow(10.0, 0.1 * level).toFloat()
                // convert from dB . energy
                level *= gfc.numlines_l[i].toFloat()
                if (x > level)
                    x = level.toDouble()
                k++
                j++
            }
            ath.cb_l[i] = x.toFloat()

            /*
			 * MINVAL. For low freq, the strength of the masking is limited by
			 * minval this is an ISO MPEG1 thing, dont know if it is really
			 * needed
			 */
            /*
			 * FIXME: it does work to reduce low-freq problems in S53-Wind-Sax
			 * and lead-voice samples, but introduces some 3 kbps bit bloat too.
			 * TODO: Further refinement of the shape of this hack.
			 */
            x = (-20 + bval[i] * 20 / 10).toDouble()
            if (x > 6) {
                x = 100.0
            }
            if (x < -15) {
                x = -15.0
            }
            x -= 8.0
            gfc.minval_l[i] = (Math.pow(10.0, x / 10.0) * gfc.numlines_l[i]).toFloat()
            i++
        }

        /************************************************************************
         * do the same things for short blocks
         */
        gfc.npart_s = init_numline(gfc.numlines_s, gfc.bo_s, gfc.bm_s, bval,
                bval_width, gfc.mld_s, psy.bo_s_weight, sfreq,
                Encoder.BLKSIZE_s, gfc.scalefac_band.s, Encoder.BLKSIZE_s / (2.0f * 192), Encoder.SBMAX_s)
        assert(gfc.npart_s < Encoder.CBANDS)

        /* SNR formula. short block is normalized by SNR. is it still right ? */
        j = 0
        i = 0
        while (i < gfc.npart_s) {
            var x: Double
            var snr = snr_s_a.toDouble()
            if (bval[i] >= bvl_a) {
                snr = (snr_s_b * (bval[i] - bvl_a) / (bvl_b - bvl_a) + snr_s_a * (bvl_b - bval[i]) / (bvl_b - bvl_a)).toDouble()
            }
            norm[i] = Math.pow(10.0, snr / 10.0).toFloat()

            /* ATH */
            x = Float.MAX_VALUE.toDouble()
            var k = 0
            while (k < gfc.numlines_s[i]) {
                val freq = sfreq * j / (1000.0f * Encoder.BLKSIZE_s)
                var level: Float
                /* freq = Min(.1,freq); *//*
										 * ATH below 100 Hz constant, not
										 * further climbing
										 */
                level = ATHformula(freq * 1000, gfp) - 20
                // scale to FFT units; returned value is in dB
                level = Math.pow(10.0, 0.1 * level).toFloat()
                // convert from dB . energy
                level *= gfc.numlines_s[i].toFloat()
                if (x > level)
                    x = level.toDouble()
                k++
                j++
            }
            ath.cb_s[i] = x.toFloat()

            /*
			 * MINVAL. For low freq, the strength of the masking is limited by
			 * minval this is an ISO MPEG1 thing, dont know if it is really
			 * needed
			 */
            x = -7.0 + bval[i] * 7.0 / 12.0
            if (bval[i] > 12) {
                x *= 1 + Math.log(1 + x) * 3.1
            }
            if (bval[i] < 12) {
                x *= 1 + Math.log(1 - x) * 2.3
            }
            if (x < -15) {
                x = -15.0
            }
            x -= 8.0
            gfc.minval_s[i] = Math.pow(10.0, x / 10).toFloat() * gfc.numlines_s[i]
            i++
        }

        gfc.s3_ss = init_s3_values(gfc.s3ind_s, gfc.npart_s, bval, bval_width,
                norm, useOldS3)

        init_mask_add_max_values()
        fft.init_fft(gfc)

        /* setup temporal masking */
        gfc.decay = Math.exp(-1.0 * LOG10 / (temporalmask_sustain_sec * sfreq / 192.0)).toFloat()

        run {
            var msfix: Float
            msfix = NS_MSFIX
            if (gfp.exp_nspsytune and 2 != 0)
                msfix = 1.0f
            if (Math.abs(gfp.msfix) > 0.0)
                msfix = gfp.msfix
            gfp.msfix = msfix

            /*
			 * spread only from npart_l bands. Normally, we use the spreading
			 * function to convolve from npart_l down to npart_l bands
			 */
            for (b in 0 until gfc.npart_l)
                if (gfc.s3ind[b][1] > gfc.npart_l - 1)
                    gfc.s3ind[b][1] = gfc.npart_l - 1
        }

        /*
		 * prepare for ATH auto adjustment: we want to decrease the ATH by 12 dB
		 * per second
		 */
        val frame_duration = 576f * gfc.mode_gr / sfreq
        ath.decay = Math.pow(10.0, -12.0 / 10.0 * frame_duration).toFloat()
        ath.adjust = 0.01f /* minimum, for leading low loudness */
        ath.adjustLimit = 1.0f /* on lead, allow adjust up to maximum */

        assert(gfc.bo_l[Encoder.SBMAX_l - 1] <= gfc.npart_l)
        assert(gfc.bo_s[Encoder.SBMAX_s - 1] <= gfc.npart_s)

        if (gfp.ATHtype != -1) {
            /* compute equal loudness weights (eql_w) */
            var freq: Float
            val freq_inc = gfp.out_samplerate.toFloat() / Encoder.BLKSIZE.toFloat()
            var eql_balance = 0.0f
            freq = 0.0f
            i = 0
            while (i < Encoder.BLKSIZE / 2) {
                /* convert ATH dB to relative power (not dB) */
                /* to determine eql_w */
                freq += freq_inc
                ath.eql_w[i] = 1f / Math.pow(10.0,
                        (ATHformula(freq, gfp) / 10).toDouble()).toFloat()
                eql_balance += ath.eql_w[i]
                ++i
            }
            eql_balance = 1.0f / eql_balance
            i = Encoder.BLKSIZE / 2
            while (--i >= 0) { /* scale weights */
                ath.eql_w[i] *= eql_balance
            }
        }
        run {
            var b = 0
            j = 0
            while (b < gfc.npart_s) {
                i = 0
                while (i < gfc.numlines_s[b]) {
                    ++j
                    ++i
                }
                b++
            }
            assert(j == 129)
            j = 0
            b = 0
            while (b < gfc.npart_l) {
                i = 0
                while (i < gfc.numlines_l[b]) {
                    ++j
                    ++i
                }
                b++
            }
            assert(j == 513)
        }
        j = 0
        i = 0
        while (i < gfc.npart_l) {
            val freq = sfreq * (j + gfc.numlines_l[i] / 2) / (1.0f * Encoder.BLKSIZE)
            gfc.mld_cb_l[i] = stereo_demask(freq.toDouble())
            j += gfc.numlines_l[i]
            i++
        }
        while (i < Encoder.CBANDS) {
            gfc.mld_cb_l[i] = 1f
            ++i
        }
        j = 0
        i = 0
        while (i < gfc.npart_s) {
            val freq = sfreq * (j + gfc.numlines_s[i] / 2) / (1.0f * Encoder.BLKSIZE_s)
            gfc.mld_cb_s[i] = stereo_demask(freq.toDouble())
            j += gfc.numlines_s[i]
            i++
        }
        while (i < Encoder.CBANDS) {
            gfc.mld_cb_s[i] = 1f
            ++i
        }
        return 0
    }

    /**
     * Those ATH formulas are returning their minimum value for input = -1
     */
    private fun ATHformula_GB(f: Float, value: Float): Float {
        var f = f
        /**
         * <PRE>
         * from Painter & Spanias
         * modified by Gabriel Bouvigne to better fit the reality
         * ath =    3.640 * pow(f,-0.8)
         * - 6.800 * exp(-0.6*pow(f-3.4,2.0))
         * + 6.000 * exp(-0.15*pow(f-8.7,2.0))
         * + 0.6* 0.001 * pow(f,4.0);
         *
         *
         * In the past LAME was using the Painter &Spanias formula.
         * But we had some recurrent problems with HF content.
         * We measured real ATH values, and found the older formula
         * to be inaccurate in the higher part. So we made this new
         * formula and this solved most of HF problematic test cases.
         * The tradeoff is that in VBR mode it increases a lot the
         * bitrate.
        </PRE> *
         */

        /*
		 * This curve can be adjusted according to the VBR scale: it adjusts
		 * from something close to Painter & Spanias on V9 up to Bouvigne's
		 * formula for V0. This way the VBR bitrate is more balanced according
		 * to the -V value.
		 */

        // the following Hack allows to ask for the lowest value
        if (f < -.3)
            f = 3410f

        // convert to khz
        f /= 1000f
        f = Math.max(0.1, f.toDouble()).toFloat()

        return (3.640f * Math.pow(f.toDouble(), -0.8).toFloat() - 6.800f * Math.exp(-0.6 * Math.pow(f - 3.4, 2.0)).toFloat() + 6.000f * Math.exp(-0.15 * Math.pow(f - 8.7, 2.0)).toFloat()
                + (0.6f + 0.04f * value) * 0.001f * Math.pow(f.toDouble(), 4.0).toFloat())
    }

    fun ATHformula(f: Float, gfp: LameGlobalFlags): Float {
        val ath: Float
        when (gfp.ATHtype) {
            0 -> ath = ATHformula_GB(f, 9f)
            1 ->
                // over sensitive, should probably be removed
                ath = ATHformula_GB(f, -1f)
            2 -> ath = ATHformula_GB(f, 0f)
            3 ->
                // modification of GB formula by Roel
                ath = ATHformula_GB(f, 1f) + 6
            4 -> ath = ATHformula_GB(f, gfp.ATHcurve)
            else -> ath = ATHformula_GB(f, 0f)
        }
        return ath
    }

    companion object {

        private const val LOG10 = 2.30258509299404568402f

        private const val rpelev = 2
        private const val rpelev2 = 16
        private const val rpelev_s = 2
        private const val rpelev2_s = 16

        /* size of each partition band, in barks: */
        private const val DELBARK = .34f

        /* tuned for output level (sensitive to energy scale) */
        private const val VO_SCALE = 1f / (14752 * 14752).toFloat() / (Encoder.BLKSIZE / 2).toFloat()

        private const val temporalmask_sustain_sec = 0.01f

        private const val NS_PREECHO_ATT0 = 0.8f
        private const val NS_PREECHO_ATT1 = 0.6f
        private const val NS_PREECHO_ATT2 = 0.3f

        private const val NS_MSFIX = 3.5f
        const val NSATTACKTHRE = 4.4f
        const val NSATTACKTHRE_S = 25

        private const val NSFIRLEN = 21

        /* size of each partition band, in barks: */
        private const val LN_TO_LOG10 = 0.2302585093f

        private fun NON_LINEAR_SCALE_ENERGY(x: Float): Float {
            return x
        }

        /* mask_add optimization */
        /* init the limit values used to avoid computing log in mask_add when it is not necessary */

        /**
         * <PRE>
         * For example, with i = 10*log10(m2/m1)/10*16         (= log10(m2/m1)*16)
         *
         * abs(i)>8 is equivalent (as i is an integer) to
         * abs(i)>=9
         * i>=9 || i<=-9
         * equivalent to (as i is the biggest integer smaller than log10(m2/m1)*16
         * or the smallest integer bigger than log10(m2/m1)*16 depending on the sign of log10(m2/m1)*16)
         * log10(m2/m1)>=9/16 || log10(m2/m1)<=-9/16
         * exp10 is strictly increasing thus this is equivalent to
         * m2/m1 >= 10^(9/16) || m2/m1<=10^(-9/16) which are comparisons to constants
        </PRE> *
         */

        /**
         * as in if(i>8)
         */
        private const val I1LIMIT = 8
        /**
         * as in if(i>24) . changed 23
         */
        private const val I2LIMIT = 23
        /**
         * as in if(m<15)
         */
        private const val MLIMIT = 15

        /**
         * This is the masking table:<BR></BR>
         * According to tonality, values are going from 0dB (TMN) to 9.3dB (NMT).<BR></BR>
         * After additive masking computation, 8dB are added, so final values are
         * going from 8dB to 17.3dB
         *
         * pow(10, -0.0..-0.6)
         */
        private val tab = floatArrayOf(1.0f, 0.79433f, 0.63096f, 0.63096f, 0.63096f, 0.63096f, 0.63096f, 0.25119f, 0.11749f)

        private val table1 = floatArrayOf(3.3246f * 3.3246f, 3.23837f * 3.23837f, 3.15437f * 3.15437f, 3.00412f * 3.00412f, 2.86103f * 2.86103f, 2.65407f * 2.65407f, 2.46209f * 2.46209f, 2.284f * 2.284f, 2.11879f * 2.11879f, 1.96552f * 1.96552f, 1.82335f * 1.82335f, 1.69146f * 1.69146f, 1.56911f * 1.56911f, 1.46658f * 1.46658f, 1.37074f * 1.37074f, 1.31036f * 1.31036f, 1.25264f * 1.25264f, 1.20648f * 1.20648f, 1.16203f * 1.16203f, 1.12765f * 1.12765f, 1.09428f * 1.09428f, 1.0659f * 1.0659f, 1.03826f * 1.03826f, 1.01895f * 1.01895f, 1f)

        private val table2 = floatArrayOf(1.33352f * 1.33352f, 1.35879f * 1.35879f, 1.38454f * 1.38454f, 1.39497f * 1.39497f, 1.40548f * 1.40548f, 1.3537f * 1.3537f, 1.30382f * 1.30382f, 1.22321f * 1.22321f, 1.14758f * 1.14758f, 1f)

        private val table3 = floatArrayOf(2.35364f * 2.35364f, 2.29259f * 2.29259f, 2.23313f * 2.23313f, 2.12675f * 2.12675f, 2.02545f * 2.02545f, 1.87894f * 1.87894f, 1.74303f * 1.74303f, 1.61695f * 1.61695f, 1.49999f * 1.49999f, 1.39148f * 1.39148f, 1.29083f * 1.29083f, 1.19746f * 1.19746f, 1.11084f * 1.11084f, 1.03826f * 1.03826f)

        private val table2_ = floatArrayOf(1.33352f * 1.33352f, 1.35879f * 1.35879f, 1.38454f * 1.38454f, 1.39497f * 1.39497f, 1.40548f * 1.40548f, 1.3537f * 1.3537f, 1.30382f * 1.30382f, 1.22321f * 1.22321f, 1.14758f * 1.14758f, 1f)

        /**
         * these values are tuned only for 44.1kHz...
         */
        private val regcoef_s = floatArrayOf(11.8f, 13.6f, 17.2f, 32f, 46.5f, 51.3f, 57.5f, 67.1f, 71.5f, 84.6f, 97.6f, 130f)/* 255.8 */

        /**
         * these values are tuned only for 44.1kHz...
         */
        private val regcoef_l = floatArrayOf(6.8f, 5.8f, 5.8f, 6.4f, 6.5f, 9.9f, 12.1f, 14.4f, 15f, 18.9f, 21.6f, 26.9f, 34.2f, 40.2f, 46.8f, 56.5f, 60.7f, 73.9f, 85.7f, 93.4f, 126.1f)/* 241.3 */

        private val fircoef = floatArrayOf(-8.65163e-18f * 2, -0.00851586f * 2, -6.74764e-18f * 2, 0.0209036f * 2, -3.36639e-17f * 2, -0.0438162f * 2, -1.54175e-17f * 2, 0.0931738f * 2, -5.52212e-17f * 2, -0.313819f * 2)

        private val fircoef_ = floatArrayOf(-8.65163e-18f * 2, -0.00851586f * 2, -6.74764e-18f * 2, 0.0209036f * 2, -3.36639e-17f * 2, -0.0438162f * 2, -1.54175e-17f * 2, 0.0931738f * 2, -5.52212e-17f * 2, -0.313819f * 2)
    }

}
