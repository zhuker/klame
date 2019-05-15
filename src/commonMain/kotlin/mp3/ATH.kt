package mp3

/**
 * ATH related stuff, if something new ATH related has to be added, please plug
 * it here into the ATH.
 */
class ATH {
    /**
     * Method for the auto adjustment.
     */
    internal var useAdjust: Int = 0
    /**
     * factor for tuning the (sample power) point below which adaptive threshold
     * of hearing adjustment occurs
     */
    internal var aaSensitivityP: Float = 0.toFloat()
    /**
     * Lowering based on peak volume, 1 = no lowering.
     */
    internal var adjust: Float = 0.toFloat()
    /**
     * Limit for dynamic ATH adjust.
     */
    internal var adjustLimit: Float = 0.toFloat()
    /**
     * Determined to lower x dB each second.
     */
    internal var decay: Float = 0.toFloat()
    /**
     * Lowest ATH value.
     */
    internal var floor: Float = 0.toFloat()
    /**
     * ATH for sfbs in long blocks.
     */
    internal var l = FloatArray(Encoder.SBMAX_l)
    /**
     * ATH for sfbs in short blocks.
     */
    internal var s = FloatArray(Encoder.SBMAX_s)
    /**
     * ATH for partitioned sfb21 in long blocks.
     */
    internal var psfb21 = FloatArray(Encoder.PSFB21)
    /**
     * ATH for partitioned sfb12 in short blocks.
     */
    internal var psfb12 = FloatArray(Encoder.PSFB12)
    /**
     * ATH for long block convolution bands.
     */
    internal var cb_l = FloatArray(Encoder.CBANDS)
    /**
     * ATH for short block convolution bands.
     */
    internal var cb_s = FloatArray(Encoder.CBANDS)
    /**
     * Equal loudness weights (based on ATH).
     */
    internal var eql_w = FloatArray(Encoder.BLKSIZE / 2)
}