package mp3

import jdk.assert

class VBRNewIterationLoop
/**
 * @param quantize
 */
internal constructor(
        /**
         *
         */
        private val quantize: Quantize) : IIterationLoop {

    override fun iteration_loop(gfp: LameGlobalFlags,
                                pe: Array<FloatArray>, ms_ener_ratio: FloatArray,
                                ratio: Array<Array<III_psy_ratio>>) {
        val gfc = gfp.internal_flags!!
        val l3_xmin = Array(2) { Array(2) { FloatArray(L3Side.SFBMAX) } }

        val xrpow = Array(2) { Array(2) { FloatArray(576) } }
        val frameBits = IntArray(15)
        val max_bits = Array(2) { IntArray(2) }
        val l3_side = gfc.l3_side

        val analog_silence = this.quantize.VBR_new_prepare(gfp, pe, ratio, l3_xmin,
                frameBits, max_bits)

        for (gr in 0 until gfc.mode_gr) {
            for (ch in 0 until gfc.channels_out) {
                val cod_info = l3_side.tt[gr][ch]

                /*
				 * init_outer_loop sets up cod_info, scalefac and xrpow
				 */
                if (!this.quantize.init_xrpow(gfc, cod_info, xrpow[gr][ch])) {
                    /* silent granule needs no bits */
                    max_bits[gr][ch] = 0
                }
            } /* for ch */
        } /* for gr */

        /*
		 * quantize granules with lowest possible number of bits
		 */

        val used_bits = this.quantize.vbr.VBR_encode_frame(gfc, xrpow, l3_xmin, max_bits)

        if (!gfp.free_format) {
            /*
			 * find lowest bitrate able to hold used bits
			 */
            if (analog_silence != 0 && 0 == gfp.VBR_hard_min) {
                /*
				 * we detected analog silence and the user did not specify
				 * any hard framesize limit, so start with smallest possible
				 * frame
				 */
                gfc.bitrate_index = 1
            } else {
                gfc.bitrate_index = gfc.VBR_min_bitrate
            }

            while (gfc.bitrate_index < gfc.VBR_max_bitrate) {
                if (used_bits <= frameBits[gfc.bitrate_index])
                    break
                gfc.bitrate_index++
            }
            if (gfc.bitrate_index > gfc.VBR_max_bitrate) {
                gfc.bitrate_index = gfc.VBR_max_bitrate
            }
        } else {
            gfc.bitrate_index = 0
        }
        if (used_bits <= frameBits[gfc.bitrate_index]) {
            /* update Reservoire status */
            var mean_bits = 0
            val fullframebits: Int
            val mb = MeanBits(mean_bits)
            fullframebits = this.quantize.rv!!.ResvFrameBegin(gfp, mb)
            mean_bits = mb.bits
            assert(used_bits <= fullframebits)
            for (gr in 0 until gfc.mode_gr) {
                for (ch in 0 until gfc.channels_out) {
                    val cod_info = l3_side.tt[gr][ch]
                    this.quantize.rv!!.ResvAdjust(gfc, cod_info)
                }
            }
            this.quantize.rv!!.ResvFrameEnd(gfc, mean_bits)
        } else {
            /*
			 * SHOULD NOT HAPPEN INTERNAL ERROR
			 */
            throw RuntimeException("INTERNAL ERROR IN VBR NEW CODE, please send bug report")
        }
    }
}