package mp3

import jdk.Math
import jdk.assert
import mp3.VBRQuantize.algo_t

internal class ShortBlockConstrain
/**
 * @param vbrQuantize
 */
(
        /**
         *
         */
        private val vbrQuantize: VBRQuantize) : VBRQuantize.alloc_sf_f {

    /******************************************************************
     *
     * short block scalefacs
     *
     */

    override fun alloc(that: algo_t, vbrsf: IntArray, vbrsfmin: IntArray, vbrmax: Int) {
        var vbrmax = vbrmax
        val cod_info = that.cod_info
        val gfc = that.gfc
        val maxminsfb = that.mingain_l
        val mover: Int
        var maxover0 = 0
        var maxover1 = 0
        var delta = 0
        var v: Int
        var v0: Int
        var v1: Int
        var sfb: Int
        val psymax = cod_info!!.psymax

        sfb = 0
        while (sfb < psymax) {
            assert(vbrsf[sfb] >= vbrsfmin[sfb])
            v = vbrmax - vbrsf[sfb]
            if (delta < v) {
                delta = v
            }
            val max_range_short = VBRQuantize.max_range_short
            v0 = v - (4 * 14 + 2 * max_range_short[sfb])
            v1 = v - (4 * 14 + 4 * max_range_short[sfb])
            if (maxover0 < v0) {
                maxover0 = v0
            }
            if (maxover1 < v1) {
                maxover1 = v1
            }
            ++sfb
        }
        if (gfc!!.noise_shaping == 2) {
            /* allow scalefac_scale=1 */
            mover = Math.min(maxover0, maxover1)
        } else {
            mover = maxover0
        }
        if (delta > mover) {
            delta = mover
        }
        vbrmax -= delta
        maxover0 -= mover
        maxover1 -= mover

        if (maxover0 == 0) {
            cod_info.scalefac_scale = 0
        } else if (maxover1 == 0) {
            cod_info.scalefac_scale = 1
        }
        if (vbrmax < maxminsfb) {
            vbrmax = maxminsfb
        }
        cod_info.global_gain = vbrmax

        if (cod_info.global_gain < 0) {
            cod_info.global_gain = 0
        } else if (cod_info.global_gain > 255) {
            cod_info.global_gain = 255
        }
        run {
            val sf_temp = IntArray(L3Side.SFBMAX)
            sfb = 0
            while (sfb < L3Side.SFBMAX) {
                sf_temp[sfb] = vbrsf[sfb] - vbrmax
                ++sfb
            }
            this.vbrQuantize.set_subblock_gain(cod_info, that.mingain_s,
                    sf_temp)
            this.vbrQuantize.set_scalefacs(cod_info, vbrsfmin, sf_temp,
                    VBRQuantize.max_range_short)
        }
        assert(this.vbrQuantize.checkScalefactor(cod_info, vbrsfmin))

    }
}