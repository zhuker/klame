package mp3

/**
 * Variables used for --nspsytune
 *
 * @author Ken
 */
class NsPsy {
    internal var last_en_subshort = Array(4) { FloatArray(9) }
    internal var lastAttacks = IntArray(4)
    internal var pefirbuf = FloatArray(19)
    internal var longfact = FloatArray(Encoder.SBMAX_l)
    internal var shortfact = FloatArray(Encoder.SBMAX_s)

    /**
     * short block tuning
     */
    internal var attackthre: Float = 0f
    internal var attackthre_s: Float = 0f
}