package mp3

import mp3.VBRQuantize.algo_t

import jdk.Math
import jdk.assert

internal class LongBlockConstrain
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
     * long block scalefacs
     *
     */

    override fun alloc(that: algo_t, vbrsf: IntArray, vbrsfmin: IntArray, vbrmax: Int) {
        var vbrmax = vbrmax
        val cod_info = that.cod_info
        val gfc = that.gfc
        var max_rangep: IntArray
        val maxminsfb = that.mingain_l
        var sfb: Int
        var maxover0: Int
        var maxover1: Int
        var maxover0p: Int
        var maxover1p: Int
        var mover: Int
        var delta = 0
        var v: Int
        var v0: Int
        var v1: Int
        var v0p: Int
        var v1p: Int
        var vm0p = 1
        var vm1p = 1
        val psymax = cod_info!!.psymax

        val max_range_long = VBRQuantize.max_range_long
        max_rangep = if (gfc!!.mode_gr == 2)
            max_range_long
        else
            VBRQuantize.max_range_long_lsf_pretab

        maxover0 = 0
        maxover1 = 0
        maxover0p = 0 /* pretab */
        maxover1p = 0 /* pretab */

        sfb = 0
        while (sfb < psymax) {
            assert(vbrsf[sfb] >= vbrsfmin[sfb])
            v = vbrmax - vbrsf[sfb]
            if (delta < v) {
                delta = v
            }
            v0 = v - 2 * max_range_long[sfb]
            v1 = v - 4 * max_range_long[sfb]
            v0p = v - 2 * (max_rangep[sfb] + this.vbrQuantize.qupvt.pretab[sfb])
            v1p = v - 4 * (max_rangep[sfb] + this.vbrQuantize.qupvt.pretab[sfb])
            if (maxover0 < v0) {
                maxover0 = v0
            }
            if (maxover1 < v1) {
                maxover1 = v1
            }
            if (maxover0p < v0p) {
                maxover0p = v0p
            }
            if (maxover1p < v1p) {
                maxover1p = v1p
            }
            ++sfb
        }
        if (vm0p == 1) {
            var gain = vbrmax - maxover0p
            if (gain < maxminsfb) {
                gain = maxminsfb
            }
            sfb = 0
            while (sfb < psymax) {
                val a = gain - vbrsfmin[sfb] - 2 * this.vbrQuantize.qupvt.pretab[sfb]
                if (a <= 0) {
                    vm0p = 0
                    vm1p = 0
                    break
                }
                ++sfb
            }
        }
        if (vm1p == 1) {
            var gain = vbrmax - maxover1p
            if (gain < maxminsfb) {
                gain = maxminsfb
            }
            sfb = 0
            while (sfb < psymax) {
                val b = gain - vbrsfmin[sfb] - 4 * this.vbrQuantize.qupvt.pretab[sfb]
                if (b <= 0) {
                    vm1p = 0
                    break
                }
                ++sfb
            }
        }
        if (vm0p == 0) {
            maxover0p = maxover0
        }
        if (vm1p == 0) {
            maxover1p = maxover1
        }
        if (gfc.noise_shaping != 2) {
            maxover1 = maxover0
            maxover1p = maxover0p
        }
        mover = Math.min(maxover0, maxover0p)
        mover = Math.min(mover, maxover1)
        mover = Math.min(mover, maxover1p)

        if (delta > mover) {
            delta = mover
        }
        vbrmax -= delta
        if (vbrmax < maxminsfb) {
            vbrmax = maxminsfb
        }
        maxover0 -= mover
        maxover0p -= mover
        maxover1 -= mover
        maxover1p -= mover

        if (maxover0 == 0) {
            cod_info.scalefac_scale = 0
            cod_info.preflag = 0
            max_rangep = max_range_long
        } else if (maxover0p == 0) {
            cod_info.scalefac_scale = 0
            cod_info.preflag = 1
        } else if (maxover1 == 0) {
            cod_info.scalefac_scale = 1
            cod_info.preflag = 0
            max_rangep = max_range_long
        } else if (maxover1p == 0) {
            cod_info.scalefac_scale = 1
            cod_info.preflag = 1
        } else {
            assert(false) /* this should not happen */
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
            this.vbrQuantize.set_scalefacs(cod_info, vbrsfmin, sf_temp,
                    max_rangep)
        }
        assert(this.vbrQuantize.checkScalefactor(cod_info, vbrsfmin))

    }
}