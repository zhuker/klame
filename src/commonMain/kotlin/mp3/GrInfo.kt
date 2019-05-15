package mp3
import jdk.clone

class GrInfo {
    var xr = FloatArray(576)
    internal var l3_enc = IntArray(576)
    internal var scalefac = IntArray(L3Side.SFBMAX)
    internal var xrpow_max: Float = 0.toFloat()

    internal var part2_3_length: Int = 0
    internal var big_values: Int = 0
    internal var count1: Int = 0
    var global_gain: Int = 0
    internal var scalefac_compress: Int = 0
    var block_type: Int = 0
    var mixed_block_flag: Int = 0
    internal var table_select = IntArray(3)
    internal var subblock_gain = IntArray(3 + 1)
    internal var region0_count: Int = 0
    internal var region1_count: Int = 0
    internal var preflag: Int = 0
    var scalefac_scale: Int = 0
    internal var count1table_select: Int = 0

    internal var part2_length: Int = 0
    internal var sfb_lmax: Int = 0
    internal var sfb_smin: Int = 0
    internal var psy_lmax: Int = 0
    internal var sfbmax: Int = 0
    var psymax: Int = 0
    internal var sfbdivide: Int = 0
    internal var width = IntArray(L3Side.SFBMAX)
    internal var window = IntArray(L3Side.SFBMAX)
    internal var count1bits: Int = 0
    /**
     * added for LSF
     */
    internal var sfb_partition_table: IntArray? = null
    internal var slen = IntArray(4)

    internal var max_nonzero_coeff: Int = 0

    fun assign(other: GrInfo) {
        xr = other.xr.clone()
        l3_enc = other.l3_enc.clone()
        scalefac = other.scalefac.clone()
        xrpow_max = other.xrpow_max

        part2_3_length = other.part2_3_length
        big_values = other.big_values
        count1 = other.count1
        global_gain = other.global_gain
        scalefac_compress = other.scalefac_compress
        block_type = other.block_type
        mixed_block_flag = other.mixed_block_flag
        table_select = other.table_select.clone()
        subblock_gain = other.subblock_gain.clone()
        region0_count = other.region0_count
        region1_count = other.region1_count
        preflag = other.preflag
        scalefac_scale = other.scalefac_scale
        count1table_select = other.count1table_select

        part2_length = other.part2_length
        sfb_lmax = other.sfb_lmax
        sfb_smin = other.sfb_smin
        psy_lmax = other.psy_lmax
        sfbmax = other.sfbmax
        psymax = other.psymax
        sfbdivide = other.sfbdivide
        width = other.width.clone()
        window = other.window.clone()
        count1bits = other.count1bits

        sfb_partition_table = other.sfb_partition_table?.clone()
        slen = other.slen.clone()
        max_nonzero_coeff = other.max_nonzero_coeff
    }
}