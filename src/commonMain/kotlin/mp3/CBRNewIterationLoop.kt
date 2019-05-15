package mp3
import jdk.Math
import jdk.assert

/**
 * author/date??
 *
 * encodes one frame of MP3 data with constant bitrate
 *
 * @author Ken
 */
class CBRNewIterationLoop
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
        val l3_xmin = FloatArray(L3Side.SFBMAX)
        val xrpow = FloatArray(576)
        val targ_bits = IntArray(2)
        var mean_bits = 0
        var max_bits: Int
        val l3_side = gfc.l3_side

        val mb = MeanBits(mean_bits)
        this.quantize.rv!!.ResvFrameBegin(gfp, mb)
        mean_bits = mb.bits

        /* quantize! */
        for (gr in 0 until gfc.mode_gr) {

            /*
			 * calculate needed bits
			 */
            max_bits = this.quantize.qupvt!!.on_pe(gfp, pe, targ_bits, mean_bits,
                    gr, gr)

            if (gfc.mode_ext == Encoder.MPG_MD_MS_LR) {
                this.quantize.ms_convert(gfc.l3_side, gr)
                this.quantize.qupvt!!.reduce_side(targ_bits, ms_ener_ratio[gr],
                        mean_bits, max_bits)
            }

            for (ch in 0 until gfc.channels_out) {
                val adjust: Float
                val masking_lower_db: Float
                val cod_info = l3_side.tt[gr][ch]

                if (cod_info.block_type != Encoder.SHORT_TYPE) {
                    // NORM, START or STOP type
                    adjust = 0f
                    masking_lower_db = gfc.PSY!!.mask_adjust - adjust
                } else {
                    adjust = 0f
                    masking_lower_db = gfc.PSY!!.mask_adjust_short - adjust
                }
                gfc.masking_lower = Math.pow(10.0,
                        masking_lower_db * 0.1).toFloat()

                /*
				 * init_outer_loop sets up cod_info, scalefac and xrpow
				 */
                this.quantize.init_outer_loop(gfc, cod_info)
                if (this.quantize.init_xrpow(gfc, cod_info, xrpow)) {
                    /*
					 * xr contains energy we will have to encode calculate the
					 * masking abilities find some good quantization in
					 * outer_loop
					 */
                    this.quantize.qupvt!!.calc_xmin(gfp, ratio[gr][ch], cod_info,
                            l3_xmin)
                    this.quantize.outer_loop(gfp, cod_info, l3_xmin, xrpow, ch,
                            targ_bits[ch])
                }

                this.quantize.iteration_finish_one(gfc, gr, ch)
                assert(cod_info.part2_3_length <= LameInternalFlags.MAX_BITS_PER_CHANNEL)
                assert(cod_info.part2_3_length <= targ_bits[ch])
            } /* for ch */
        } /* for gr */

        this.quantize.rv!!.ResvFrameEnd(gfc, mean_bits)
    }
}