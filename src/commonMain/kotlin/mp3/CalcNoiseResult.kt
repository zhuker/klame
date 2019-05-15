package mp3

class CalcNoiseResult {
    /**
     * sum of quantization noise > masking
     */
    internal var over_noise: Float = 0.toFloat()
    /**
     * sum of all quantization noise
     */
    internal var tot_noise: Float = 0.toFloat()
    /**
     * max quantization noise
     */
    internal var max_noise: Float = 0.toFloat()
    /**
     * number of quantization noise > masking
     */
    internal var over_count: Int = 0
    /**
     * SSD-like cost of distorted bands
     */
    internal var over_SSD: Int = 0
    internal var bits: Int = 0
}