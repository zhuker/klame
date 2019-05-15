package mp3

internal class ABRPresets(kbps: Int, var quant_comp: Int, var quant_comp_s: Int,
                          var safejoint: Int, var nsmsfix: Float,
                          /**
                           * short threshold
                           */
                          var st_lrm: Float,
                          var st_s: Float, var nsbass: Float, var scale: Float,
                          var masking_adj: Float, var ath_lower: Float, var ath_curve: Float,
                          var interch: Float, var sfscale: Int)