package mp3

/**
 * allows re-use of previously computed noise values
 */
class CalcNoiseData {
    var global_gain: Int = 0
    var sfb_count1: Int = 0
    var step = IntArray(39)
    var noise = FloatArray(39)
    var noise_log = FloatArray(39)
}