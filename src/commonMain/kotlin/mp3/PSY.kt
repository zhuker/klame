package mp3

/**
 * PSY Model related stuff
 */
class PSY {
    /**
     * The dbQ stuff.
     */
    internal var mask_adjust: Float = 0f
    /**
     * The dbQ stuff.
     */
    internal var mask_adjust_short: Float = 0f
    /* at transition from one scalefactor band to next */
    /**
     * Band weight long scalefactor bands.
     */
    internal var bo_l_weight = FloatArray(Encoder.SBMAX_l)
    /**
     * Band weight short scalefactor bands.
     */
    internal var bo_s_weight = FloatArray(Encoder.SBMAX_s)
}