package mp3

import jdk.Math
import jdk.System

/**
 * Layer III side information.
 *
 * @author Ken
 */
class ScaleFac {

    internal var l = IntArray(1 + Encoder.SBMAX_l)
    internal var s = IntArray(1 + Encoder.SBMAX_s)
    internal var psfb21 = IntArray(1 + Encoder.PSFB21)
    internal var psfb12 = IntArray(1 + Encoder.PSFB12)

    constructor()

    constructor(arrL: IntArray, arrS: IntArray, arr21: IntArray,
                arr12: IntArray) {
        System.arraycopy(arrL, 0, l, 0, Math.min(arrL.size, l.size))
        System.arraycopy(arrS, 0, s, 0, Math.min(arrS.size, s.size))
        System.arraycopy(arr21, 0, psfb21, 0,
                Math.min(arr21.size, psfb21.size))
        System.arraycopy(arr12, 0, psfb12, 0,
                Math.min(arr12.size, psfb12.size))
    }
}