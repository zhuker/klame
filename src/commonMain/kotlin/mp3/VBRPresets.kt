package mp3

internal class VBRPresets(var vbr_q: Int, var quant_comp: Int, var quant_comp_s: Int,
                          var expY: Int,
                          /**
                           * short threshold
                           */
                          var st_lrm: Float, var st_s: Float,
                          var masking_adj: Float, var masking_adj_short: Float, var ath_lower: Float,
                          var ath_curve: Float, var ath_sensitivity: Float, var interch: Float,
                          var safejoint: Int, var sfb21mod: Int, var msfix: Float)