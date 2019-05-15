package mp3

import mpg.MPGLib

class LameInternalFlags {

    /********************************************************************
     * internal variables NOT set by calling program, and should not be *
     * modified by the calling program *
     */

    /**
     * Some remarks to the Class_ID field: The Class ID is an Identifier for a
     * pointer to this struct. It is very unlikely that a pointer to
     * lame_global_flags has the same 32 bits in it's structure (large and other
     * special properties, for instance prime).
     *
     * To test that the structure is right and initialized, use: if ( gfc .
     * Class_ID == LAME_ID ) ... Other remark: If you set a flag to 0 for uninit
     * data and 1 for init data, the right test should be "if (flag == 1)" and
     * NOT "if (flag)". Unintended modification of this element will be
     * otherwise misinterpreted as an init.
     */
    var Class_ID: Long = 0

    var lame_encode_frame_init: Int = 0
    var iteration_init_init: Int = 0
    var fill_buffer_resample_init: Int = 0

    var mfbuf = Array(2) { FloatArray(MFSIZE) }

    /**
     * granules per frame
     */
    var mode_gr: Int = 0
    /**
     * number of channels in the input data stream (PCM or decoded PCM)
     */
    var channels_in: Int = 0
    /**
     * number of channels in the output data stream (not used for decoding)
     */
    var channels_out: Int = 0
    /**
     * input_samp_rate/output_samp_rate
     */
    var resample_ratio: Double = 0.toDouble()

    var mf_samples_to_encode: Int = 0
    var mf_size: Int = 0
    /**
     * min bitrate index
     */
    var VBR_min_bitrate: Int = 0
    /**
     * max bitrate index
     */
    var VBR_max_bitrate: Int = 0
    var bitrate_index: Int = 0
    var samplerate_index: Int = 0
    var mode_ext: Int = 0

    /* lowpass and highpass filter control */
    /**
     * normalized frequency bounds of passband
     */
    var lowpass1: Float = 0.toFloat()
    var lowpass2: Float = 0.toFloat()
    /**
     * normalized frequency bounds of passband
     */
    var highpass1: Float = 0.toFloat()
    var highpass2: Float = 0.toFloat()

    /**
     * 0 = none 1 = ISO AAC model 2 = allow scalefac_select=1
     */
    var noise_shaping: Int = 0

    /**
     * 0 = ISO model: amplify all distorted bands<BR></BR>
     * 1 = amplify within 50% of max (on db scale)<BR></BR>
     * 2 = amplify only most distorted band<BR></BR>
     * 3 = method 1 and refine with method 2<BR></BR>
     */
    var noise_shaping_amp: Int = 0
    /**
     * 0 = no substep<BR></BR>
     * 1 = use substep shaping at last step(VBR only)<BR></BR>
     * (not implemented yet)<BR></BR>
     * 2 = use substep inside loop<BR></BR>
     * 3 = use substep inside loop and last step<BR></BR>
     */
    var substep_shaping: Int = 0

    /**
     * 1 = gpsycho. 0 = none
     */
    var psymodel: Int = 0
    /**
     * 0 = stop at over=0, all scalefacs amplified or<BR></BR>
     * a scalefac has reached max value<BR></BR>
     * 1 = stop when all scalefacs amplified or a scalefac has reached max value<BR></BR>
     * 2 = stop when all scalefacs amplified
     */
    var noise_shaping_stop: Int = 0

    /**
     * 0 = no, 1 = yes
     */
    var subblock_gain: Int = 0
    /**
     * 0 = no. 1=outside loop 2=inside loop(slow)
     */
    var use_best_huffman: Int = 0

    /**
     * 0 = stop early after 0 distortion found. 1 = full search
     */
    var full_outer_loop: Int = 0

    var l3_side = IIISideInfo()
    var ms_ratio = FloatArray(2)

    /* used for padding */
    /**
     * padding for the current frame?
     */
    var padding: Int = 0
    var frac_SpF: Int = 0
    var slot_lag: Int = 0

    /**
     * optional ID3 tags
     */
    var tag_spec: ID3TagSpec? = null
    var nMusicCRC: Int = 0

    /* variables used by Quantize */
    var OldValue = IntArray(2)
    var CurrentStep = IntArray(2)

    var masking_lower: Float = 0.toFloat()
    var bv_scf = IntArray(576)
    var pseudohalf = IntArray(L3Side.SFBMAX)

    /**
     * will be set in lame_init_params
     */
    var sfb21_extra: Boolean = false
    var inbuf_old = arrayOfNulls<FloatArray>(2)
    var blackfilt = arrayOfNulls<FloatArray>(2 * BPC + 1)
    var itime = DoubleArray(2)
    var sideinfo_len: Int = 0

    /* variables for newmdct.c */
    var sb_sample = Array(2) { Array(2) { Array(18) { FloatArray(Encoder.SBLIMIT) } } }
    var amp_filter = FloatArray(32)

    var header = Array(MAX_HEADER_BUF) { Header() }

    var h_ptr: Int = 0
    var w_ptr: Int = 0
    var ancillary_flag: Int = 0

    /* variables for Reservoir */
    /**
     * in bits
     */
    var ResvSize: Int = 0
    /**
     * in bits
     */
    var ResvMax: Int = 0

    var scalefac_band = ScaleFac()

    /* daa from PsyModel */
    /* The static variables "r", "phi_sav", "new", "old" and "oldest" have */
    /* to be remembered for the unpredictability measure. For "r" and */
    /* "phi_sav", the first index from the left is the channel select and */
    /* the second index is the "age" of the data. */
    var minval_l = FloatArray(Encoder.CBANDS)
    var minval_s = FloatArray(Encoder.CBANDS)
    var nb_1 = Array(4) { FloatArray(Encoder.CBANDS) }
    var nb_2 = Array(4) { FloatArray(Encoder.CBANDS) }
    var nb_s1 = Array(4) { FloatArray(Encoder.CBANDS) }
    var nb_s2 = Array(4) { FloatArray(Encoder.CBANDS) }
    var s3_ss: FloatArray? = null
    var s3_ll: FloatArray? = null
    var decay: Float = 0.toFloat()

    var thm = Array<III_psy_xmin>(4){        III_psy_xmin() }
    var en = Array<III_psy_xmin>(4){III_psy_xmin()}

    /**
     * fft and energy calculation
     */
    var tot_ener = FloatArray(4)

    /* loudness calculation (for adaptive threshold of hearing) */
    /**
     * loudness^2 approx. per granule and channel
     */
    var loudness_sq = Array(2) { FloatArray(2) }
    /**
     * account for granule delay of L3psycho_anal
     */
    var loudness_sq_save = FloatArray(2)

    /**
     * Scale Factor Bands
     */
    var mld_l = FloatArray(Encoder.SBMAX_l)
    var mld_s = FloatArray(Encoder.SBMAX_s)
    var bm_l = IntArray(Encoder.SBMAX_l)
    var bo_l = IntArray(Encoder.SBMAX_l)
    var bm_s = IntArray(Encoder.SBMAX_s)
    var bo_s = IntArray(Encoder.SBMAX_s)
    var npart_l: Int = 0
    var npart_s: Int = 0

    var s3ind = Array(Encoder.CBANDS) { IntArray(2) }
    var s3ind_s = Array(Encoder.CBANDS) { IntArray(2) }

    var numlines_s = IntArray(Encoder.CBANDS)
    var numlines_l = IntArray(Encoder.CBANDS)
    var rnumlines_l = FloatArray(Encoder.CBANDS)
    var mld_cb_l = FloatArray(Encoder.CBANDS)
    var mld_cb_s = FloatArray(Encoder.CBANDS)
    var numlines_s_num1: Int = 0
    var numlines_l_num1: Int = 0

    /* ratios */
    var pe = FloatArray(4)
    var ms_ratio_s_old: Float = 0.toFloat()
    var ms_ratio_l_old: Float = 0.toFloat()
    var ms_ener_ratio_old: Float = 0.toFloat()

    /**
     * block type
     */
    var blocktype_old = IntArray(2)

    /**
     * variables used for --nspsytune
     */
    var nsPsy = NsPsy()

    /**
     * used for Xing VBR header
     */
    var VBR_seek_table = VBRSeekInfo()

    /**
     * all ATH related stuff
     */
    var ATH: ATH? = null

    var PSY: PSY? = null

    var nogap_total: Int = 0
    var nogap_current: Int = 0

    /* ReplayGain */
    var decode_on_the_fly = true
    var findReplayGain = true
    var findPeakSample = true
    var PeakSample: Float = 0.toFloat()
    var RadioGain: Int = 0
    var AudiophileGain: Int = 0
    var rgdata: ReplayGain? = null

    /**
     * gain change required for preventing clipping
     */
    var noclipGainChange: Int = 0
    /**
     * user-specified scale factor required for preventing clipping
     */
    var noclipScale: Float = 0.toFloat()

    /* simple statistics */
    var bitrate_stereoMode_Hist = Array(16) { IntArray(4 + 1) }
    /**
     * norm/start/short/stop/mixed(short)/sum
     */
    var bitrate_blockType_Hist = Array(16) { IntArray(4 + 1 + 1) }

    var pinfo: PlottingData? = null
    var hip: MPGLib.mpstr_tag? = null

    var in_buffer_nsamples: Int = 0
    var in_buffer_0: FloatArray? = null
    var in_buffer_1: FloatArray? = null

    var iteration_loop: IIterationLoop? = null

    class Header {
        var write_timing: Int = 0
        var ptr: Int = 0
        var buf = ByteArray(MAX_HEADER_LEN)
    }

    companion object {

        const val MFSIZE = 3 * 1152 + Encoder.ENCDELAY - Encoder.MDCTDELAY

        const val MAX_BITS_PER_CHANNEL = 4095
        const val MAX_BITS_PER_GRANULE = 7680

        /* BPC = maximum number of filter convolution windows to precompute */
        const val BPC = 320

        /* variables for BitStream */

        /**
         * <PRE>
         * mpeg1: buffer=511 bytes  smallest frame: 96-38(sideinfo)=58
         * max number of frames in reservoir:  8
         * mpeg2: buffer=255 bytes.  smallest frame: 24-23bytes=1
         * with VBR, if you are encoding all silence, it is possible to
         * have 8kbs/24khz frames with 1byte of data each, which means we need
         * to buffer up to 255 headers!
        </PRE> *
         */
        /**
         * also, max_header_buf has to be a power of two
         */
        const val MAX_HEADER_BUF = 256
        /**
         * max size of header is 38
         */
        private const val MAX_HEADER_LEN = 40
    }

}
