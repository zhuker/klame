/*
** FFT and FHT routines
**  Copyright 1988, 1993; Ron Mayer
**      Copyright (c) 1999-2000 Takehiro Tominaga
**
**  fht(fz,n);
**      Does a hartley transform of "n" points in the array "fz".
**
** NOTE: This routine uses at least 2 patented algorithms, and may be
**       under the restrictions of a bunch of different organizations.
**       Although I wrote it completely myself; it is kind of a derivative
**       of a routine I once authored and released under the GPL, so it
**       may fall under the free software foundation's restrictions;
**       it was worked on as a Stanford Univ project, so they claim
**       some rights to it; it was further optimized at work here, so
**       I think this company claims parts of it.  The patents are
**       held by R. Bracewell (the FHT algorithm) and O. Buneman (the
**       trig generator), both at Stanford Univ.
**       If it were up to me, I'd say go do whatever you want with it;
**       but it would be polite to give credit to the following people
**       if you use this anywhere:
**           Euler     - probable inventor of the fourier transform.
**           Gauss     - probable inventor of the FFT.
**           Hartley   - probable inventor of the hartley transform.
**           Buneman   - for a really cool trig generator
**           Mayer(me) - for authoring this particular version and
**                       including all the optimizations in one package.
**       Thanks,
**       Ron Mayer; mayer@acuson.com
** and added some optimization by
**           Mather    - idea of using lookup table
**           Takehiro  - some dirty hack for speed up
*/
package mp3

import jdk.and
import jdk.Math


class FFT {

    private fun fht(fz: FloatArray, fzPos: Int, n: Int) {
        var n = n
        var tri = 0
        var k4: Int
        var fi: Int
        var gi: Int
        val fn: Int

        n = n shl 1 /* to get BLKSIZE, because of 3DNow! ASM routine */
        fn = fzPos + n
        k4 = 4
        do {
            var s1: Float
            var c1: Float
            var i: Int
            val k1: Int
            val k2: Int
            val k3: Int
            val kx: Int
            kx = k4 shr 1
            k1 = k4
            k2 = k4 shl 1
            k3 = k2 + k1
            k4 = k2 shl 1
            fi = fzPos
            gi = fi + kx
            do {
                var f0: Float
                var f1: Float
                var f2: Float
                var f3: Float
                f1 = fz[fi + 0] - fz[fi + k1]
                f0 = fz[fi + 0] + fz[fi + k1]
                f3 = fz[fi + k2] - fz[fi + k3]
                f2 = fz[fi + k2] + fz[fi + k3]
                fz[fi + k2] = f0 - f2
                fz[fi + 0] = f0 + f2
                fz[fi + k3] = f1 - f3
                fz[fi + k1] = f1 + f3
                f1 = fz[gi + 0] - fz[gi + k1]
                f0 = fz[gi + 0] + fz[gi + k1]
                f3 = Util.SQRT2 * fz[gi + k3]
                f2 = Util.SQRT2 * fz[gi + k2]
                fz[gi + k2] = f0 - f2
                fz[gi + 0] = f0 + f2
                fz[gi + k3] = f1 - f3
                fz[gi + k1] = f1 + f3
                gi += k4
                fi += k4
            } while (fi < fn)
            c1 = costab[tri + 0]
            s1 = costab[tri + 1]
            i = 1
            while (i < kx) {
                var c2: Float
                val s2: Float
                c2 = 1 - 2 * s1 * s1
                s2 = 2 * s1 * c1
                fi = fzPos + i
                gi = fzPos + k1 - i
                do {
                    var a: Float
                    var b: Float
                    val g0: Float
                    val f0: Float
                    val f1: Float
                    val g1: Float
                    val f2: Float
                    val g2: Float
                    val f3: Float
                    val g3: Float
                    b = s2 * fz[fi + k1] - c2 * fz[gi + k1]
                    a = c2 * fz[fi + k1] + s2 * fz[gi + k1]
                    f1 = fz[fi + 0] - a
                    f0 = fz[fi + 0] + a
                    g1 = fz[gi + 0] - b
                    g0 = fz[gi + 0] + b
                    b = s2 * fz[fi + k3] - c2 * fz[gi + k3]
                    a = c2 * fz[fi + k3] + s2 * fz[gi + k3]
                    f3 = fz[fi + k2] - a
                    f2 = fz[fi + k2] + a
                    g3 = fz[gi + k2] - b
                    g2 = fz[gi + k2] + b
                    b = s1 * f2 - c1 * g3
                    a = c1 * f2 + s1 * g3
                    fz[fi + k2] = f0 - a
                    fz[fi + 0] = f0 + a
                    fz[gi + k3] = g1 - b
                    fz[gi + k1] = g1 + b
                    b = c1 * g2 - s1 * f3
                    a = s1 * g2 + c1 * f3
                    fz[gi + k2] = g0 - a
                    fz[gi + 0] = g0 + a
                    fz[fi + k3] = f1 - b
                    fz[fi + k1] = f1 + b
                    gi += k4
                    fi += k4
                } while (fi < fn)
                c2 = c1
                c1 = c2 * costab[tri + 0] - s1 * costab[tri + 1]
                s1 = c2 * costab[tri + 1] + s1 * costab[tri + 0]
                i++
            }
            tri += 2
        } while (k4 < n)
    }

    fun fft_short(gfc: LameInternalFlags,
                  x_real: Array<FloatArray>, chn: Int, buffer: Array<FloatArray>,
                  bufPos: Int) {
        for (b in 0..2) {
            var x = Encoder.BLKSIZE_s / 2
            val k = (576 / 3 * (b + 1)).toShort()
            var j = Encoder.BLKSIZE_s / 8 - 1
            do {
                var f0: Float
                var f1: Float
                var f2: Float
                var f3: Float
                var w: Float

                val i = rv_tbl[j shl 2] and 0xff

                f0 = window_s[i] * buffer[chn][bufPos + i + k.toInt()]
                w = window_s[0x7f - i] * buffer[chn][bufPos + i + k.toInt() + 0x80]
                f1 = f0 - w
                f0 = f0 + w
                f2 = window_s[i + 0x40] * buffer[chn][bufPos + i + k.toInt() + 0x40]
                w = window_s[0x3f - i] * buffer[chn][bufPos + i + k.toInt() + 0xc0]
                f3 = f2 - w
                f2 = f2 + w

                x -= 4
                x_real[b][x + 0] = f0 + f2
                x_real[b][x + 2] = f0 - f2
                x_real[b][x + 1] = f1 + f3
                x_real[b][x + 3] = f1 - f3

                f0 = window_s[i + 0x01] * buffer[chn][bufPos + i + k.toInt() + 0x01]
                w = window_s[0x7e - i] * buffer[chn][bufPos + i + k.toInt() + 0x81]
                f1 = f0 - w
                f0 = f0 + w
                f2 = window_s[i + 0x41] * buffer[chn][bufPos + i + k.toInt() + 0x41]
                w = window_s[0x3e - i] * buffer[chn][bufPos + i + k.toInt() + 0xc1]
                f3 = f2 - w
                f2 = f2 + w

                x_real[b][x + Encoder.BLKSIZE_s / 2 + 0] = f0 + f2
                x_real[b][x + Encoder.BLKSIZE_s / 2 + 2] = f0 - f2
                x_real[b][x + Encoder.BLKSIZE_s / 2 + 1] = f1 + f3
                x_real[b][x + Encoder.BLKSIZE_s / 2 + 3] = f1 - f3
            } while (--j >= 0)

            fht(x_real[b], x, Encoder.BLKSIZE_s / 2)
            /* BLKSIZE_s/2 because of 3DNow! ASM routine */
            /* BLKSIZE/2 because of 3DNow! ASM routine */
        }
    }

    fun fft_long(gfc: LameInternalFlags, y: FloatArray, chn: Int,
                 buffer: Array<FloatArray>, bufPos: Int) {
        var jj = Encoder.BLKSIZE / 8 - 1
        var x = Encoder.BLKSIZE / 2

        do {
            var f0: Float
            var f1: Float
            var f2: Float
            var f3: Float
            var w: Float

            val i = rv_tbl[jj] and 0xff
            f0 = window[i] * buffer[chn][bufPos + i]
            w = window[i + 0x200] * buffer[chn][bufPos + i + 0x200]
            f1 = f0 - w
            f0 = f0 + w
            f2 = window[i + 0x100] * buffer[chn][bufPos + i + 0x100]
            w = window[i + 0x300] * buffer[chn][bufPos + i + 0x300]
            f3 = f2 - w
            f2 = f2 + w

            x -= 4
            y[x + 0] = f0 + f2
            y[x + 2] = f0 - f2
            y[x + 1] = f1 + f3
            y[x + 3] = f1 - f3

            f0 = window[i + 0x001] * buffer[chn][bufPos + i + 0x001]
            w = window[i + 0x201] * buffer[chn][bufPos + i + 0x201]
            f1 = f0 - w
            f0 = f0 + w
            f2 = window[i + 0x101] * buffer[chn][bufPos + i + 0x101]
            w = window[i + 0x301] * buffer[chn][bufPos + i + 0x301]
            f3 = f2 - w
            f2 = f2 + w

            y[x + Encoder.BLKSIZE / 2 + 0] = f0 + f2
            y[x + Encoder.BLKSIZE / 2 + 2] = f0 - f2
            y[x + Encoder.BLKSIZE / 2 + 1] = f1 + f3
            y[x + Encoder.BLKSIZE / 2 + 3] = f1 - f3
        } while (--jj >= 0)

        fht(y, x, Encoder.BLKSIZE / 2)
        /* BLKSIZE/2 because of 3DNow! ASM routine */
    }

    fun init_fft(gfc: LameInternalFlags) {
        /* The type of window used here will make no real difference, but */
        /*
		 * in the interest of merging nspsytune stuff - switch to blackman
		 * window
		 */
        for (i in 0 until Encoder.BLKSIZE)
        /* blackman window */
            window[i] = (0.42 - 0.5 * Math.cos(2.0 * Math.PI * (i + .5) / Encoder.BLKSIZE) + 0.08 * Math.cos(4.0 * Math.PI * (i + .5) / Encoder.BLKSIZE)).toFloat()

        for (i in 0 until Encoder.BLKSIZE_s / 2)
            window_s[i] = (0.5 * (1.0 - Math.cos((2.0 * Math.PI
                    * (i + 0.5)) / Encoder.BLKSIZE_s))).toFloat()

    }

    companion object {

        private val window = FloatArray(Encoder.BLKSIZE)
        private val window_s = FloatArray(Encoder.BLKSIZE_s / 2)

        private val costab = floatArrayOf(9.238795325112867e-01f, 3.826834323650898e-01f, 9.951847266721969e-01f, 9.801714032956060e-02f, 9.996988186962042e-01f, 2.454122852291229e-02f, 9.999811752826011e-01f, 6.135884649154475e-03f)

        private val rv_tbl = byteArrayOf(0x00, 0x80.toByte(), 0x40, 0xc0.toByte(), 0x20, 0xa0.toByte(), 0x60, 0xe0.toByte(), 0x10, 0x90.toByte(), 0x50, 0xd0.toByte(), 0x30, 0xb0.toByte(), 0x70, 0xf0.toByte(), 0x08, 0x88.toByte(), 0x48, 0xc8.toByte(), 0x28, 0xa8.toByte(), 0x68, 0xe8.toByte(), 0x18, 0x98.toByte(), 0x58, 0xd8.toByte(), 0x38, 0xb8.toByte(), 0x78, 0xf8.toByte(), 0x04, 0x84.toByte(), 0x44, 0xc4.toByte(), 0x24, 0xa4.toByte(), 0x64, 0xe4.toByte(), 0x14, 0x94.toByte(), 0x54, 0xd4.toByte(), 0x34, 0xb4.toByte(), 0x74, 0xf4.toByte(), 0x0c, 0x8c.toByte(), 0x4c, 0xcc.toByte(), 0x2c, 0xac.toByte(), 0x6c, 0xec.toByte(), 0x1c, 0x9c.toByte(), 0x5c, 0xdc.toByte(), 0x3c, 0xbc.toByte(), 0x7c, 0xfc.toByte(), 0x02, 0x82.toByte(), 0x42, 0xc2.toByte(), 0x22, 0xa2.toByte(), 0x62, 0xe2.toByte(), 0x12, 0x92.toByte(), 0x52, 0xd2.toByte(), 0x32, 0xb2.toByte(), 0x72, 0xf2.toByte(), 0x0a, 0x8a.toByte(), 0x4a, 0xca.toByte(), 0x2a, 0xaa.toByte(), 0x6a, 0xea.toByte(), 0x1a, 0x9a.toByte(), 0x5a, 0xda.toByte(), 0x3a, 0xba.toByte(), 0x7a, 0xfa.toByte(), 0x06, 0x86.toByte(), 0x46, 0xc6.toByte(), 0x26, 0xa6.toByte(), 0x66, 0xe6.toByte(), 0x16, 0x96.toByte(), 0x56, 0xd6.toByte(), 0x36, 0xb6.toByte(), 0x76, 0xf6.toByte(), 0x0e, 0x8e.toByte(), 0x4e, 0xce.toByte(), 0x2e, 0xae.toByte(), 0x6e, 0xee.toByte(), 0x1e, 0x9e.toByte(), 0x5e, 0xde.toByte(), 0x3e, 0xbe.toByte(), 0x7e, 0xfe.toByte())
    }

}
