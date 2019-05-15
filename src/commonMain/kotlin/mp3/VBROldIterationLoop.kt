package mp3

/**
 * tries to find out how many bits are needed for each granule and channel
 * to get an acceptable quantization. An appropriate bitrate will then be
 * choosed for quantization. rh 8/99
 *
 * Robert Hegemann 2000-09-06 rewrite
 *
 * @author Ken
 */
class VBROldIterationLoop
/**
 * @param quantize
 */
internal constructor(
        /**
         *
         */
        private val quantize: Quantize) : IIterationLoop {

    override fun iteration_loop(gfp: LameGlobalFlags, pe: Array<FloatArray>,
                                ms_ener_ratio: FloatArray, ratio: Array<Array<III_psy_ratio>>) {
        val gfc = gfp.internal_flags!!
        val l3_xmin = Array(2) { Array(2) { FloatArray(L3Side.SFBMAX) } }

        val xrpow = FloatArray(576)
        val bands = Array(2) { IntArray(2) }
        val frameBits = IntArray(15)
        val min_bits = Array(2) { IntArray(2) }
        val max_bits = Array(2) { IntArray(2) }
        var mean_bits = 0
        val l3_side = gfc.l3_side

        val analog_silence = this.quantize.VBR_old_prepare(gfp, pe, ms_ener_ratio, ratio,
                l3_xmin, frameBits, min_bits, max_bits, bands)

        /*---------------------------------*/
        while (true) {
            /*
			 * quantize granules with lowest possible number of bits
			 */
            var used_bits = 0

            for (gr in 0 until gfc.mode_gr) {
                for (ch in 0 until gfc.channels_out) {
                    val cod_info = l3_side.tt[gr][ch]

                    /*
					 * init_outer_loop sets up cod_info, scalefac and xrpow
					 */
                    val ret = this.quantize.init_xrpow(gfc, cod_info, xrpow)
                    if (!ret || max_bits[gr][ch] == 0) {
                        /*
						 * xr contains no energy l3_enc, our encoding data,
						 * will be quantized to zero
						 */
                        continue /* with next channel */
                    }

                    this.quantize.VBR_encode_granule(gfp, cod_info, l3_xmin[gr][ch],
                            xrpow, ch, min_bits[gr][ch], max_bits[gr][ch])

                    /*
					 * do the 'substep shaping'
					 */
                    if (gfc.substep_shaping and 1 != 0) {
                        this.quantize.trancate_smallspectrums(gfc, l3_side.tt[gr][ch],
                                l3_xmin[gr][ch], xrpow)
                    }

                    val usedB = cod_info.part2_3_length + cod_info.part2_length
                    used_bits += usedB
                } /* for ch */
            } /* for gr */

            /*
			 * find lowest bitrate able to hold used bits
			 */
            if (analog_silence != 0 && 0 == gfp.VBR_hard_min)
            /*
				 * we detected analog silence and the user did not specify
				 * any hard framesize limit, so start with smallest possible
				 * frame
				 */
                gfc.bitrate_index = 1
            else
                gfc.bitrate_index = gfc.VBR_min_bitrate

            while (gfc.bitrate_index < gfc.VBR_max_bitrate) {
                if (used_bits <= frameBits[gfc.bitrate_index])
                    break
                gfc.bitrate_index++
            }
            val mb = MeanBits(mean_bits)
            val bits = this.quantize.rv!!.ResvFrameBegin(gfp, mb)
            mean_bits = mb.bits

            if (used_bits <= bits)
                break

            this.quantize.bitpressure_strategy(gfc, l3_xmin, min_bits, max_bits)

        }
        /* breaks adjusted */
        /*--------------------------------------*/

        for (gr in 0 until gfc.mode_gr) {
            for (ch in 0 until gfc.channels_out) {
                this.quantize.iteration_finish_one(gfc, gr, ch)
            } /* for ch */
        } /* for gr */
        this.quantize.rv!!.ResvFrameEnd(gfc, mean_bits)
    }
}