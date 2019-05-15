/*
 *      GTK plotting routines source file
 *
 *      Copyright (c) 1999 Mark Taylor
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
package mp3

/**
 * used by the frame analyzer
 */
class PlottingData {
    /**
     * current frame number
     */
    internal var frameNum: Int = 0
    internal var frameNum123: Int = 0
    /**
     * number of pcm samples read for this frame
     */
    internal var num_samples: Int = 0
    /**
     * starting time of frame, in seconds
     */
    internal var frametime: Double = 0.0
    internal var pcmdata = Array(2) { DoubleArray(1600) }
    internal var pcmdata2 = Array(2) { DoubleArray(1152 + 1152 - Encoder.DECDELAY) }
    internal var xr = Array(2) { Array(2) { DoubleArray(576) } }
    var mpg123xr = Array(2) { Array(2) { DoubleArray(576) } }
    internal var ms_ratio = DoubleArray(2)
    internal var ms_ener_ratio = DoubleArray(2)

    /* L,R, M and S values */

    /**
     * psymodel is one ahead
     */
    internal var energy_save = Array(4) { DoubleArray(Encoder.BLKSIZE) }
    internal var energy = Array(2) { Array(4) { DoubleArray(Encoder.BLKSIZE) } }
    internal var pe = Array(2) { DoubleArray(4) }
    internal var thr = Array(2) { Array(4) { DoubleArray(Encoder.SBMAX_l) } }
    internal var en = Array(2) { Array(4) { DoubleArray(Encoder.SBMAX_l) } }
    internal var thr_s = Array(2) { Array(4) { DoubleArray(3 * Encoder.SBMAX_s) } }
    internal var en_s = Array(2) { Array(4) { DoubleArray(3 * Encoder.SBMAX_s) } }
    /**
     * psymodel is one ahead
     */
    internal var ers_save = DoubleArray(4)
    internal var ers = Array(2) { DoubleArray(4) }

    var sfb = Array(2) { Array(2) { DoubleArray(Encoder.SBMAX_l) } }
    var sfb_s = Array(2) { Array(2) { DoubleArray(3 * Encoder.SBMAX_s) } }
    internal var LAMEsfb = Array(2) { Array(2) { DoubleArray(Encoder.SBMAX_l) } }
    internal var LAMEsfb_s = Array(2) { Array(2) { DoubleArray(3 * Encoder.SBMAX_s) } }

    internal var LAMEqss = Array(2) { IntArray(2) }
    var qss = Array(2) { IntArray(2) }
    var big_values = Array(2) { IntArray(2) }
    var sub_gain = Array(2) { Array(2) { IntArray(3) } }

    internal var xfsf = Array(2) { Array(2) { DoubleArray(Encoder.SBMAX_l) } }
    internal var xfsf_s = Array(2) { Array(2) { DoubleArray(3 * Encoder.SBMAX_s) } }

    internal var over = Array(2) { IntArray(2) }
    internal var tot_noise = Array(2) { DoubleArray(2) }
    internal var max_noise = Array(2) { DoubleArray(2) }
    internal var over_noise = Array(2) { DoubleArray(2) }
    internal var over_SSD = Array(2) { IntArray(2) }
    internal var blocktype = Array(2) { IntArray(2) }
    var scalefac_scale = Array(2) { IntArray(2) }
    var preflag = Array(2) { IntArray(2) }
    var mpg123blocktype = Array(2) { IntArray(2) }
    var mixed = Array(2) { IntArray(2) }
    var mainbits = Array(2) { IntArray(2) }
    var sfbits = Array(2) { IntArray(2) }
    internal var LAMEmainbits = Array(2) { IntArray(2) }
    internal var LAMEsfbits = Array(2) { IntArray(2) }
    var framesize: Int = 0
    var stereo: Int = 0
    var js: Int = 0
    var ms_stereo: Int = 0
    var i_stereo: Int = 0
    var emph: Int = 0
    var bitrate: Int = 0
    var sampfreq: Int = 0
    var maindata: Int = 0
    var crc: Int = 0
    var padding: Int = 0
    var scfsi = IntArray(2)
    var mean_bits: Int = 0
    var resvsize: Int = 0
    internal var totbits: Int = 0
}