package mp3

import jdk.System

class III_psy_xmin {
    internal var l = FloatArray(Encoder.SBMAX_l)
    internal var s = Array(Encoder.SBMAX_s) { FloatArray(3) }

    fun assign(iii_psy_xmin: III_psy_xmin) {
        System.arraycopy(iii_psy_xmin.l, 0, l, 0, Encoder.SBMAX_l)
        for (i in 0 until Encoder.SBMAX_s) {
            for (j in 0..2) {
                s[i][j] = iii_psy_xmin.s[i][j]
            }
        }
    }
}