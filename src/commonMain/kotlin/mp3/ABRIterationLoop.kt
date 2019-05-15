package mp3

import jdk.Math
import jdk.assert

/**
 * encode a frame with a disired average bitrate
 *
 * mt 2000/05/31
 *
 * @author Ken
 */
class ABRIterationLoop
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
        val gfc = gfp.internal_flags
        val l3_xmin = FloatArray(L3Side.SFBMAX)
        val xrpow = FloatArray(576)
        val targ_bits = Array(2) { IntArray(2) }
        val max_frame_bits = IntArray(1)
        val analog_silence_bits = IntArray(1)
        val l3_side = gfc!!.l3_side

        var mean_bits = 0

        this.quantize.calc_target_bits(gfp, pe, ms_ener_ratio, targ_bits,
                analog_silence_bits, max_frame_bits)

        /*
		 * encode granules
		 */
        for (gr in 0 until gfc.mode_gr) {

            if (gfc.mode_ext == Encoder.MPG_MD_MS_LR) {
                this.quantize.ms_convert(gfc.l3_side, gr)
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
				 * cod_info, scalefac and xrpow get initialized in
				 * init_outer_loop
				 */
                this.quantize.init_outer_loop(gfc, cod_info)
                if (this.quantize.init_xrpow(gfc, cod_info, xrpow)) {
                    /*
					 * xr contains energy we will have to encode calculate the
					 * masking abilities find some good quantization in
					 * outer_loop
					 */
                    val ath_over = this.quantize.qupvt!!.calc_xmin(gfp,
                            ratio[gr][ch], cod_info, l3_xmin)
                    if (0 == ath_over)
                    /* analog silence */
                        targ_bits[gr][ch] = analog_silence_bits[0]

                    this.quantize.outer_loop(gfp, cod_info, l3_xmin, xrpow, ch,
                            targ_bits[gr][ch])
                }
                this.quantize.iteration_finish_one(gfc, gr, ch)
            } /* ch */
        } /* gr */

        /*
		 * find a bitrate which can refill the resevoir to positive size.
		 */
        gfc.bitrate_index = gfc.VBR_min_bitrate
        while (gfc.bitrate_index <= gfc.VBR_max_bitrate) {

            val mb = MeanBits(mean_bits)
            val rc = this.quantize.rv!!.ResvFrameBegin(gfp, mb)
            mean_bits = mb.bits
            if (rc >= 0)
                break
            gfc.bitrate_index++
        }
        assert(gfc.bitrate_index <= gfc.VBR_max_bitrate)

        this.quantize.rv!!.ResvFrameEnd(gfc, mean_bits)
    }
}