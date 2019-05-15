package mpg

import mpg.L2Tables.al_table2

class Frame {

    var stereo: Int = 0
    var jsbound: Int = 0
    var single: Int = 0          /* single channel (monophonic) */
    var lsf: Int = 0             /* 0 = MPEG-1, 1 = MPEG-2/2.5 */
    var mpeg25: Boolean = false          /* 1 = MPEG-2.5, 0 = MPEG-1/2 */
    var lay: Int = 0             /* Layer */
    var error_protection: Boolean = false /* 1 = CRC-16 code following header */
    var bitrate_index: Int = 0
    var sampling_frequency: Int = 0 /* sample rate of decompressed audio in Hz */
    var padding: Int = 0
    var extension: Int = 0
    var mode: Int = 0
    var mode_ext: Int = 0
    var copyright: Int = 0
    var original: Int = 0
    var emphasis: Int = 0
    var framesize: Int = 0       /* computed framesize */

    /* AF: ADDED FOR LAYER1/LAYER2 */
    var II_sblimit: Int = 0
    var alloc: Array<al_table2>? = null
    var down_sample_sblimit: Int = 0
    var down_sample: Int = 0

}
