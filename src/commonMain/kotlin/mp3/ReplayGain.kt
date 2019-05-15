package mp3

class ReplayGain {
    internal var linprebuf = FloatArray(GainAnalysis.MAX_ORDER * 2)
    /**
     * left input samples, with pre-buffer
     */
    internal var linpre: Int = 0
    internal var lstepbuf = FloatArray(GainAnalysis.MAX_SAMPLES_PER_WINDOW + GainAnalysis.MAX_ORDER)
    /**
     * left "first step" (i.e. post first filter) samples
     */
    internal var lstep: Int = 0
    internal var loutbuf = FloatArray(GainAnalysis.MAX_SAMPLES_PER_WINDOW + GainAnalysis.MAX_ORDER)
    /**
     * left "out" (i.e. post second filter) samples
     */
    internal var lout: Int = 0
    internal var rinprebuf = FloatArray(GainAnalysis.MAX_ORDER * 2)
    /**
     * right input samples ...
     */
    internal var rinpre: Int = 0
    internal var rstepbuf = FloatArray(GainAnalysis.MAX_SAMPLES_PER_WINDOW + GainAnalysis.MAX_ORDER)
    internal var rstep: Int = 0
    internal var routbuf = FloatArray(GainAnalysis.MAX_SAMPLES_PER_WINDOW + GainAnalysis.MAX_ORDER)
    internal var rout: Int = 0
    /**
     * number of samples required to reach number of milliseconds required
     * for RMS window
     */
    internal var sampleWindow: Int = 0
    internal var totsamp: Int = 0
    internal var lsum: Double = 0.toDouble()
    internal var rsum: Double = 0.toDouble()
    internal var freqindex: Int = 0
    internal var first: Int = 0
    internal var A = IntArray((GainAnalysis.STEPS_per_dB * GainAnalysis.MAX_dB).toInt())
    internal var B = IntArray((GainAnalysis.STEPS_per_dB * GainAnalysis.MAX_dB).toInt())

}